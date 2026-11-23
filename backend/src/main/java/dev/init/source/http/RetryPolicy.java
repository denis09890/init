package dev.init.source.http;

import java.time.Duration;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import dev.init.source.SourceFetchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retries failures that are worth retrying (network errors, HTTP 429 and 5xx) with exponential
 * backoff: the wait doubles after every failed attempt up to a maximum, and is randomized to
 * 50–100% of that value so that parallel callers do not retry in lockstep. Everything else, such
 * as a 404 or a malformed response, fails immediately.
 */
public final class RetryPolicy {

	private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

	private static final RetryPolicy NONE = new RetryPolicy(1, Duration.ZERO, Duration.ZERO, Thread::sleep,
			Math::random);

	/** Pauses the current thread; replaceable in tests. */
	@FunctionalInterface
	interface Sleeper {

		void sleep(Duration duration) throws InterruptedException;

	}

	private final int maxAttempts;

	private final Duration initialBackoff;

	private final Duration maxBackoff;

	private final Sleeper sleeper;

	private final DoubleSupplier random;

	RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff, Sleeper sleeper, DoubleSupplier random) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1, was " + maxAttempts);
		}
		this.maxAttempts = maxAttempts;
		this.initialBackoff = initialBackoff;
		this.maxBackoff = maxBackoff;
		this.sleeper = sleeper;
		this.random = random;
	}

	/** A single attempt, no retries. */
	public static RetryPolicy none() {
		return NONE;
	}

	public static RetryPolicy exponential(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
		return new RetryPolicy(maxAttempts, initialBackoff, maxBackoff, Thread::sleep, Math::random);
	}

	public <T> T execute(Supplier<T> action) {
		for (int attempt = 1;; attempt++) {
			try {
				return action.get();
			}
			catch (SourceFetchException ex) {
				if (!ex.isRetryable() || attempt >= maxAttempts) {
					throw ex;
				}
				Duration delay = backoff(attempt);
				log.warn("{} (attempt {}/{}), retrying in {} ms", ex.getMessage(), attempt, maxAttempts,
						delay.toMillis());
				pause(delay, ex);
			}
		}
	}

	/** The wait after the given failed attempt (1-based). */
	Duration backoff(int failedAttempt) {
		double exponential = initialBackoff.toMillis() * Math.pow(2, failedAttempt - 1);
		long capped = (long) Math.min(exponential, maxBackoff.toMillis());
		return Duration.ofMillis((long) (capped * (0.5 + 0.5 * random.getAsDouble())));
	}

	private void pause(Duration delay, SourceFetchException failure) {
		try {
			sleeper.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new SourceFetchException("Interrupted while waiting to retry: " + failure.getMessage(), ex);
		}
	}

}
