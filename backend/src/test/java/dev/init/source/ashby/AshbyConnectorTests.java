package dev.init.source.ashby;

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

/**
 * The fixture follows the format of the live API (checked on 2026-11-28) for a fictional board named
 * {@code example}; see {@code src/test/resources/fixtures/README.md}.
 */
class AshbyConnectorTests {

	private static final String JOBS_URL = "https://api.ashbyhq.com/posting-api/job-board/example";

	private final Fixtures.MockedHttp http = Fixtures.mockedHttp();

	private final MockRestServiceServer server = http.server();

	private final AshbyConnector connector = new AshbyConnector(http.client(), Fixtures.objectMapper());

	@Test
	void fetchesListedJobsWithTheirRawPayloadAndSkipsUnlistedOnes() {
		List<RawJob> jobs = fetchFixture();

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId)
			.containsExactly("0a1b2c3d-0000-4000-8000-000000000001", "0a1b2c3d-0000-4000-8000-000000000002");
		assertThat(jobs.getFirst().payload()).contains("\"title\":\"Software Engineer Intern\"");
	}

	@Test
	void normalizesHybridInternshipInRomania() {
		NormalizedJob job = connector.normalize(fetchFixture().get(0));

		assertThat(job.externalId()).isEqualTo("0a1b2c3d-0000-4000-8000-000000000001");
		assertThat(job.title()).isEqualTo("Software Engineer Intern");
		assertThat(job.url()).isEqualTo("https://jobs.ashbyhq.com/example/0a1b2c3d-0000-4000-8000-000000000001");
		assertThat(job.locations()).containsExactly("Bucharest, Romania", "Cluj-Napoca");
		assertThat(job.countryCode()).isEqualTo("RO");
		assertThat(job.remoteType()).as("workplaceType wins over isRemote").isEqualTo(RemoteType.HYBRID);
		assertThat(job.description()).isEqualTo("Description shortened for the test fixture. Build product features.");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-11-20T09:15:00Z"));
		assertThat(job.department()).isEqualTo("Engineering");
		assertThat(job.employmentType()).isEqualTo("Intern");
		assertThat(job.experienceLevel()).isNull();
	}

	@Test
	void readsRemoteJobOpenToSeveralCountriesAndHtmlOnlyDescription() {
		NormalizedJob job = connector.normalize(fetchFixture().get(1));

		assertThat(job.locations()).containsExactly("Remote - European Union", "Berlin", "Romania");
		assertThat(job.countryCode()).as("only Romania is mapped to a code").isNull();
		assertThat(job.remoteType()).isEqualTo(RemoteType.REMOTE);
		assertThat(job.description()).isEqualTo("Description shortened for the test fixture. Lead the team.");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2024-03-04T14:29:08.532Z"));
	}

	@Test
	void fallsBackToTeamRemoteFlagAndAddress() {
		RawJob raw = new RawJob("1", """
				{"id": "1", "title": "Support Agent", "jobUrl": "https://jobs.ashbyhq.com/example/1",
				 "team": "Customer Support", "isRemote": true,
				 "address": {"postalAddress": {"addressLocality": "Iasi", "addressCountry": "Romania"}}}
				""");

		NormalizedJob job = connector.normalize(raw);

		assertThat(job.department()).isEqualTo("Customer Support");
		assertThat(job.remoteType()).isEqualTo(RemoteType.REMOTE);
		assertThat(job.locations()).containsExactly("Iasi", "Romania");
		assertThat(job.countryCode()).isEqualTo("RO");
		assertThat(job.description()).isEmpty();
	}

	@Test
	void rejectsPostingWithoutLink() {
		RawJob raw = new RawJob("1", "{\"id\": \"1\", \"title\": \"Support Agent\"}");

		assertThatThrownBy(() -> connector.normalize(raw)).isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("jobUrl");
	}

	@Test
	void unknownBoardFailsWithStatusThatIsNotRetryable() {
		server.expect(requestTo(JOBS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.ASHBY, "example")))
			.isInstanceOfSatisfying(SourceFetchException.class, ex -> {
				assertThat(ex.statusCode()).hasValue(404);
				assertThat(ex.isRetryable()).isFalse();
			});
	}

	@Test
	void responseWithoutJobsArrayIsRejected() {
		server.expect(requestTo(JOBS_URL)).andRespond(withSuccess("{\"apiVersion\": \"1\"}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.ASHBY, "example")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("no jobs array");
	}

	@Test
	void refusesTargetsOfOtherSourceTypes() {
		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.GREENHOUSE, "example")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private List<RawJob> fetchFixture() {
		server.expect(requestTo(JOBS_URL))
			.andRespond(withSuccess(Fixtures.read("ashby/job-board-example.json"), MediaType.APPLICATION_JSON));
		return connector.fetch(SourceTarget.of(SourceType.ASHBY, "example"));
	}

}
