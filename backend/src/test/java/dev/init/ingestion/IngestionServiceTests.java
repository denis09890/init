package dev.init.ingestion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import dev.init.company.Company;
import dev.init.company.CompanySource;
import dev.init.source.JobSourceConnector;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceConnectors;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceRegion;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Failure isolation and bookkeeping of one source's ingestion; persistence is mocked. */
class IngestionServiceTests {

	private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

	private static final Long SOURCE_ID = 7L;

	private static final Long RUN_ID = 99L;

	private final IngestionRunRecorder recorder = mock(IngestionRunRecorder.class);

	private final PostingSynchronizer synchronizer = mock(PostingSynchronizer.class);

	private final CompanySource source = source();

	@BeforeEach
	void startRuns() {
		when(recorder.start(SOURCE_ID, NOW)).thenReturn(RUN_ID);
	}

	@Test
	void storesTheBatchAndRecordsASuccessfulRun() {
		when(synchronizer.synchronize(eq(SOURCE_ID), any(), eq(NOW))).thenReturn(new SyncResult(2, 0, 0));
		IngestionService service = service(connector(() -> List.of(raw("a"), raw("b")), IngestionServiceTests::normalized));

		SourceIngestionResult result = service.ingest(source);

		IngestionCounts counts = new IngestionCounts(2, 2, 0, 0, 0);
		assertThat(result.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
		assertThat(result.counts()).isEqualTo(counts);
		assertThat(result.company()).isEqualTo("acme");
		verify(recorder).succeed(RUN_ID, counts, null, NOW);
		ArgumentCaptor<SourceBatch> batch = ArgumentCaptor.forClass(SourceBatch.class);
		verify(synchronizer).synchronize(eq(SOURCE_ID), batch.capture(), eq(NOW));
		assertThat(batch.getValue().jobs()).hasSize(2);
		assertThat(batch.getValue().skippedIds()).isEmpty();
	}

	@Test
	void passesTheConfiguredTargetToTheConnector() {
		when(synchronizer.synchronize(any(), any(), any())).thenReturn(new SyncResult(0, 0, 0));
		SourceTarget[] seen = new SourceTarget[1];
		IngestionService service = service(connector(() -> List.of(), IngestionServiceTests::normalized, target -> seen[0] = target));

		service.ingest(source);

		assertThat(seen[0]).isEqualTo(new SourceTarget(SourceType.GREENHOUSE, "acme", SourceRegion.EU));
	}

	@Test
	void aFailedFetchIsRecordedAndNothingIsStored() {
		SourceFetchException failure = new SourceFetchException("GET https://example.com returned HTTP 503");
		IngestionService service = service(connector(() -> {
			throw failure;
		}, IngestionServiceTests::normalized));

		SourceIngestionResult result = service.ingest(source);

		assertThat(result.status()).isEqualTo(IngestionRunStatus.FAILED);
		assertThat(result.error()).isEqualTo("GET https://example.com returned HTTP 503");
		verify(recorder).fail(RUN_ID, "GET https://example.com returned HTTP 503", NOW);
		verify(recorder, never()).succeed(any(), any(), any(), any());
		verifyNoInteractions(synchronizer);
	}

	@Test
	void anUnexpectedConnectorErrorIsRecordedAsAFailedRun() {
		IngestionService service = service(connector(() -> {
			throw new IllegalArgumentException("not a page URL");
		}, IngestionServiceTests::normalized));

		SourceIngestionResult result = service.ingest(source);

		assertThat(result.status()).isEqualTo(IngestionRunStatus.FAILED);
		verify(recorder).fail(RUN_ID, "IllegalArgumentException: not a page URL", NOW);
	}

	@Test
	void aFailureWhileStoringIsRecordedAsAFailedRun() {
		when(synchronizer.synchronize(any(), any(), any())).thenThrow(new IllegalStateException("db down"));
		IngestionService service = service(connector(() -> List.of(raw("a")), IngestionServiceTests::normalized));

		SourceIngestionResult result = service.ingest(source);

		assertThat(result.status()).isEqualTo(IngestionRunStatus.FAILED);
		verify(recorder).fail(RUN_ID, "IllegalStateException: db down", NOW);
	}

	@Test
	void aPostingThatCannotBeNormalizedIsSkippedNotFatal() {
		when(synchronizer.synchronize(any(), any(), any())).thenReturn(new SyncResult(1, 0, 0));
		IngestionService service = service(connector(() -> List.of(raw("a"), raw("b")), raw -> {
			if (raw.externalId().equals("b")) {
				throw new SourceFetchException("Greenhouse posting without title");
			}
			return normalized(raw);
		}));

		SourceIngestionResult result = service.ingest(source);

		assertThat(result.status()).isEqualTo(IngestionRunStatus.SUCCEEDED);
		assertThat(result.counts()).isEqualTo(new IngestionCounts(2, 1, 0, 0, 1));
		ArgumentCaptor<String> warning = ArgumentCaptor.forClass(String.class);
		verify(recorder).succeed(eq(RUN_ID), eq(new IngestionCounts(2, 1, 0, 0, 1)), warning.capture(), eq(NOW));
		assertThat(warning.getValue()).contains("1 of 2 postings skipped").contains("posting b").contains("without title");
		ArgumentCaptor<SourceBatch> batch = ArgumentCaptor.forClass(SourceBatch.class);
		verify(synchronizer).synchronize(eq(SOURCE_ID), batch.capture(), eq(NOW));
		assertThat(batch.getValue().jobs()).hasSize(1);
		assertThat(batch.getValue().skippedIds()).containsExactly("b");
	}

	@Test
	void valuesThatWouldNotFitTheDatabaseAreSkipped() {
		when(synchronizer.synchronize(any(), any(), any())).thenReturn(new SyncResult(0, 0, 0));
		IngestionService service = service(connector(() -> List.of(raw("long-title"), raw("long-id".repeat(40))), raw -> {
			NormalizedJob job = normalized(raw);
			return raw.externalId().equals("long-title")
					? new NormalizedJob(job.externalId(), "t".repeat(IngestionService.MAX_TITLE_LENGTH + 1), job.url(),
							job.locations(), null, RemoteType.UNKNOWN, "", null, null, null, null)
					: job;
		}));

		SourceIngestionResult result = service.ingest(source);

		assertThat(result.counts().skipped()).isEqualTo(2);
		ArgumentCaptor<SourceBatch> batch = ArgumentCaptor.forClass(SourceBatch.class);
		verify(synchronizer).synchronize(eq(SOURCE_ID), batch.capture(), eq(NOW));
		assertThat(batch.getValue().jobs()).isEmpty();
	}

	// ----- helpers ------------------------------------------------------------------------------

	private IngestionService service(JobSourceConnector connector) {
		return new IngestionService(new SourceConnectors(List.of(connector)), recorder, synchronizer,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static CompanySource source() {
		Company company = new Company("Acme", "acme", null);
		CompanySource source = new CompanySource(company, SourceType.GREENHOUSE, "acme", SourceRegion.EU);
		ReflectionTestUtils.setField(source, "id", SOURCE_ID);
		return source;
	}

	private static RawJob raw(String id) {
		return new RawJob(id, "{\"id\":\"" + id + "\"}");
	}

	private static NormalizedJob normalized(RawJob raw) {
		return new NormalizedJob(raw.externalId(), "Job " + raw.externalId(), "https://example.com/" + raw.externalId(),
				List.of("Bucharest"), "RO", RemoteType.HYBRID, "Description.", null, null, null, null);
	}

	private static JobSourceConnector connector(Supplier<List<RawJob>> fetch, Function<RawJob, NormalizedJob> normalize) {
		return connector(fetch, normalize, target -> {
		});
	}

	private static JobSourceConnector connector(Supplier<List<RawJob>> fetch, Function<RawJob, NormalizedJob> normalize,
			Consumer<SourceTarget> onFetch) {
		return new JobSourceConnector() {
			@Override
			public SourceType type() {
				return SourceType.GREENHOUSE;
			}

			@Override
			public List<RawJob> fetch(SourceTarget target) {
				onFetch.accept(target);
				return fetch.get();
			}

			@Override
			public NormalizedJob normalize(RawJob job) {
				return normalize.apply(job);
			}
		};
	}

}
