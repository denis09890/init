package dev.init.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs an ingestion over every active source. Sources are fetched in parallel on virtual threads,
 * bounded by {@code init.ingestion.max-parallel-sources} so the database connection pool is never
 * exhausted; politeness towards each host is handled by the HTTP client. Only one full ingestion
 * runs at a time.
 */
@Service
public class IngestionOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(IngestionOrchestrator.class);

	private final CompanySourceRepository sources;

	private final IngestionService ingestion;

	private final IngestionProperties properties;

	private final AtomicBoolean running = new AtomicBoolean();

	public IngestionOrchestrator(CompanySourceRepository sources, IngestionService ingestion,
			IngestionProperties properties) {
		this.sources = sources;
		this.ingestion = ingestion;
		this.properties = properties;
	}

	/**
	 * @throws IngestionAlreadyRunningException if another ingestion is in progress
	 */
	public IngestionSummary runAll() {
		if (!running.compareAndSet(false, true)) {
			throw new IngestionAlreadyRunningException();
		}
		try {
			return execute();
		}
		finally {
			running.set(false);
		}
	}

	private IngestionSummary execute() {
		List<CompanySource> targets = sources.findActiveWithCompany();
		log.info("Ingestion started for {} sources", targets.size());
		Semaphore permits = new Semaphore(properties.maxParallelSources());

		List<SourceIngestionResult> results = new ArrayList<>();
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<SourceIngestionResult>> futures = new ArrayList<>();
			for (CompanySource source : targets) {
				futures.add(executor.submit(() -> ingestOne(source, permits)));
			}
			for (int i = 0; i < targets.size(); i++) {
				results.add(await(futures.get(i), targets.get(i)));
			}
		}

		IngestionSummary summary = new IngestionSummary(results);
		log.info("Ingestion finished: {} succeeded, {} failed, totals {}", summary.succeeded(), summary.failed(),
				summary.totals());
		return summary;
	}

	private SourceIngestionResult ingestOne(CompanySource source, Semaphore permits) {
		try {
			permits.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return SourceIngestionResult.failed(source, "Interrupted before the ingestion started");
		}
		try {
			return ingestion.ingest(source);
		}
		catch (RuntimeException ex) {
			// IngestionService reports its own failures; this only catches what escaped, such as a database outage
			log.error("Ingestion of {} could not be completed", source.getCompany().getSlug(), ex);
			return SourceIngestionResult.failed(source, ex.toString());
		}
		finally {
			permits.release();
		}
	}

	private static SourceIngestionResult await(Future<SourceIngestionResult> future, CompanySource source) {
		try {
			return future.get();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return SourceIngestionResult.failed(source, "Interrupted while waiting for the ingestion");
		}
		catch (ExecutionException ex) {
			return SourceIngestionResult.failed(source, String.valueOf(ex.getCause()));
		}
	}

}
