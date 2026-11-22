package dev.init.source.smartrecruiters;

import java.time.Instant;
import java.util.List;

import dev.init.source.Fixtures;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SmartRecruitersConnectorTests {

	private static final String API = "https://api.smartrecruiters.com/v1/companies/BoschGroup/postings";

	private final Fixtures.MockedHttp http = Fixtures.mockedHttp();

	private final MockRestServiceServer server = http.server();

	@Test
	void listsRomanianPostingsAndFetchesTheDetailsOfEach() {
		expectBoschFixture();

		List<RawJob> jobs = connector().fetch(SourceTarget.of(SourceType.SMARTRECRUITERS, "BoschGroup"));

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId).containsExactly("744000162663639", "744000162664020");
		assertThat(jobs.getFirst().payload()).as("the detail is the raw payload").contains("postingUrl");
	}

	@Test
	void pagesByTotalFound() {
		server.expect(requestTo(API + "?country=ro&offset=0&limit=1"))
			.andRespond(withSuccess(page(2, "1"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(API + "?country=ro&offset=1&limit=1"))
			.andRespond(withSuccess(page(2, "2"), MediaType.APPLICATION_JSON));
		server.expect(requestTo(API + "/1")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		server.expect(requestTo(API + "/2")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		List<RawJob> jobs = new SmartRecruitersConnector(http.client(), Fixtures.objectMapper(), 1)
			.fetch(SourceTarget.of(SourceType.SMARTRECRUITERS, "BoschGroup"));

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId).containsExactly("1", "2");
	}

	@Test
	void normalizesWorkingStudentPosting() {
		NormalizedJob job = connector().normalize(fixture().get(0));

		assertThat(job.title()).isEqualTo("Working Student Claims Management");
		assertThat(job.url())
			.isEqualTo("https://jobs.smartrecruiters.com/BoschGroup/744000162663639-working-student-claims-management-");
		assertThat(job.locations()).containsExactly("Blaj");
		assertThat(job.countryCode()).isEqualTo("RO");
		assertThat(job.remoteType()).isEqualTo(RemoteType.HYBRID);
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-11-20T06:49:50.059Z"));
		assertThat(job.department()).isEqualTo("Administrative");
		assertThat(job.employmentType()).isEqualTo("Intern");
		assertThat(job.experienceLevel()).isEqualTo("Internship");
	}

	@Test
	void descriptionCoversTheJobSectionsButNotTheCompanyBlurb() {
		NormalizedJob job = connector().normalize(fixture().get(0));

		assertThat(job.description()).startsWith("Processing of complaints on site.")
			.contains("Bachelor/Master degree ongoing in technical studies;")
			.contains("Benefits: Meal vouchers")
			.doesNotContain("Company text shortened");
	}

	private SmartRecruitersConnector connector() {
		return new SmartRecruitersConnector(http.client(), Fixtures.objectMapper());
	}

	private List<RawJob> fixture() {
		expectBoschFixture();
		return connector().fetch(SourceTarget.of(SourceType.SMARTRECRUITERS, "BoschGroup"));
	}

	private void expectBoschFixture() {
		server.expect(requestTo(API + "?country=ro&offset=0&limit=100"))
			.andRespond(withSuccess(Fixtures.read("smartrecruiters/postings-boschgroup.json"),
					MediaType.APPLICATION_JSON));
		server.expect(requestTo(API + "/744000162663639"))
			.andRespond(withSuccess(Fixtures.read("smartrecruiters/posting-744000162663639.json"),
					MediaType.APPLICATION_JSON));
		server.expect(requestTo(API + "/744000162664020"))
			.andRespond(withSuccess(Fixtures.read("smartrecruiters/posting-744000162664020.json"),
					MediaType.APPLICATION_JSON));
	}

	private static String page(int totalFound, String id) {
		return "{\"totalFound\": " + totalFound + ", \"content\": [{\"id\": \"" + id + "\"}]}";
	}

}
