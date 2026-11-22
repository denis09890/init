package dev.init.source.lever;

import java.time.Instant;
import java.util.List;

import dev.init.source.Fixtures;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceRegion;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LeverConnectorTests {

	private final Fixtures.MockedHttp http = Fixtures.mockedHttp();

	private final MockRestServiceServer server = http.server();

	@Test
	void fetchesPostingsFromTheGlobalHost() {
		server.expect(requestTo("https://api.lever.co/v0/postings/leverdemo?mode=json&skip=0&limit=100"))
			.andRespond(withSuccess(Fixtures.read("lever/postings-leverdemo.json"), MediaType.APPLICATION_JSON));

		List<RawJob> jobs = connector().fetch(SourceTarget.of(SourceType.LEVER, "leverdemo"));

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId)
			.containsExactly("ad208490-4052-4f91-a57e-433f2d1e484b", "120b5976-ee4e-4dc5-b31b-2b1ca124460c");
	}

	@Test
	void usesTheEuHostForEuSources() {
		server.expect(requestTo("https://api.eu.lever.co/v0/postings/acme?mode=json&skip=0&limit=100"))
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<RawJob> jobs = connector().fetch(new SourceTarget(SourceType.LEVER, "acme", SourceRegion.EU));

		server.verify();
		assertThat(jobs).isEmpty();
	}

	@Test
	void pagesUntilAShortPage() {
		server.expect(requestTo("https://api.lever.co/v0/postings/acme?mode=json&skip=0&limit=2"))
			.andRespond(withSuccess("[" + posting("a") + "," + posting("b") + "]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.lever.co/v0/postings/acme?mode=json&skip=2&limit=2"))
			.andRespond(withSuccess("[" + posting("c") + "]", MediaType.APPLICATION_JSON));

		List<RawJob> jobs = connector(2).fetch(SourceTarget.of(SourceType.LEVER, "acme"));

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId).containsExactly("a", "b", "c");
	}

	@Test
	void normalizesHybridPostingWithListsInTheDescription() {
		NormalizedJob job = connector().normalize(fixture().get(0));

		assertThat(job.title()).isEqualTo("Customer Success Manager");
		assertThat(job.url()).isEqualTo("https://jobs.lever.co/leverdemo/ad208490-4052-4f91-a57e-433f2d1e484b");
		assertThat(job.locations()).containsExactly("Atlanta, Georgia");
		assertThat(job.countryCode()).isEqualTo("US");
		assertThat(job.remoteType()).isEqualTo(RemoteType.HYBRID);
		assertThat(job.description()).isEqualTo(
				"Description shortened for the test fixture. Skill Set: Clear written communication Customer empathy");
		assertThat(job.postedAt()).isEqualTo(Instant.ofEpochMilli(1788199939703L));
		assertThat(job.department()).isEqualTo("Customer Success");
		assertThat(job.employmentType()).isEqualTo("Regular Full Time (Salary)");
	}

	@Test
	void readsTheOnsiteSpellingUsedByLiveResponses() {
		NormalizedJob job = connector().normalize(fixture().get(1));

		assertThat(job.remoteType()).isEqualTo(RemoteType.ONSITE);
		assertThat(job.description()).endsWith("Closing text shortened.");
	}

	private LeverConnector connector() {
		return new LeverConnector(http.client(), Fixtures.objectMapper());
	}

	private LeverConnector connector(int pageSize) {
		return new LeverConnector(http.client(), Fixtures.objectMapper(), pageSize);
	}

	private List<RawJob> fixture() {
		server.expect(requestTo("https://api.lever.co/v0/postings/leverdemo?mode=json&skip=0&limit=100"))
			.andRespond(withSuccess(Fixtures.read("lever/postings-leverdemo.json"), MediaType.APPLICATION_JSON));
		return connector().fetch(SourceTarget.of(SourceType.LEVER, "leverdemo"));
	}

	private static String posting(String id) {
		return "{\"id\": \"" + id + "\", \"text\": \"Job " + id + "\", \"hostedUrl\": \"https://jobs.lever.co/acme/" + id + "\"}";
	}

}
