package dev.init.ingestion;

import java.util.List;
import java.util.Set;

/**
 * Everything one source returned in one run.
 *
 * @param jobs postings that were fetched and normalized
 * @param skippedIds ids of postings the source did publish but that could not be normalized. They
 * count as still published, so a posting that breaks the normalizer is not closed by mistake.
 */
public record SourceBatch(List<IngestedJob> jobs, Set<String> skippedIds) {

	public SourceBatch {
		jobs = List.copyOf(jobs);
		skippedIds = Set.copyOf(skippedIds);
	}

}
