package dev.init.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.init.IntegrationTest;
import dev.init.classification.OpportunityType;
import dev.init.company.Company;
import dev.init.company.CompanyRepository;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.SourceConnectors;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The evaluation export on a real PostgreSQL, from payloads stored by the normal ingestion path. */
@IntegrationTest
@Transactional
class EvaluationSampleExporterTests {

	private static final Instant T0 = Instant.parse("2026-11-28T10:00:00Z");

	@Autowired
	private EvaluationSampleExporter exporter;

	@Autowired
	private PostingSynchronizer synchronizer;

	@Autowired
	private SourceConnectors connectors;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void setUp() {
		Company company = companies.save(new Company("Acme", "acme", null));
		CompanySource source = sources.save(new CompanySource(company, SourceType.GREENHOUSE, "acme",
				SourceRegion.GLOBAL));
		IngestedJob intern = job(1, "Software Engineer Intern", "Bucharest, Romania",
				"&lt;p&gt;Summer internship for students.&lt;/p&gt;");
		// stored like any other posting, but its payload can no longer be normalized
		IngestedJob broken = new IngestedJob(new RawJob("9", "{\"id\": 9}"), withId(intern.normalized(), "9"));
		synchronizer.synchronize(source.getId(), new SourceBatch(List.of(
				intern,
				job(2, "Senior Backend Engineer", "Bucharest, Romania", "&lt;p&gt;Lead the platform.&lt;/p&gt;"),
				job(3, "Accountant", "Cluj-Napoca, Romania", "&lt;p&gt;Keep the books.&lt;/p&gt;"),
				job(4, "Sales Manager", "Iasi, Romania", "&lt;p&gt;Grow the region.&lt;/p&gt;"),
				job(5, "Software Engineer Intern", "Toronto, Canada", "&lt;p&gt;Summer internship.&lt;/p&gt;"),
				broken), Set.of()), T0);
		entityManager.flush();
	}

	@Test
	void includesEveryRulesPositiveAndASampleOfTheRestInRomania() {
		EvaluationSample sample = exporter.export(2);

		assertThat(sample.eligiblePostings()).as("Toronto is not eligible, the broken payload is skipped").isEqualTo(4);
		assertThat(sample.rulesPositive()).isEqualTo(1);
		assertThat(sample.rulesNegative()).isEqualTo(3);
		assertThat(sample.sampledNegatives()).isEqualTo(2);
		assertThat(sample.skipped()).isEqualTo(1);
		assertThat(sample.items()).hasSize(3);

		EvaluationSample.Item first = sample.items().getFirst();
		assertThat(first.key()).isEqualTo("GREENHOUSE:acme:1");
		assertThat(first.company()).isEqualTo("acme");
		assertThat(first.url()).isEqualTo("https://example.com/jobs/1");
		assertThat(first.stratum()).isEqualTo(EvaluationSample.Stratum.RULES_POSITIVE);
		assertThat(first.input().title()).isEqualTo("Software Engineer Intern");
		assertThat(first.input().locations()).containsExactly("Bucharest, Romania");
		assertThat(first.input().excerpt()).isEqualTo("Summer internship for students.");
		assertThat(first.rules().earlyCareer()).isTrue();
		assertThat(first.rules().opportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
		assertThat(first.rules().reasons()).isNotEmpty();
		assertThat(sample.items().subList(1, 3)).allMatch(item -> item.stratum() == EvaluationSample.Stratum.RULES_NEGATIVE
				&& !item.rules().earlyCareer());
	}

	@Test
	void theSampleIsTheSameForTheSameData() {
		List<String> first = exporter.export(2).items().stream().map(EvaluationSample.Item::key).toList();
		List<String> second = exporter.export(2).items().stream().map(EvaluationSample.Item::key).toList();

		assertThat(second).isEqualTo(first);
	}

	@Test
	void asksForMoreNegativesThanThereAre() {
		EvaluationSample sample = exporter.export(50);

		assertThat(sample.sampledNegatives()).isEqualTo(3);
		assertThat(sample.items()).hasSize(4);
	}

	@Test
	void refusesANegativeSampleSize() {
		assertThatThrownBy(() -> exporter.export(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	private IngestedJob job(int id, String title, String location, String escapedHtml) {
		RawJob raw = new RawJob(String.valueOf(id), """
				{"id": %d, "title": "%s", "absolute_url": "https://example.com/jobs/%d",
				 "location": {"name": "%s"}, "content": "%s"}
				""".formatted(id, title, id, location, escapedHtml));
		return new IngestedJob(raw, connectors.get(SourceType.GREENHOUSE).normalize(raw));
	}

	private static NormalizedJob withId(NormalizedJob job, String externalId) {
		return new NormalizedJob(externalId, job.title(), job.url() + "-broken", job.locations(), job.countryCode(),
				job.remoteType(), job.description(), job.postedAt(), job.department(), job.employmentType(),
				job.experienceLevel());
	}

}
