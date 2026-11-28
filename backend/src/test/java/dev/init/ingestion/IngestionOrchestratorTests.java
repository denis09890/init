package dev.init.ingestion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.init.company.Company;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.posting.DuplicateDetector;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Parallelism, failure isolation and the single-run guard; the per-source work is mocked. */
class IngestionOrchestratorTests {

	private final CompanySourceRepository sources = mock(CompanySourceRepository.class);

	private final IngestionService ingestion = mock(IngestionService.class);

	private final DuplicateDetector duplicates = mock(DuplicateDetector.class);

	@Test
	void marksDuplicatesOnceEverySourceIsDone() {
		when(sources.findActiveWithCompany()).thenReturn(List.of(source("a"), source("b")));
		when(ingestion.ingest(any())).thenAnswer(invocation -> ok(invocation.getArgument(0)));

		orchestrator(4).runAll();

		verify(duplicates, times(1)).refresh();
	}

	@Test
	void aFailingDuplicateDetectionDoesNotFailTheRun() {
		when(sources.findActiveWithCompany()).thenReturn(List.of(source("a")));
		when(ingestion.ingest(any())).thenAnswer(invocation -> ok(invocation.getArgument(0)));
		when(duplicates.refresh()).thenThrow(new RuntimeException("database restarting"));

		IngestionSummary summary = orchestrator(4).runAll();

		assertThat(summary.succeeded()).isEqualTo(1);
	}

	@Test
	void ingestsEverySourceAndSummarizesTheResults() {
		List<CompanySource> targets = List.of(source("a"), source("b"), source("c"));
		when(sources.findActiveWithCompany()).thenReturn(targets);
		when(ingestion.ingest(any())).thenAnswer(invocation -> ok(invocation.getArgument(0)));

		IngestionSummary summary = orchestrator(4).runAll();

		assertThat(summary.results()).extracting(SourceIngestionResult::company).containsExactly("a", "b", "c");
		assertThat(summary.succeeded()).isEqualTo(3);
		assertThat(summary.failed()).isZero();
		assertThat(summary.totals()).isEqualTo(new IngestionCounts(30, 12, 9, 6, 3));
		verify(ingestion, times(3)).ingest(any());
	}

	@Test
	void aFailingSourceDoesNotStopTheOthers() {
		CompanySource a = source("a");
		CompanySource b = source("b");
		CompanySource c = source("c");
		when(sources.findActiveWithCompany()).thenReturn(List.of(a, b, c));
		when(ingestion.ingest(a)).thenReturn(ok(a));
		when(ingestion.ingest(b)).thenThrow(new RuntimeException("boom"));
		when(ingestion.ingest(c)).thenReturn(ok(c));

		IngestionSummary summary = orchestrator(4).runAll();

		assertThat(summary.results()).extracting(SourceIngestionResult::status)
			.containsExactly(IngestionRunStatus.SUCCEEDED, IngestionRunStatus.FAILED, IngestionRunStatus.SUCCEEDED);
		assertThat(summary.results().get(1).error()).contains("boom");
		assertThat(summary.succeeded()).isEqualTo(2);
		assertThat(summary.failed()).isEqualTo(1);
	}

	@Test
	void neverIngestsMoreSourcesAtOnceThanConfigured() {
		List<CompanySource> targets = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			targets.add(source("s" + i));
		}
		when(sources.findActiveWithCompany()).thenReturn(targets);
		AtomicInteger running = new AtomicInteger();
		AtomicInteger highest = new AtomicInteger();
		when(ingestion.ingest(any())).thenAnswer(invocation -> {
			highest.accumulateAndGet(running.incrementAndGet(), Math::max);
			Thread.sleep(30);
			running.decrementAndGet();
			return ok(invocation.getArgument(0));
		});

		IngestionSummary summary = orchestrator(2).runAll();

		assertThat(summary.succeeded()).isEqualTo(8);
		assertThat(highest.get()).isLessThanOrEqualTo(2);
	}

	@Test
	void rejectsAnIngestionWhileAnotherIsRunning() throws Exception {
		CompanySource a = source("a");
		when(sources.findActiveWithCompany()).thenReturn(List.of(a));
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(ingestion.ingest(any())).thenAnswer(invocation -> {
			started.countDown();
			release.await(5, TimeUnit.SECONDS);
			return ok(invocation.getArgument(0));
		});
		IngestionOrchestrator orchestrator = orchestrator(1);

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<IngestionSummary> first = executor.submit(orchestrator::runAll);
			assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

			assertThatThrownBy(orchestrator::runAll).isInstanceOf(IngestionAlreadyRunningException.class);

			release.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS).succeeded()).isEqualTo(1);
		}
		assertThat(orchestrator.runAll().succeeded()).as("the guard is released afterwards").isEqualTo(1);
	}

	@Test
	void withoutActiveSourcesNothingRuns() {
		when(sources.findActiveWithCompany()).thenReturn(List.of());

		IngestionSummary summary = orchestrator(4).runAll();

		assertThat(summary.results()).isEmpty();
		assertThat(summary.totals()).isEqualTo(IngestionCounts.ZERO);
		verifyNoInteractions(ingestion);
	}

	private IngestionOrchestrator orchestrator(int maxParallelSources) {
		return new IngestionOrchestrator(sources, ingestion,
				new IngestionProperties(false, Duration.ofHours(6), Duration.ofMinutes(1), maxParallelSources),
				duplicates);
	}

	private static CompanySource source(String slug) {
		return new CompanySource(new Company(slug, slug, null), SourceType.GREENHOUSE, slug, SourceRegion.GLOBAL);
	}

	private static SourceIngestionResult ok(CompanySource source) {
		return SourceIngestionResult.succeeded(source, new IngestionCounts(10, 4, 3, 2, 1));
	}

}
