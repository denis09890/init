package dev.init.classification;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
	 * Joins an export with its labels. Every exported posting must have exactly one label, and the
	 * opportunity type must be {@code OTHER} exactly when the posting is not early career.
	 *
	 * @param sampleResource classpath resource with the export, e.g. {@code /evaluation/sample.json}
	 * @param labelsResource classpath resource with the labels, e.g. {@code /evaluation/labels.json}
	 * @throws IllegalStateException listing every problem when the two files do not match
	 */
	static LabeledSet load(String sampleResource, String labelsResource) {
		ObjectMapper mapper = JsonMapper.builder().build();
		ExportedSample sample = read(mapper, sampleResource, ExportedSample.class);
		LabelFile labels = read(mapper, labelsResource, LabelFile.class);

		List<String> problems = new ArrayList<>();
		List<LabeledPosting> postings = new ArrayList<>();
		Set<String> exported = new HashSet<>();
		for (ExportedItem item : sample.items()) {
			exported.add(item.key());
			LabelEntry entry = labels.labels().get(item.key());
			if (entry == null) {
				problems.add("no label for " + item.key());
				continue;
			}
			if (entry.earlyCareer() == (entry.opportunityType() == OpportunityType.OTHER)) {
				problems.add(item.key() + ": opportunity type " + entry.opportunityType()
						+ " does not match earlyCareer=" + entry.earlyCareer());
			}
			postings.add(new LabeledPosting(item.key(), item.company(), item.url(), item.stratum(), item.input(),
					new Label(entry.earlyCareer(), entry.opportunityType(), entry.domain()), entry.note()));
		}
		labels.labels().keySet().stream()
			.filter(key -> !exported.contains(key))
			.forEach(key -> problems.add("label for a posting that is not in the export: " + key));
		if (!problems.isEmpty()) {
			throw new IllegalStateException("Labels do not match the export:\n" + String.join("\n", problems));
		}
		return new LabeledSet(labels.description(),
				new Population(sample.eligiblePostings(), sample.rulesPositive(), sample.rulesNegative()), postings);
	}

	private static <T> T read(ObjectMapper mapper, String resource, Class<T> type) {
		try (InputStream in = LabeledSet.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalArgumentException("No resource " + resource);
			}
			return mapper.readValue(in, type);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The parts of an export ({@code GET /api/admin/evaluation/sample}) the evaluation needs. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExportedSample(int eligiblePostings, int rulesPositive, int rulesNegative, List<ExportedItem> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ExportedItem(String key, String company, String url, String stratum, Input input) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LabelFile(String description, Map<String, LabelEntry> labels) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LabelEntry(boolean earlyCareer, OpportunityType opportunityType, Domain domain, String note) {
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
