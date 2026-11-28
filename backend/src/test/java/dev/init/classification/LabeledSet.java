package dev.init.classification;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;

/**
 * Postings labelled by hand, read from {@code src/test/resources/evaluation/labeled-postings.json}.
 * The file is built from an export of {@code GET /api/admin/evaluation/sample} (dev profile); see
 * {@code docs/EVALUATION.md}.
 *
 * @param population the size of the strata the sample was drawn from, to weight the sampled negatives
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LabeledSet(String description, Population population, List<LabeledPosting> postings) {

	LabeledSet {
		postings = List.copyOf(postings);
	}

	/**
	 * @param rulesPositive postings the rules called early career when the sample was drawn (all labelled)
	 * @param rulesNegative postings they did not (a sample is labelled)
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Population(int eligiblePostings, int rulesPositive, int rulesNegative) {
	}

	/**
	 * @param stratum {@code RULES_POSITIVE} or {@code RULES_NEGATIVE}, as exported
	 * @param note why a label is what it is, when that is not obvious
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record LabeledPosting(String key, String company, String url, String stratum, Input input, Label label,
			String note) {

		boolean sampledNegative() {
			return "RULES_NEGATIVE".equals(stratum);
		}

		/** The posting as the classifier sees it, with the excerpt in place of the full description. */
		NormalizedJob toJob() {
			return new NormalizedJob(key, input.title(), url == null ? key : url, input.locations(),
					input.countryCode(), input.remoteType(), input.excerpt(), null, input.department(),
					input.employmentType(), input.experienceLevel());
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Input(String title, List<String> locations, String countryCode, RemoteType remoteType, String department,
			String employmentType, String experienceLevel, String excerpt) {
	}

	/** {@code opportunityType} is {@code OTHER} exactly when {@code earlyCareer} is false. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Label(boolean earlyCareer, OpportunityType opportunityType, Domain domain) {
	}

}
