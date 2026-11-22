package dev.init.source.greenhouse;

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

class GreenhouseConnectorTests {

	private static final String JOBS_URL = "https://boards-api.greenhouse.io/v1/boards/cresta/jobs?content=true";

	private final Fixtures.MockedHttp http = Fixtures.mockedHttp();

	private final MockRestServiceServer server = http.server();

	private final GreenhouseConnector connector = new GreenhouseConnector(http.client(), Fixtures.objectMapper());

	@Test
	void fetchesEveryJobOfTheBoardWithItsRawPayload() {
		server.expect(requestTo(JOBS_URL))
			.andRespond(withSuccess(Fixtures.read("greenhouse/jobs-cresta.json"), MediaType.APPLICATION_JSON));

		List<RawJob> jobs = connector.fetch(SourceTarget.of(SourceType.GREENHOUSE, "cresta"));

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId).containsExactly("4123841008", "4928801008", "5030292008");
		assertThat(jobs.getFirst().payload()).contains("\"title\":\"Software Engineer Intern\"");
	}

	@Test
	void normalizesInternshipWithEscapedHtmlDescription() {
		NormalizedJob job = connector.normalize(fetchFixture().get(0));

		assertThat(job.externalId()).isEqualTo("4123841008");
		assertThat(job.title()).isEqualTo("Software Engineer Intern");
		assertThat(job.url()).isEqualTo("https://job-boards.greenhouse.io/cresta/jobs/4123841008");
		assertThat(job.locations()).containsExactly("Toronto Canada");
		assertThat(job.remoteType()).isEqualTo(RemoteType.UNKNOWN);
		assertThat(job.description()).isEqualTo(
				"Description shortened for the test fixture. About the role Build product features with the platform team.");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2024-09-10T23:10:58Z"));
		assertThat(job.employmentType()).as("metadata value is null").isNull();
		assertThat(job.countryCode()).isNull();
	}

	@Test
	void readsHybridLocationAndMetadataFields() {
		NormalizedJob job = connector.normalize(fetchFixture().get(1));

		assertThat(job.locations()).containsExactly("Bucharest, Romania (Hybrid)");
		assertThat(job.remoteType()).isEqualTo(RemoteType.HYBRID);
		assertThat(job.employmentType()).isEqualTo("Full-time");
		assertThat(job.department()).isEqualTo("Engineering");
	}

	@Test
	void trimsTitleAndFallsBackToUpdatedAtAndDepartments() {
		NormalizedJob job = connector.normalize(fetchFixture().get(2));

		assertThat(job.title()).isEqualTo("Staff Software Engineer, Backend (Iasi)");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-10-19T18:22:44Z"));
		assertThat(job.department()).isEqualTo("Engineering");
	}

	@Test
	void splitsMultipleLocations() {
		RawJob raw = new RawJob("1", """
				{"id": 1, "title": "Growth Engineer", "absolute_url": "https://example.com/1",
				 "location": {"name": "Canada (Remote); Toronto, Canada (Hybrid); United States (Remote)"}}
				""");

		NormalizedJob job = connector.normalize(raw);

		assertThat(job.locations()).containsExactly("Canada (Remote)", "Toronto, Canada (Hybrid)",
				"United States (Remote)");
		assertThat(job.remoteType()).isEqualTo(RemoteType.HYBRID);
	}

	@Test
	void rejectsPostingWithoutTitle() {
		RawJob raw = new RawJob("1", "{\"id\": 1, \"title\": \" \", \"absolute_url\": \"https://example.com/1\"}");

		assertThatThrownBy(() -> connector.normalize(raw)).isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("title");
	}

	@Test
	void unknownBoardFailsWithStatusThatIsNotRetryable() {
		server.expect(requestTo(JOBS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.GREENHOUSE, "cresta")))
			.isInstanceOfSatisfying(SourceFetchException.class, ex -> {
				assertThat(ex.statusCode()).hasValue(404);
				assertThat(ex.isRetryable()).isFalse();
			});
	}

	@Test
	void serverErrorIsRetryable() {
		server.expect(requestTo(JOBS_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.GREENHOUSE, "cresta")))
			.isInstanceOfSatisfying(SourceFetchException.class, ex -> assertThat(ex.isRetryable()).isTrue());
	}

	@Test
	void responseWithoutJobsArrayIsRejected() {
		server.expect(requestTo(JOBS_URL)).andRespond(withSuccess("{\"error\": \"oops\"}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.GREENHOUSE, "cresta")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("no jobs array");
	}

	@Test
	void refusesTargetsOfOtherSourceTypes() {
		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.LEVER, "cresta")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private List<RawJob> fetchFixture() {
		server.expect(requestTo(JOBS_URL))
			.andRespond(withSuccess(Fixtures.read("greenhouse/jobs-cresta.json"), MediaType.APPLICATION_JSON));
		return connector.fetch(SourceTarget.of(SourceType.GREENHOUSE, "cresta"));
	}

}
