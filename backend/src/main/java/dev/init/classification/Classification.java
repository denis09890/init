package dev.init.classification;

import java.util.List;
import java.util.Objects;

/**
 * The classifier's verdict on one posting.
 *
 * @param earlyCareer whether the posting suits students and graduates with 0–2 years of experience
 * @param locationEligible whether the posting is in Romania, or remote and open to Romania/EU
 * @param language ISO 639-1 code of the posting text ({@code en}, {@code ro}), or null if unclear
 * @param paid true/false when the posting says so, null when it does not
 * @param reasons human-readable explanation of the rules that fired, for debugging and the UI
 */
public record Classification(
		boolean earlyCareer,
		OpportunityType opportunityType,
		Domain domain,
		Seniority seniority,
		boolean locationEligible,
		String language,
		Boolean paid,
		List<String> reasons) {

	public Classification {
		Objects.requireNonNull(opportunityType, "opportunityType");
		Objects.requireNonNull(domain, "domain");
		Objects.requireNonNull(seniority, "seniority");
		reasons = reasons == null ? List.of() : List.copyOf(reasons);
	}

}
