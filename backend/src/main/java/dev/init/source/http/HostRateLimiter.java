package dev.init.source.http;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.init.source.SourceFetchException;

/**
 * Keeps the load on every host polite: at most {@code maxConcurrent} requests in flight per host,
 * and at least {@code minInterval} between the starts of two requests to the same host. Different
 * hosts do not slow each other down. Callers wait on virtual threads, so waiting is cheap.
 */
public final class HostRateLimiter {

	private final int maxConcurrent;

	private final long minIntervalNanos;

	private final ConcurrentHashMap<String, HostGate> gates = new ConcurrentHashMap<>();

	public HostRateLimiter(int maxConcurrent, Duration minInterval) {
		if (maxConcurrent < 1) {
			throw new IllegalArgumentException("maxConcurrent must be at least 1, was " + maxConcurrent);
		}
		this.maxConcurrent = maxConcurrent;
		this.minIntervalNanos = minInterval.toNanos();
	}

	/** No limits at all; used where politeness is irrelevant, such as tests against a mock server. */
	public static HostRateLimiter unlimited() {
		return new HostRateLimiter(Integer.MAX_VALUE, Duration.ZERO);
	}

	public <T> T execute(String host, Supplier<T> action) {
		HostGate gate = gates.computeIfAbsent(host.toLowerCase(Locale.ROOT), key -> new HostGate(maxConcurrent));
		try {
			gate.permits.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new SourceFetchException("Interrupted while waiting for a slot on " + host, ex);
		}
		try {
			long waitNanos = gate.reserveSlot(minIntervalNanos);
			if (waitNanos > 0) {
				TimeUnit.NANOSECONDS.sleep(waitNanos);
			}
			return action.get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new SourceFetchException("Interrupted while waiting to call " + host, ex);
		}
		finally {
			gate.permits.release();
		}
	}

	private static final class HostGate {

		final Semaphore permits;

		private long nextStartNanos = System.nanoTime();

		HostGate(int maxConcurrent) {
			this.permits = new Semaphore(maxConcurrent);
		}

		/**
		 * Claims the next start time for this host and returns how long the caller must wait for it.
		 * Only does arithmetic, so holding the monitor is harmless for virtual threads.
		 */
		synchronized long reserveSlot(long intervalNanos) {
			long now = System.nanoTime();
			long start = Math.max(now, nextStartNanos);
			nextStartNanos = start + intervalNanos;
			return start - now;
		}

	}

}
