package dev.init.source.http;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.init.source.SourceFetchException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTests {

	private static final URI URL = URI.create("https://example.com/jobs");

	private final List<Duration> sleeps = new ArrayList<>();

	private final AtomicInteger calls = new AtomicInteger();

	/** Waits 1 s, 2 s, 4 s ... capped at 3 s; {@code jitter} 1.0 keeps the full wait, 0.0 halves it. */
	private RetryPolicy policy(int maxAttempts, double jitter) {
		return new RetryPolicy(maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(3), sleeps::add, () -> jitter);
	}

	@Test
	void returnsTheResultWithoutWaiting() {
		assertThat(policy(3, 1.0).execute(() -> "ok")).isEqualTo("ok");
		assertThat(sleeps).isEmpty();
	}

	@Test
	void retriesTransientFailuresUntilItSucceeds() {
		String result = policy(3, 1.0).execute(() -> {
			if (calls.incrementAndGet() < 3) {
				throw SourceFetchException.httpStatus(URL, 503, null);
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(calls).hasValue(3);
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void givesUpAfterTheMaximumNumberOfAttempts() {
		RetryPolicy policy = policy(3, 1.0);

		assertThatThrownBy(() -> policy.execute(() -> {
			calls.incrementAndGet();
			throw SourceFetchException.httpStatus(URL, 503, null);
		})).isInstanceOfSatisfying(SourceFetchException.class,
				ex -> assertThat(ex.statusCode()).hasValue(503));

		assertThat(calls).hasValue(3);
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void doesNotRetryClientErrors() {
		RetryPolicy policy = policy(3, 1.0);

		assertThatThrownBy(() -> policy.execute(() -> {
			calls.incrementAndGet();
			throw SourceFetchException.httpStatus(URL, 404, null);
		})).isInstanceOf(SourceFetchException.class);

		assertThat(calls).hasValue(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void retriesTooManyRequestsAndNetworkErrors() {
		RetryPolicy policy = policy(3, 1.0);

		String result = policy.execute(() -> {
			int call = calls.incrementAndGet();
			if (call == 1) {
				throw SourceFetchException.httpStatus(URL, 429, null);
			}
			if (call == 2) {
				throw SourceFetchException.io(URL, new IOException("timed out"));
			}
			return "ok";
		});

		assertThat(result).isEqualTo("ok");
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void unexpectedExceptionsAreNotRetried() {
		RetryPolicy policy = policy(3, 1.0);

		assertThatThrownBy(() -> policy.execute(() -> {
			calls.incrementAndGet();
			throw new IllegalStateException("bug");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(calls).hasValue(1);
	}

	@Test
	void backoffDoublesUpToTheMaximum() {
		RetryPolicy policy = policy(6, 1.0);

		assertThatThrownBy(() -> policy.execute(() -> {
			throw SourceFetchException.httpStatus(URL, 500, null);
		})).isInstanceOf(SourceFetchException.class);

		assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3),
				Duration.ofSeconds(3), Duration.ofSeconds(3));
	}

	@Test
	void jitterSpreadsTheWaitBetweenHalfAndFull() {
		assertThat(policy(2, 0.0).backoff(1)).isEqualTo(Duration.ofMillis(500));
		assertThat(policy(2, 0.5).backoff(1)).isEqualTo(Duration.ofMillis(750));
		assertThat(policy(2, 1.0).backoff(1)).isEqualTo(Duration.ofMillis(1000));
	}

	@Test
	void noneNeverRetries() {
		assertThatThrownBy(() -> RetryPolicy.none().execute(() -> {
			calls.incrementAndGet();
			throw SourceFetchException.httpStatus(URL, 503, null);
		})).isInstanceOf(SourceFetchException.class);

		assertThat(calls).hasValue(1);
	}

	@Test
	void interruptionWhileWaitingStopsRetrying() {
		RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(3), duration -> {
			throw new InterruptedException();
		}, () -> 1.0);

		assertThatThrownBy(() -> policy.execute(() -> {
			calls.incrementAndGet();
			throw SourceFetchException.httpStatus(URL, 503, null);
		})).isInstanceOf(SourceFetchException.class).hasMessageContaining("Interrupted");

		assertThat(calls).hasValue(1);
		assertThat(Thread.interrupted()).as("the interrupt flag is restored (and cleared here)").isTrue();
	}

	@Test
	void rejectsFewerThanOneAttempt() {
		assertThatThrownBy(() -> policy(0, 1.0)).isInstanceOf(IllegalArgumentException.class);
	}

}
