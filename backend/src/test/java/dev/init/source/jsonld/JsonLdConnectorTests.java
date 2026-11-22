package dev.init.source.jsonld;

import java.time.Instant;
import java.util.List;

import dev.init.source.Fixtures;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JsonLdConnectorTests {

	private static final String PAGE = "https://careers.example.com/jobs";

	private static final String ROBOTS = "https://careers.example.com/robots.txt";

	private final Fixtures.MockedHttp http = Fixtures.mockedHttp();

	private final MockRestServiceServer server = http.server();

	private final JsonLdConnector connector = new JsonLdConnector(http.client(), Fixtures.objectMapper());

	@Test
	void readsEveryJobPostingOnThePageAndIgnoresOtherTypesAndBrokenBlocks() {
		List<RawJob> jobs = fetchFixtureWithoutRobotsTxt();

		server.verify();
		assertThat(jobs).hasSize(2);
		assertThat(jobs.get(0).externalId()).as("PropertyValue identifier").isEqualTo("DA-2026-07");
		assertThat(jobs.get(1).externalId()).as("no identifier or url: stable hash").startsWith("sha256:");
	}

	@Test
	void hashedIdentifierIsStableAcrossFetches() {
		String first = fetchFixtureWithoutRobotsTxt().get(1).externalId();
		String second = fetchFixtureWithoutRobotsTxt().get(1).externalId();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void normalizesPostingWithStructuredAddress() {
		NormalizedJob job = connector.normalize(fetchFixtureWithoutRobotsTxt().get(0));

		assertThat(job.title()).isEqualTo("Junior Data Analyst");
		assertThat(job.url()).isEqualTo("https://careers.example.com/jobs/junior-data-analyst");
		assertThat(job.locations()).containsExactly("Cluj-Napoca");
		assertThat(job.countryCode()).isEqualTo("RO");
		assertThat(job.remoteType()).isEqualTo(RemoteType.UNKNOWN);
		assertThat(job.description()).isEqualTo("Description shortened for the test fixture. SQL Python");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
		assertThat(job.employmentType()).isEqualTo("FULL_TIME, INTERN");
	}

	@Test
	void normalizesRemotePostingFromGraphUsingThePageUrl() {
		NormalizedJob job = connector.normalize(fetchFixtureWithoutRobotsTxt().get(1));

		assertThat(job.title()).isEqualTo("Marketing Intern");
		assertThat(job.url()).isEqualTo(PAGE);
		assertThat(job.remoteType()).isEqualTo(RemoteType.REMOTE);
		assertThat(job.locations()).isEmpty();
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-10-15T05:00:00Z"));
		assertThat(job.employmentType()).isEqualTo("INTERN");
	}

	@Test
	void neverFetchesAPageDisallowedByRobotsTxt() {
		server.expect(requestTo(ROBOTS))
			.andRespond(withSuccess("User-agent: *\nDisallow: /jobs\n", MediaType.TEXT_PLAIN));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.JSON_LD, PAGE)))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("robots.txt");
		server.verify();
	}

	@Test
	void fetchesThePageWhenRobotsTxtAllowsIt() {
		server.expect(requestTo(ROBOTS))
			.andRespond(withSuccess("User-agent: *\nDisallow: /admin\n", MediaType.TEXT_PLAIN));
		server.expect(requestTo(PAGE))
			.andRespond(withSuccess(Fixtures.read("jsonld/careers-page.html"), MediaType.TEXT_HTML));

		assertThat(connector.fetch(SourceTarget.of(SourceType.JSON_LD, PAGE))).hasSize(2);
		server.verify();
	}

	@Test
	void rejectsIdentifiersThatAreNotPageUrls() {
		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.JSON_LD, "careers.example.com")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("http(s) page URL");
	}

	private List<RawJob> fetchFixtureWithoutRobotsTxt() {
		server.reset();
		server.expect(requestTo(ROBOTS)).andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(PAGE))
			.andRespond(withSuccess(Fixtures.read("jsonld/careers-page.html"), MediaType.TEXT_HTML));
		return connector.fetch(SourceTarget.of(SourceType.JSON_LD, PAGE));
	}

}
