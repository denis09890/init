package dev.init.ingestion;

import dev.init.company.CompanySource;
import dev.init.source.SourceType;

/** The outcome of ingesting one source; {@code error} is set when the run failed. */
public record SourceIngestionResult(
		String company,
		SourceType type,
		String identifier,
		IngestionRunStatus status,
		IngestionCounts counts,
		String error) {

	public static SourceIngestionResult succeeded(CompanySource source, IngestionCounts counts) {
		return new SourceIngestionResult(source.getCompany().getSlug(), source.getSourceType(),
				source.getIdentifier(), IngestionRunStatus.SUCCEEDED, counts, null);
	}

	public static SourceIngestionResult failed(CompanySource source, String error) {
		return new SourceIngestionResult(source.getCompany().getSlug(), source.getSourceType(),
				source.getIdentifier(), IngestionRunStatus.FAILED, IngestionCounts.ZERO, error);
	}

}
