package dev.init.posting;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.init.IntegrationTest;
import dev.init.company.Company;
import dev.init.company.CompanyRepository;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.company.CompanySummary;
import dev.init.company.CompanyQueryRepository;
import dev.init.ingestion.IngestedJob;
import dev.init.ingestion.PostingSynchronizer;
import dev.init.ingestion.SourceBatch;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Duplicate detection on a real PostgreSQL with pg_trgm. */
@IntegrationTest
@Transactional
class DuplicateDetectorTests {

	private static final Instant T0 = Instant.parse("2026-11-28T10:00:00Z");

	private static final Instant T1 = Instant.parse("2026-11-28T16:00:00Z");

	@Autowired
	private DuplicateDetector detector;

	@Autowired
	private PostingSynchronizer synchronizer;

	@Autowired
	private PostingSearchRepository search;

	@Autowired
	private CompanyQueryRepository companyQueries;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private EntityManager entityManager;

	private CompanySource acme;

	private CompanySource acmeSecondBoard;

	private CompanySource globex;

	@BeforeEach
	void setUp() {
		Company acmeCompany = companies.save(new Company("Acme", "acme", null));
		acme = sources.save(new CompanySource(acmeCompany, SourceType.SMARTRECRUITERS, "Acme", SourceRegion.GLOBAL));
		acmeSecondBoard = sources.save(new CompanySource(acmeCompany, SourceType.GREENHOUSE, "acme",
				SourceRegion.GLOBAL));
		globex = sources.save(new CompanySource(companies.save(new Company("Globex", "globex", null)),
				SourceType.GREENHOUSE, "globex", SourceRegion.GLOBAL));
	}

	@Test
	void theSameRoleInTheSameCityIsADuplicateOfTheFirstOne() {
		sync(acme, T0, job("1", "Working Student - Accounts Payable", "Timișoara"),
				job("2", "Working Student – Accounts Payable", "Timișoara"),
				job("3", "Working Student - Accounts Payable", "Timișoara"));

		assertThat(detector.refresh()).isEqualTo(2);

		assertThat(duplicateOf("2")).isEqualTo(id("1"));
		assertThat(duplicateOf("3")).isEqualTo(id("1"));
		assertThat(duplicateOf("1")).isNull();
	}

	@Test
	void theSameTitleInAnotherCityIsNotADuplicate() {
		sync(acme, T0, job("1", "Senior Data Engineer", "Cluj-Napoca"), job("2", "Senior Data Engineer", "Iași"));

		assertThat(detector.refresh()).isZero();
	}

	@Test
	void differentRolesInTheSameCityAreNotDuplicates() {
		sync(acme, T0, job("1", "Working Student - Accounts Payable", "Timișoara"),
				job("2", "Working Student - Accounts Receivable", "Timișoara"));

		assertThat(detector.refresh()).isZero();
	}

	@Test
	void anotherCompanyWithTheSameTitleIsNotADuplicate() {
		sync(acme, T0, job("1", "Software Engineer Intern", "Bucharest"));
		sync(globex, T0, job("1", "Software Engineer Intern", "Bucharest"));

		assertThat(detector.refresh()).isZero();
	}

	@Test
	void theSameRoleOnTwoBoardsOfOneCompanyIsADuplicate() {
		sync(acme, T0, job("1", "Fullstack Software Developer", "Bucharest"));
		sync(acmeSecondBoard, T0, job("9", "Fullstack Software Developer", "Bucharest"));

		assertThat(detector.refresh()).isEqualTo(1);
	}

	@Test
	void aDuplicateBecomesVisibleAgainWhenItsOriginalCloses() {
		sync(acme, T0, job("1", "Software Engineer Intern", "Bucharest"), job("2", "Software Engineer Intern", "Bucharest"));
		detector.refresh();

		sync(acme, T1, job("2", "Software Engineer Intern", "Bucharest"));

		assertThat(detector.refresh()).isZero();
		assertThat(duplicateOf("2")).isNull();
	}

	@Test
	void theFeedAndTheCompanyCountsLeaveDuplicatesOut() {
		sync(acme, T0, job("1", "Software Engineer Intern", "Bucharest"), job("2", "Software Engineer Intern", "Bucharest"),
				job("3", "Marketing Intern", "Bucharest"));
		detector.refresh();

		assertThat(search.search(PostingSearchRequest.defaults()).items()).extracting(PostingResponse::title)
			.containsExactlyInAnyOrder("Software Engineer Intern", "Marketing Intern");
		assertThat(companyQueries.findActive()).filteredOn(company -> company.slug().equals("acme"))
			.extracting(CompanySummary::earlyCareerPostings)
			.containsExactly(2L);
		assertThat(search.findById(id("2"))).as("a duplicate keeps its own page").isPresent();
	}

	private void sync(CompanySource source, Instant now, IngestedJob... jobs) {
		synchronizer.synchronize(source.getId(), new SourceBatch(List.of(jobs), Set.of()), now);
		entityManager.flush();
	}

	/** The id of the posting this one repeats, or null; a single row is read, as the value may be null. */
	private Long duplicateOf(String externalId) {
		Object value = jdbc
			.sql("select duplicate_of_id from postings where company_source_id = :source and external_id = :id")
			.param("source", acme.getId())
			.param("id", externalId)
			.query()
			.singleRow()
			.get("duplicate_of_id");
		return value == null ? null : ((Number) value).longValue();
	}

	private long id(String externalId) {
		return jdbc.sql("select id from postings where company_source_id = :source and external_id = :id")
			.param("source", acme.getId())
			.param("id", externalId)
			.query(Long.class)
			.single();
	}

	private static IngestedJob job(String id, String title, String city) {
		NormalizedJob normalized = new NormalizedJob(id, title, "https://example.com/" + title.hashCode() + "/" + id,
				List.of(city), "RO", RemoteType.HYBRID, "Join us.", T0, null, null, null);
		return new IngestedJob(new RawJob(id, "{\"id\":\"" + id + "\"}"), normalized);
	}

}
