package dev.init.source.personio;

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
 * The fixture follows the format of the live feed (checked on 2026-11-28) for a fictional company
 * named {@code example}; see {@code src/test/resources/fixtures/README.md}.
 */
class PersonioConnectorTests {

	private static final String FEED_URL = "https://example.jobs.personio.de/xml";

	private final Fixtures.MockedHttp http = Fixtures.mockedHttp();

	private final MockRestServiceServer server = http.server();

	private final PersonioConnector connector = new PersonioConnector(http.client(), Fixtures.objectMapper());

	@Test
	void fetchesEveryPositionAsJsonWithItsLink() {
		List<RawJob> jobs = fetchFixture();

		server.verify();
		assertThat(jobs).extracting(RawJob::externalId).containsExactly("1000001", "1000002");
		assertThat(jobs.getFirst().payload())
			.contains("\"name\":\"Software Developer Intern\"")
			.contains("\"jobUrl\":\"https://example.jobs.personio.de/job/1000001\"");
	}

	@Test
	void keepsElementsInitDoesNotUseInTheRawPayload() {
		RawJob second = fetchFixture().get(1);

		assertThat(second.payload()).contains("\"salaryInformation\":{\"min\":\"4000.00\",\"currencyCode\":\"RON\"")
			.contains("\"yearsOfExperience\":\"lt-1\"");
	}

	@Test
	void normalizesInternshipWithSectionsAndSeveralOffices() {
		NormalizedJob job = connector.normalize(fetchFixture().get(0));

		assertThat(job.externalId()).isEqualTo("1000001");
		assertThat(job.title()).isEqualTo("Software Developer Intern");
		assertThat(job.url()).isEqualTo("https://example.jobs.personio.de/job/1000001");
		assertThat(job.locations()).as("UTF-8 is kept").containsExactly("Cluj-Napoca", "Timișoara");
		assertThat(job.countryCode()).isNull();
		assertThat(job.remoteType()).isEqualTo(RemoteType.UNKNOWN);
		assertThat(job.description()).isEqualTo("About the role Description shortened for the test fixture. "
				+ "What you'll learn Build product features.");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2026-11-26T16:55:38Z"));
		assertThat(job.department()).isEqualTo("Engineering");
		assertThat(job.employmentType()).isEqualTo("intern");
		assertThat(job.experienceLevel()).isEqualTo("student");
	}

	@Test
	void readsRemoteOfficeCategoryAndOffsetWithoutColon() {
		NormalizedJob job = connector.normalize(fetchFixture().get(1));

		assertThat(job.locations()).containsExactly("Remote");
		assertThat(job.remoteType()).isEqualTo(RemoteType.REMOTE);
		assertThat(job.department()).as("recruiting category when there is no department").isEqualTo("Customer Success");
		assertThat(job.postedAt()).isEqualTo(Instant.parse("2016-05-31T10:14:07Z"));
		assertThat(job.experienceLevel()).isEqualTo("entry-level");
	}

	@Test
	void emptyFeedHasNoPositions() {
		server.expect(requestTo(FEED_URL)).andRespond(withSuccess("<workzag-jobs/>", MediaType.TEXT_XML));

		assertThat(connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example"))).isEmpty();
	}

	@Test
	void identifierIsLowerCasedIntoTheHostName() {
		server.expect(requestTo(FEED_URL)).andRespond(withSuccess("<workzag-jobs/>", MediaType.TEXT_XML));

		connector.fetch(SourceTarget.of(SourceType.PERSONIO, " Example "));

		server.verify();
	}

	@Test
	void rejectsIdentifierThatIsNotASingleSubdomain() {
		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.PERSONIO, "evil.example.com/x")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("not a valid subdomain");
	}

	@Test
	void refusesDoctypeSoExternalEntitiesCannotBeRead() {
		String hostile = """
				<?xml version="1.0"?>
				<!DOCTYPE workzag-jobs [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
				<workzag-jobs><position><id>1</id><name>&secret;</name></position></workzag-jobs>
				""";
		server.expect(requestTo(FEED_URL)).andRespond(withSuccess(hostile, MediaType.TEXT_XML));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("Malformed XML");
	}

	@Test
	void malformedXmlIsRejected() {
		server.expect(requestTo(FEED_URL)).andRespond(withSuccess("<workzag-jobs><position>", MediaType.TEXT_XML));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("Malformed XML");
	}

	@Test
	void unexpectedRootElementIsRejected() {
		server.expect(requestTo(FEED_URL)).andRespond(withSuccess("<html><body/></html>", MediaType.TEXT_XML));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("unexpected root element <html>");
	}

	@Test
	void positionWithoutIdIsRejected() {
		server.expect(requestTo(FEED_URL))
			.andRespond(withSuccess("<workzag-jobs><position><name>Intern</name></position></workzag-jobs>",
					MediaType.TEXT_XML));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example")))
			.isInstanceOf(SourceFetchException.class)
			.hasMessageContaining("id");
	}

	@Test
	void unknownCompanyFailsWithStatusThatIsNotRetryable() {
		server.expect(requestTo(FEED_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example")))
			.isInstanceOfSatisfying(SourceFetchException.class, ex -> {
				assertThat(ex.statusCode()).hasValue(404);
				assertThat(ex.isRetryable()).isFalse();
			});
	}

	@Test
	void refusesTargetsOfOtherSourceTypes() {
		assertThatThrownBy(() -> connector.fetch(SourceTarget.of(SourceType.ASHBY, "example")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private List<RawJob> fetchFixture() {
		server.expect(requestTo(FEED_URL))
			.andRespond(withSuccess(Fixtures.read("personio/feed-example.xml"), MediaType.TEXT_XML));
		return connector.fetch(SourceTarget.of(SourceType.PERSONIO, "example"));
	}

}
