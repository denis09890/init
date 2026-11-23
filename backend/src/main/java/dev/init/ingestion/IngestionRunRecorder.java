package dev.init.ingestion;

import java.time.Instant;

import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the history of ingestion runs. Each method is its own short transaction, so a run is on
 * record from the moment it starts and its failure is stored even if the work itself was rolled back.
 */
@Service
public class IngestionRunRecorder {

	private final IngestionRunRepository runs;

	private final CompanySourceRepository sources;

	public IngestionRunRecorder(IngestionRunRepository runs, CompanySourceRepository sources) {
		this.runs = runs;
		this.sources = sources;
	}

	/** @return the id of the new run */
	@Transactional
	public Long start(Long sourceId, Instant startedAt) {
		CompanySource source = sources.findById(sourceId)
			.orElseThrow(() -> new IllegalArgumentException("No company source " + sourceId));
		return runs.save(IngestionRun.start(source, startedAt)).getId();
	}

	/** @param warning a note about postings that were skipped, or null */
	@Transactional
	public void succeed(Long runId, IngestionCounts counts, String warning, Instant finishedAt) {
		find(runId).succeed(counts.fetched(), counts.created(), counts.updated(), counts.closed(), warning,
				finishedAt);
	}

	@Transactional
	public void fail(Long runId, String errorMessage, Instant finishedAt) {
		find(runId).fail(errorMessage, finishedAt);
	}

	private IngestionRun find(Long runId) {
		return runs.findById(runId).orElseThrow(() -> new IllegalStateException("No ingestion run " + runId));
	}

}
