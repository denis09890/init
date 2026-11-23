package dev.init.source.http;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class HostRateLimiterTests {

	@Test
	void limitsConcurrentRequestsPerHost() throws Exception {
		HostRateLimiter limiter = new HostRateLimiter(2, Duration.ZERO);
		AtomicInteger inFlight = new AtomicInteger();
		AtomicInteger highestSeen = new AtomicInteger();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<String>> futures = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				futures.add(executor.submit(() -> limiter.execute("example.com", () -> {
					highestSeen.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
					pause(20);
					inFlight.decrementAndGet();
					return "done";
				})));
			}
			for (Future<String> future : futures) {
				assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("done");
			}
		}

		assertThat(highestSeen.get()).isLessThanOrEqualTo(2);
	}

	@Test
	void hostsDoNotBlockEachOther() throws Exception {
		HostRateLimiter limiter = new HostRateLimiter(1, Duration.ZERO);
		CountDownLatch slowHostBusy = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<String> slow = executor.submit(() -> limiter.execute("slow.example", () -> {
				slowHostBusy.countDown();
				awaitQuietly(release);
				return "slow";
			}));
			assertThat(slowHostBusy.await(5, TimeUnit.SECONDS)).isTrue();

			assertThat(limiter.execute("fast.example", () -> "fast")).isEqualTo("fast");

			release.countDown();
			assertThat(slow.get(5, TimeUnit.SECONDS)).isEqualTo("slow");
		}
	}

	@Test
	void spacesRequestsToTheSameHost() {
		HostRateLimiter limiter = new HostRateLimiter(5, Duration.ofMillis(60));

		long start = System.nanoTime();
		for (int i = 0; i < 3; i++) {
			limiter.execute("example.com", () -> "x");
		}
		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		// the first request starts at once, the next two wait 60 ms each; a little tolerance for timer granularity
		assertThat(elapsedMillis).isGreaterThanOrEqualTo(110);
	}

	@Test
	void releasesTheSlotWhenTheActionFails() {
		HostRateLimiter limiter = new HostRateLimiter(1, Duration.ZERO);

		assertThatThrownBy(() -> limiter.execute("example.com", () -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			assertThat(limiter.execute("example.com", () -> "next")).isEqualTo("next");
		});
	}

	@Test
	void hostNamesAreMatchedIgnoringCase() {
		HostRateLimiter limiter = new HostRateLimiter(5, Duration.ofMillis(60));

		long start = System.nanoTime();
		limiter.execute("Example.COM", () -> "x");
		limiter.execute("example.com", () -> "x");
		long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		assertThat(elapsedMillis).isGreaterThanOrEqualTo(50);
	}

	@Test
	void unlimitedDoesNotWait() {
		HostRateLimiter limiter = HostRateLimiter.unlimited();

		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
			for (int i = 0; i < 100; i++) {
				limiter.execute("example.com", () -> "x");
			}
		});
	}

	@Test
	void rejectsFewerThanOneConcurrentRequest() {
		assertThatThrownBy(() -> new HostRateLimiter(0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
	}

	private static void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
