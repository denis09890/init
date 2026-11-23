package dev.init.ingestion;

import java.util.List;

/** The outcome of a full ingestion, as returned by the admin endpoint. */
public record IngestionRunResponse(long succeeded, long failed, IngestionCounts totals,
		List<SourceIngestionResult> results) {

	static IngestionRunResponse from(IngestionSummary summary) {
		return new IngestionRunResponse(summary.succeeded(), summary.failed(), summary.totals(), summary.results());
	}

}
