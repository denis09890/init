package dev.init.classification.llm;

import java.util.List;
import java.util.Objects;

import dev.init.classification.Classification;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;

/**
 * What the LLM decided about one posting. It covers the judgement calls (early career, type, domain,
 * seniority); location, language and pay stay with the rules, which read them reliably.
 *
 * @param reason the model's one-sentence explanation, kept like the rules' reasons
 */
public record LlmVerdict(boolean earlyCareer, OpportunityType opportunityType, Domain domain, Seniority seniority,
		String reason) {

	public LlmVerdict {
		Objects.requireNonNull(opportunityType, "opportunityType");
		Objects.requireNonNull(domain, "domain");
		Objects.requireNonNull(seniority, "seniority");
		if (!earlyCareer) {
			opportunityType = OpportunityType.OTHER;
		}
	}

	/** This verdict, with location, language and pay taken from the rules' classification. */
	public Classification over(Classification rules) {
		return new Classification(earlyCareer, opportunityType, domain, seniority, rules.locationEligible(),
				rules.language(), rules.paid(), List.of("LLM: " + (reason == null ? "no reason given" : reason)));
	}

}
