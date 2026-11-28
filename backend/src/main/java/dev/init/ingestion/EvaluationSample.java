package dev.init.ingestion;

import java.time.Instant;
import java.util.List;

import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.source.RemoteType;

/**
 * A sample of postings for labelling by hand, to measure the classifier
 * (see {@code docs/EVALUATION.md}). Only postings the feed could show are considered: open and
 * located in Romania (or remote and open to it). Every posting the rules call early career is
 * included; the others are a reproducible sample, so precision is measured on all of them and recall
 * is estimated from the sample.
 *
 * @param eligiblePostings open postings that are location-eligible and could be normalized
 * @param rulesPositive how many of them the rules call early career (all are in the sample)
 * @param rulesNegative how many the rules do not call early career
 * @param sampledNegatives how many of those are in the sample
 * @param skipped postings whose stored payload could not be normalized again
 */
public record EvaluationSample(
		Instant generatedAt,
		int eligiblePostings,
		int rulesPositive,
		int rulesNegative,
		int sampledNegatives,
		int skipped,
		List<Item> items) {

	public EvaluationSample {
		items = List.copyOf(items);
	}

	/** Whether a posting entered the sample as a rules positive (all of them) or as a sampled negative. */
	public enum Stratum {
		RULES_POSITIVE, RULES_NEGATIVE
	}

	/**
	 * @param key {@code SOURCE_TYPE:company-slug:external-id}, stable across exports
	 * @param url the original posting, to check a label by hand
	 */
	public record Item(String key, String company, String url, Stratum stratum, Input input, RulesVerdict rules) {
	}

	/**
	 * Exactly what the classifier reads, except that the description is cut to the public snippet
	 * length: anything committed to the repository is limited to such excerpts.
	 */
	public record Input(String title, List<String> locations, String countryCode, RemoteType remoteType,
			String department, String employmentType, String experienceLevel, String excerpt) {
	}

	/** The rules' verdict on the full description at export time. */
	public record RulesVerdict(boolean earlyCareer, OpportunityType opportunityType, Domain domain, Seniority seniority,
			List<String> reasons) {
	}

}
