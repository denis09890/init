package dev.init.source.http;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import dev.init.source.SourceFetchException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SourceHttpClientTests {

	private static final String URL = "https://example.com/jobs";

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final List<Duration> sleeps = new ArrayList<>();

	private final SourceHttpClient client = new SourceHttpClient(builder.build(), HostRateLimiter.unlimited(),
			new RetryPolicy(3, Duration.ofMillis(10), Duration.ofMillis(10), sleeps::add, () -> 1.0));

	@Test
	void retriesServerErrorsUntilTheSourceAnswers() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(client.getJson(URI.create(URL))).isEqualTo("[]");

		server.verify();
		assertThat(sleeps).hasSize(1);
	}

	@Test
	void retriesTooManyRequests() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		server.expect(requestTo(URL)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(client.getJson(URI.create(URL))).isEqualTo("[]");

		server.verify();
	}

	@Test
	void givesUpAfterTheConfiguredAttempts() {
		server.expect(ExpectedCount.times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

		assertThatThrownBy(() -> client.getJson(URI.create(URL))).isInstanceOfSatisfying(SourceFetchException.class,
				ex -> assertThat(ex.statusCode()).hasValue(502));

		server.verify();
		assertThat(sleeps).hasSize(2);
	}

	@Test
	void doesNotRetryNotFound() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> client.getJson(URI.create(URL))).isInstanceOfSatisfying(SourceFetchException.class,
				ex -> assertThat(ex.isRetryable()).isFalse());

		server.verify();
		assertThat(sleeps).isEmpty();
	}

	@Test
	void readsXmlAsBytesSoTheDeclaredEncodingIsKept() {
		byte[] xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a>Timișoara</a>"
			.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		server.expect(requestTo(URL)).andRespond(withSuccess(xml, MediaType.TEXT_XML));

		assertThat(client.getXml(URI.create(URL))).isEqualTo(xml);

		server.verify();
	}

	@Test
	void aMissingTextFileIsNotAnErrorAndNotRetried() {
		server.expect(requestTo("https://example.com/robots.txt")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(client.getTextIfPresent(URI.create("https://example.com/robots.txt"))).isEmpty();

		server.verify();
	}

}
