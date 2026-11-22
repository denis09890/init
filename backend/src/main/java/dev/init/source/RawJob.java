package dev.init.source;

import java.util.Objects;

/**
 * One posting exactly as the source returned it.
 *
 * @param externalId the source's own id for the posting, stable across fetches
 * @param payload the posting as JSON; stored in {@code raw_postings} so it can be normalized again
 * later without fetching
 */
public record RawJob(String externalId, String payload) {

	public RawJob {
		Objects.requireNonNull(externalId, "externalId");
		Objects.requireNonNull(payload, "payload");
	}

}
