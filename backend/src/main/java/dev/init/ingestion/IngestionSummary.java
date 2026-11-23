package dev.init.ingestion;

import java.util.List;

/** The outcome of one full ingestion over all active sources. */
public record IngestionSummary(List<SourceIngestionResult> results) {

	public IngestionSummary {
		results = List.copyOf(results);
	}

	public long succeeded() {
		return results.stream().filter(result -> result.status() == IngestionRunStatus.SUCCEEDED).count();
	}

	public long failed() {
		return results.stream().filter(result -> result.status() == IngestionRunStatus.FAILED).count();
	}

	public IngestionCounts totals() {
		return results.stream().map(SourceIngestionResult::counts).reduce(IngestionCounts.ZERO, IngestionCounts::plus);
	}

}
