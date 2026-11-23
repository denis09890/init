package dev.init.ingestion;

import java.util.Objects;

import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;

/** A posting as fetched ({@code raw}, stored internally) and in the common shape ({@code normalized}). */
public record IngestedJob(RawJob raw, NormalizedJob normalized) {

	public IngestedJob {
		Objects.requireNonNull(raw, "raw");
		Objects.requireNonNull(normalized, "normalized");
	}

}
