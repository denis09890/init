package dev.init.posting;

import java.time.Instant;
import java.util.List;

import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.source.RemoteType;

/**
 * A posting as the public API shows it: a short snippet and a link to the original, never the full
 * description or the raw payload.
 *
 * @param url the original posting on the company's own site or ATS
 * @param snippet the start of the description, at most {@value Posting#SNIPPET_MAX_LENGTH} characters
 * @param paid {@code null} when the posting does not say
 * @param skills skills the posting mentions, from a fixed vocabulary
 */
public record PostingResponse(
		Long id,
		String title,
		CompanyRef company,
		String url,
		List<String> locations,
		RemoteType remoteType,
		String snippet,
		Instant postedAt,
		Instant firstSeenAt,
		Instant lastSeenAt,
		Instant closedAt,
		PostingStatus status,
		boolean earlyCareer,
		boolean locationEligible,
		OpportunityType opportunityType,
		Domain domain,
		Seniority seniority,
		String language,
		Boolean paid,
		List<String> skills) {

	public PostingResponse {
		locations = List.copyOf(locations);
		skills = skills == null ? List.of() : List.copyOf(skills);
	}

	public record CompanyRef(String name, String slug) {

	}

}
