package dev.init.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.init.IntegrationTest;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.classification.llm.LlmClassifier;
import dev.init.classification.llm.LlmVerdict;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The LLM pass on a real PostgreSQL, with the LLM itself replaced by a mock. */
@IntegrationTest
@Transactional
class LlmReclassifierTests {

	private static final Instant T0 = Instant.parse("2026-11-28T10:00:00Z");

	@MockitoBean
	private LlmClassifier llm;

	@Autowired
	private LlmReclassifier reclassifier;

	@Autowired
	private PostingSynchronizer synchronizer;

	@Autowired
	private SourceConnectors connectors;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private EntityManager entityManager;

	private CompanySource source;

	@BeforeEach
	void setUp() {
		source = sources.save(new CompanySource(companies.save(new Company("Acme", "acme", null)),
				SourceType.GREENHOUSE, "acme", SourceRegion.GLOBAL));
		sync(job(1, "Accountant", "Bucharest, Romania"), job(2, "Senior Engineer", "Bucharest, Romania"),
				job(3, "Sales Manager", "Toronto, Canada"));
		when(llm.isActive()).thenReturn(true);
		when(llm.classify(titled("Accountant"))).thenReturn(Optional.of(
				new LlmVerdict(true, OpportunityType.JUNIOR, Domain.FINANCE, Seniority.ENTRY, "Entry-level accountant.")));
		when(llm.classify(titled("Senior Engineer"))).thenReturn(Optional.of(
				new LlmVerdict(false, OpportunityType.OTHER, Domain.ENGINEERING, Seniority.SENIOR, "Senior role.")));
	}

	@Test
	void appliesTheVerdictToPostingsTheFeedCanShow() {
		assertThat(reclassifier.run(10)).isEqualTo(2);

		Map<String, Object> accountant = row("1");
		assertThat(accountant.get("is_early_career")).isEqualTo(true);
		assertThat(accountant.get("opportunity_type")).isEqualTo("JUNIOR");
		assertThat(accountant.get("domain")).isEqualTo("FINANCE");
		assertThat(accountant.get("seniority")).isEqualTo("ENTRY");
		assertThat(accountant.get("llm_content_hash")).isEqualTo(accountant.get("content_hash"));
		verify(llm, never()).classify(titled("Sales Manager"));
	}

	@Test
	void aJudgedPostingIsNotSentAgainUntilItChanges() {
		reclassifier.run(10);

		assertThat(reclassifier.run(10)).isZero();
		verify(llm, times(2)).classify(any());
	}

	@Test
	void respectsTheLimit() {
		assertThat(reclassifier.run(1)).isEqualTo(1);
	}

	@Test
	void doesNothingWhenTheLlmIsNotActive() {
		when(llm.isActive()).thenReturn(false);

		assertThat(reclassifier.run(10)).isZero();
		verify(llm, never()).classify(any());
	}

	@Test
	void stopsAfterSeveralFailuresInARow() {
		sync(job(4, "Analyst", "Bucharest, Romania"), job(5, "Designer", "Bucharest, Romania"),
				job(6, "Recruiter", "Bucharest, Romania"), job(7, "Buyer", "Bucharest, Romania"));
		when(llm.classify(any())).thenReturn(Optional.empty());

		assertThat(reclassifier.run(10)).isZero();
		verify(llm, times(LlmReclassifier.MAX_CONSECUTIVE_FAILURES)).classify(any());
	}

	private void sync(IngestedJob... jobs) {
		synchronizer.synchronize(source.getId(), new SourceBatch(List.of(jobs), Set.of()), T0);
		entityManager.flush();
	}

	private Map<String, Object> row(String externalId) {
		return jdbc.sql("""
				select is_early_career, opportunity_type, domain, seniority, content_hash, llm_content_hash
				from postings where company_source_id = :source and external_id = :id
				""").param("source", source.getId()).param("id", externalId).query().singleRow();
	}

	private IngestedJob job(int id, String title, String location) {
		RawJob raw = new RawJob(String.valueOf(id), """
				{"id": %d, "title": "%s", "absolute_url": "https://example.com/jobs/%d",
				 "location": {"name": "%s"}, "content": "&lt;p&gt;Join the team.&lt;/p&gt;"}
				""".formatted(id, title, id, location));
		return new IngestedJob(raw, connectors.get(SourceType.GREENHOUSE).normalize(raw));
	}

	private static NormalizedJob titled(String title) {
		return argThat(job -> job != null && title.equals(job.title()));
	}

}
