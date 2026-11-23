package dev.init.ingestion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.init.company.CompanySource;
import dev.init.source.JobSourceConnector;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.SourceConnectors;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ingests one source: records the run, fetches and normalizes outside any transaction (network calls
 * must not hold a database connection), then stores the batch in one transaction.
 * <p>
 * Failure isolation: whatever goes wrong with this source ends up on its {@code ingestion_runs} row
 * and in the returned result. Nothing is thrown at the caller, so one broken source never stops the
 * others. A posting that cannot be normalized is skipped, not fatal.
 */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	/** Column sizes of {@code postings} and {@code raw_postings}. */
	static final int MAX_EXTERNAL_ID_LENGTH = 200;

	static final int MAX_TITLE_LENGTH = 500;

	static final int MAX_URL_LENGTH = 2000;

	private final SourceConnectors connectors;

	private final IngestionRunRecorder recorder;

	private final PostingSynchronizer synchronizer;

	private final Clock clock;

	public IngestionService(SourceConnectors connectors, IngestionRunRecorder recorder,
			PostingSynchronizer synchronizer, Clock clock) {
		this.connectors = connectors;
		this.recorder = recorder;
		this.synchronizer = synchronizer;
		this.clock = clock;
	}

	/** @param source an active source whose company is loaded (see {@code findActiveWithCompany}) */
	public SourceIngestionResult ingest(CompanySource source) {
		String label = source.getCompany().getSlug() + "/" + source.getSourceType();
		Instant started = clock.instant();
		Long runId = recorder.start(source.getId(), started);
		try {
			JobSourceConnector connector = connectors.get(source.getSourceType());
			List<RawJob> fetched = connector
				.fetch(new SourceTarget(source.getSourceType(), source.getIdentifier(), source.getRegion()));
			NormalizedBatch normalized = normalize(connector, fetched, label);
			SyncResult sync = synchronizer.synchronize(source.getId(), normalized.batch(), clock.instant());

			IngestionCounts counts = new IngestionCounts(fetched.size(), sync.created(), sync.updated(),
					sync.closed(), normalized.batch().skippedIds().size());
			recorder.succeed(runId, counts, normalized.warning(), clock.instant());
			log.info("Ingested {}: {} in {} ms", label, counts, Duration.between(started, clock.instant()).toMillis());
			return SourceIngestionResult.succeeded(source, counts);
		}
		catch (Exception ex) {
			String message = describe(ex);
			if (ex instanceof SourceFetchException) {
				log.warn("Ingestion of {} failed: {}", label, message);
			}
			else {
				log.error("Ingestion of {} failed unexpectedly", label, ex);
			}
			recorder.fail(runId, message, clock.instant());
			return SourceIngestionResult.failed(source, message);
		}
	}

	private NormalizedBatch normalize(JobSourceConnector connector, List<RawJob> fetched, String label) {
		List<IngestedJob> jobs = new ArrayList<>();
		Set<String> skipped = new LinkedHashSet<>();
		String firstProblem = null;
		for (RawJob raw : fetched) {
			try {
				NormalizedJob job = connector.normalize(raw);
				validate(raw, job);
				jobs.add(new IngestedJob(raw, job));
			}
			catch (RuntimeException ex) {
				skipped.add(raw.externalId());
				String problem = "posting %s: %s".formatted(raw.externalId(), ex.getMessage());
				log.warn("Skipping {} in {}", problem, label);
				if (firstProblem == null) {
					firstProblem = problem;
				}
			}
		}
		String warning = skipped.isEmpty() ? null
				: "%d of %d postings skipped, first: %s".formatted(skipped.size(), fetched.size(), firstProblem);
		return new NormalizedBatch(new SourceBatch(jobs, skipped), warning);
	}

	/** Values that would not fit their database column are rejected here instead of failing the whole batch. */
	private static void validate(RawJob raw, NormalizedJob job) {
		if (raw.externalId().length() > MAX_EXTERNAL_ID_LENGTH) {
			throw new IllegalArgumentException("external id longer than " + MAX_EXTERNAL_ID_LENGTH + " characters");
		}
		if (job.title().length() > MAX_TITLE_LENGTH) {
			throw new IllegalArgumentException("title longer than " + MAX_TITLE_LENGTH + " characters");
		}
		if (job.url().length() > MAX_URL_LENGTH) {
			throw new IllegalArgumentException("url longer than " + MAX_URL_LENGTH + " characters");
		}
	}

	private static String describe(Exception ex) {
		if (ex instanceof SourceFetchException) {
			return String.valueOf(ex.getMessage());
		}
		return ex.getClass().getSimpleName() + ": " + ex.getMessage();
	}

	private record NormalizedBatch(SourceBatch batch, String warning) {

	}

}
