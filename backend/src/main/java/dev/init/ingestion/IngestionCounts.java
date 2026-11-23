package dev.init.ingestion;

/**
 * Counters of one ingestion run.
 *
 * @param fetched postings the source returned
 * @param skipped fetched postings that could not be processed (see {@link SourceBatch#skippedIds()})
 */
public record IngestionCounts(int fetched, int created, int updated, int closed, int skipped) {

	public static final IngestionCounts ZERO = new IngestionCounts(0, 0, 0, 0, 0);

	public IngestionCounts plus(IngestionCounts other) {
		return new IngestionCounts(fetched + other.fetched, created + other.created, updated + other.updated,
				closed + other.closed, skipped + other.skipped);
	}

}
