package dev.init.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.init.IntegrationTest;
import dev.init.company.Company;
import dev.init.company.CompanyRepository;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.posting.Posting;
import dev.init.posting.PostingRepository;
import dev.init.posting.PostingStatus;
import dev.init.source.JobSourceConnector;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceConnectors;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceRegion;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One source ingested several times against a real database, with a fake connector standing in for
 * the network: run history, closing of vanished postings and the rule that a failed run closes nothing.
 */
@IntegrationTest
@Transactional
class IngestionServiceIntegrationTests {

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	private static final Instant T1 = T0.plus(6, ChronoUnit.HOURS);

	private static final Instant T2 = T0.plus(12, ChronoUnit.HOURS);

	@Autowired
	private IngestionRunRecorder recorder;

	@Autowired
	private PostingSynchronizer synchronizer;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private PostingRepository postings;

	@Autowired
	private IngestionRunRepository runs;

	@Autowired
	private EntityManager entityManager;

	private CompanySource source;

	/** What the fake source currently publishes, or the failure it answers with. */
	private List<String> published = List.of();

	private RuntimeException failure;

	private final JobSourceConnector connector = new JobSourceConnector() {
		@Override
		public SourceType type() {
			return SourceType.GREENHOUSE;
		}

		@Override
		public List<RawJob> fetch(SourceTarget target) {
			if (failure != null) {
				throw failure;
			}
			return published.stream().map(id -> new RawJob(id, "{\"id\":\"" + id + "\"}")).toList();
		}

		@Override
		public NormalizedJob normalize(RawJob job) {
			return new NormalizedJob(job.externalId(), "Software Intern " + job.externalId(),
					"https://example.com/jobs/" + job.externalId(), List.of("Bucharest, Romania"), "RO",
					RemoteType.HYBRID, "Join our team.", null, null, null, null);
		}
	};

	@BeforeEach
	void setUp() {
		Company company = companies.save(new Company("Acme", "acme", null));
		source = sources.save(new CompanySource(company, SourceType.GREENHOUSE, "acme", SourceRegion.GLOBAL));
	}

	@Test
	void ingestsClosesVanishedPostingsAndRecordsEveryRun() {
		published = List.of("a", "b");
		SourceIngestionResult first = serviceAt(T0).ingest(source);
		assertThat(first.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
		assertThat(first.counts()).isEqualTo(new IngestionCounts(2, 2, 0, 0, 0));
		assertThat(openIds()).containsExactlyInAnyOrder("a", "b");
		assertThat(postings.findByCompanySourceIdAndExternalId(source.getId(), "a").orElseThrow().isEarlyCareer())
			.isTrue();

		failure = new SourceFetchException("GET https://example.com returned HTTP 503");
		SourceIngestionResult second = serviceAt(T1).ingest(source);
		assertThat(second.status()).isEqualTo(IngestionRunStatus.FAILED);
		assertThat(openIds()).as("a failed run never closes postings").containsExactlyInAnyOrder("a", "b");

		failure = null;
		published = List.of("a");
		SourceIngestionResult third = serviceAt(T2).ingest(source);
		assertThat(third.counts()).isEqualTo(new IngestionCounts(1, 0, 0, 1, 0));
		assertThat(openIds()).containsExactly("a");

		entityManager.flush();
		entityManager.clear();
		List<IngestionRun> history = runs.findAll(Sort.by("id"));
		assertThat(history).extracting(IngestionRun::getStatus)
			.containsExactly(IngestionRunStatus.SUCCEEDED, IngestionRunStatus.FAILED, IngestionRunStatus.SUCCEEDED);
		assertThat(history).extracting(IngestionRun::getStartedAt).containsExactly(T0, T1, T2);
		assertThat(history.get(0).getNewCount()).isEqualTo(2);
		assertThat(history.get(0).getFetchedCount()).isEqualTo(2);
		assertThat(history.get(1).getErrorMessage()).contains("HTTP 503");
		assertThat(history.get(1).getFinishedAt()).isEqualTo(T1);
		assertThat(history.get(2).getClosedCount()).isEqualTo(1);
		assertThat(history).allMatch(run -> run.getSourceType() == SourceType.GREENHOUSE);
	}

	@Test
	void anEmptySuccessfulResponseClosesEverything() {
		published = List.of("a", "b");
		serviceAt(T0).ingest(source);

		published = List.of();
		SourceIngestionResult result = serviceAt(T1).ingest(source);

		assertThat(result.counts()).isEqualTo(new IngestionCounts(0, 0, 0, 2, 0));
		assertThat(openIds()).isEmpty();
	}

	private IngestionService serviceAt(Instant now) {
		return new IngestionService(new SourceConnectors(List.of(connector)), recorder, synchronizer,
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private List<String> openIds() {
		entityManager.flush();
		entityManager.clear();
		return postings.findByCompanySourceId(source.getId())
			.stream()
			.filter(posting -> posting.getStatus() == PostingStatus.OPEN)
			.map(Posting::getExternalId)
			.toList();
	}

}
