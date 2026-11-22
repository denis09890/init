package dev.init;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.company.Company;
import dev.init.company.CompanyRepository;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.ingestion.IngestionRun;
import dev.init.ingestion.IngestionRunRepository;
import dev.init.ingestion.IngestionRunStatus;
import dev.init.ingestion.RawPosting;
import dev.init.ingestion.RawPostingRepository;
import dev.init.posting.Posting;
import dev.init.posting.PostingRepository;
import dev.init.posting.PostingStatus;
import dev.init.posting.RemoteType;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the Flyway migrations on a real PostgreSQL and checks that the JPA mapping, constraints,
 * array and JSONB columns and full-text search behave as intended. Each test rolls back.
 */
@IntegrationTest
@Transactional
class PersistenceIntegrationTests {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository companySources;

	@Autowired
	private PostingRepository postings;

	@Autowired
	private RawPostingRepository rawPostings;

	@Autowired
	private IngestionRunRepository ingestionRuns;

	@Autowired
	private EntityManager entityManager;

	private CompanySource source;

	@BeforeEach
	void setUp() {
		Company company = companies.save(new Company("Example Corp", "example-corp", "https://example.com"));
		source = companySources.save(new CompanySource(company, SourceType.GREENHOUSE, "examplecorp",
				SourceRegion.GLOBAL));
	}

	@Test
	void persistsAndReloadsPostingWithLocationsAndClassification() {
		Posting posting = posting("job-1", "Software Engineering Intern", "Join our Bucharest team for the summer.");
		posting.applyClassification(true, OpportunityType.INTERNSHIP, Domain.SOFTWARE, Seniority.INTERN, "en", true);
		postings.saveAndFlush(posting);
		entityManager.clear();

		Posting reloaded = postings.findByCompanySourceIdAndExternalId(source.getId(), "job-1").orElseThrow();

		assertThat(reloaded.getTitle()).isEqualTo("Software Engineering Intern");
		assertThat(reloaded.getLocations()).containsExactly("Bucharest", "Cluj-Napoca");
		assertThat(reloaded.getSourceType()).isEqualTo(SourceType.GREENHOUSE);
		assertThat(reloaded.getCompany().getSlug()).isEqualTo("example-corp");
		assertThat(reloaded.getStatus()).isEqualTo(PostingStatus.OPEN);
		assertThat(reloaded.isEarlyCareer()).isTrue();
		assertThat(reloaded.getOpportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
		assertThat(reloaded.getDomain()).isEqualTo(Domain.SOFTWARE);
		assertThat(reloaded.getPaid()).isTrue();
		assertThat(reloaded.getFirstSeenAt()).isEqualTo(NOW);
	}

	@Test
	void rejectsSecondPostingWithSameSourceAndExternalId() {
		postings.saveAndFlush(posting("job-1", "Junior Accountant", null));

		assertThatThrownBy(() -> postings.saveAndFlush(posting("job-1", "Junior Accountant", null)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void fullTextSearchMatchesTitleAndSnippet() {
		postings.saveAndFlush(posting("job-1", "Software Engineering Intern", "Summer internship in Cluj-Napoca."));
		postings.saveAndFlush(posting("job-2", "Senior Backend Engineer", "Lead the payments platform."));

		assertThat(countMatching("intern")).isEqualTo(1);
		assertThat(countMatching("cluj")).isEqualTo(1);
		assertThat(countMatching("engineer")).isEqualTo(1);
		assertThat(countMatching("marketing")).isZero();
	}

	@Test
	void storesRawPayloadAsQueryableJsonb() {
		rawPostings.saveAndFlush(new RawPosting(source, "job-1", "{\"id\": 1, \"title\": \"Data Intern\"}",
				"a".repeat(64), NOW));

		Object title = entityManager
			.createNativeQuery("select payload ->> 'title' from raw_postings where external_id = 'job-1'")
			.getSingleResult();

		assertThat(title).isEqualTo("Data Intern");
		assertThat(rawPostings.existsByCompanySourceIdAndExternalIdAndPayloadHash(source.getId(), "job-1",
				"a".repeat(64)))
			.isTrue();
	}

	@Test
	void recordsSuccessfulIngestionRun() {
		IngestionRun run = IngestionRun.start(source, NOW);
		run.succeed(12, 3, 2, 1, NOW.plusSeconds(5));
		ingestionRuns.saveAndFlush(run);
		entityManager.clear();

		IngestionRun reloaded = ingestionRuns.findById(run.getId()).orElseThrow();

		assertThat(reloaded.getStatus()).isEqualTo(IngestionRunStatus.SUCCEEDED);
		assertThat(reloaded.getSourceType()).isEqualTo(SourceType.GREENHOUSE);
		assertThat(reloaded.getFetchedCount()).isEqualTo(12);
		assertThat(reloaded.getNewCount()).isEqualTo(3);
		assertThat(reloaded.getUpdatedCount()).isEqualTo(2);
		assertThat(reloaded.getClosedCount()).isEqualTo(1);
		assertThat(reloaded.getFinishedAt()).isEqualTo(NOW.plusSeconds(5));
	}

	private Posting posting(String externalId, String title, String snippet) {
		return new Posting(source, externalId, title, "https://example.com/jobs/" + externalId,
				List.of("Bucharest", "Cluj-Napoca"), RemoteType.HYBRID, snippet, NOW, "b".repeat(64), NOW);
	}

	private long countMatching(String query) {
		Number count = (Number) entityManager
			.createNativeQuery("select count(*) from postings where search_vector @@ plainto_tsquery('simple', ?1)")
			.setParameter(1, query)
			.getSingleResult();
		return count.longValue();
	}

}
