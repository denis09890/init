package dev.init.classification;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Compares a classifier's verdicts with the labels of a {@link LabeledSet}.
 * <p>
 * The sample holds every posting the rules called early career plus a random sample of the others,
 * so two views are reported for the early-career flag: the counts on the sample as it is, and an
 * estimate for the whole population in which each sampled negative stands for
 * {@code rulesNegative / sampledNegatives} postings. Precision is exact (every rules positive is
 * labelled); recall depends on how many early-career postings the rules miss, which only the
 * weighted estimate reflects.
 */
final class ClassifierEvaluation {

	private ClassifierEvaluation() {
	}

	static Report evaluate(PostingClassifier classifier, LabeledSet set) {
		long sampledNegatives = set.postings().stream().filter(LabeledSet.LabeledPosting::sampledNegative).count();
		double negativeWeight = sampledNegatives == 0 ? 1
				: (double) set.population().rulesNegative() / sampledNegatives;
		List<Outcome> outcomes = set.postings()
			.stream()
			.map(posting -> new Outcome(posting, classifier.classify(posting.toJob()),
					posting.sampledNegative() ? negativeWeight : 1))
			.toList();
		return new Report(set.population(), negativeWeight, outcomes);
	}

	/** One labelled posting with the classifier's verdict. */
	record Outcome(LabeledSet.LabeledPosting posting, Classification predicted, double weight) {

		boolean expectedEarlyCareer() {
			return posting.label().earlyCareer();
		}

		boolean predictedEarlyCareer() {
			return predicted.earlyCareer();
		}

		boolean earlyCareerCorrect() {
			return expectedEarlyCareer() == predictedEarlyCareer();
		}

		boolean typeCorrect() {
			return posting.label().opportunityType() == predicted.opportunityType();
		}

		boolean domainCorrect() {
			return posting.label().domain() == predicted.domain();
		}

	}

	/** True/false positives and negatives, possibly weighted. */
	record BinaryCounts(double truePositives, double falsePositives, double falseNegatives, double trueNegatives) {

		double precision() {
			return ratio(truePositives, truePositives + falsePositives);
		}

		double recall() {
			return ratio(truePositives, truePositives + falseNegatives);
		}

		double f1() {
			double precision = precision();
			double recall = recall();
			return Double.isNaN(precision) || Double.isNaN(recall) || precision + recall == 0 ? Double.NaN
					: 2 * precision * recall / (precision + recall);
		}

		double accuracy() {
			return ratio(truePositives + trueNegatives, truePositives + falsePositives + falseNegatives + trueNegatives);
		}

		private static double ratio(double numerator, double denominator) {
			return denominator == 0 ? Double.NaN : numerator / denominator;
		}

	}

	record Report(LabeledSet.Population population, double negativeWeight, List<Outcome> outcomes) {

		/** On the sample, every posting counting once. */
		BinaryCounts earlyCareerOnSample() {
			return earlyCareer(outcome -> 1);
		}

		/** Estimated for every eligible posting: each sampled negative counts for its share of the stratum. */
		BinaryCounts earlyCareerEstimated() {
			return earlyCareer(Outcome::weight);
		}

		/** Share of postings whose opportunity type ({@code OTHER} when not early career) is right. */
		double typeAccuracy() {
			return share(outcomes, Outcome::typeCorrect);
		}

		/** Opportunity type among postings that are early career and that the classifier also calls so. */
		double typeAccuracyOnAgreedEarlyCareer() {
			return share(agreedEarlyCareer(), Outcome::typeCorrect);
		}

		double domainAccuracy() {
			return share(outcomes, Outcome::domainCorrect);
		}

		double domainAccuracyOnEarlyCareer() {
			return share(outcomes.stream().filter(Outcome::expectedEarlyCareer).toList(), Outcome::domainCorrect);
		}

		/** Rows: label; columns: classifier. */
		Map<OpportunityType, Map<OpportunityType, Integer>> typeConfusion() {
			Map<OpportunityType, Map<OpportunityType, Integer>> matrix = new EnumMap<>(OpportunityType.class);
			for (OpportunityType expected : OpportunityType.values()) {
				Map<OpportunityType, Integer> row = new EnumMap<>(OpportunityType.class);
				for (OpportunityType predicted : OpportunityType.values()) {
					row.put(predicted, 0);
				}
				matrix.put(expected, row);
			}
			for (Outcome outcome : outcomes) {
				matrix.get(outcome.posting().label().opportunityType())
					.merge(outcome.predicted().opportunityType(), 1, Integer::sum);
			}
			return matrix;
		}

		List<Outcome> earlyCareerErrors() {
			return outcomes.stream().filter(outcome -> !outcome.earlyCareerCorrect()).toList();
		}

		List<Outcome> typeErrorsOnAgreedEarlyCareer() {
			return agreedEarlyCareer().stream().filter(outcome -> !outcome.typeCorrect()).toList();
		}

		List<Outcome> domainErrorsOnEarlyCareer() {
			return outcomes.stream()
				.filter(Outcome::expectedEarlyCareer)
				.filter(outcome -> !outcome.domainCorrect())
				.toList();
		}

		String toMarkdown() {
			BinaryCounts sample = earlyCareerOnSample();
			BinaryCounts estimate = earlyCareerEstimated();
			StringBuilder md = new StringBuilder();
			md.append("## Early career\n\n")
				.append("Eligible postings when the sample was drawn: ").append(population.eligiblePostings())
				.append(" (").append(population.rulesPositive()).append(" called early career by the rules, ")
				.append(population.rulesNegative()).append(" not).\n")
				.append("Labelled postings: ").append(outcomes.size())
				.append(" (each sampled negative stands for ").append(format(negativeWeight)).append(" postings).\n\n")
				.append("| Metric | On the sample | Estimated for all eligible postings |\n")
				.append("|---|---|---|\n")
				.append(row("Precision", sample.precision(), estimate.precision()))
				.append(row("Recall", sample.recall(), estimate.recall()))
				.append(row("F1", sample.f1(), estimate.f1()))
				.append(row("Accuracy", sample.accuracy(), estimate.accuracy()))
				.append("\nCounts on the sample: ")
				.append("%.0f true positives, %.0f false positives, %.0f false negatives, %.0f true negatives.\n\n"
					.formatted(sample.truePositives(), sample.falsePositives(), sample.falseNegatives(),
							sample.trueNegatives()));

			md.append("## Opportunity type and domain\n\n")
				.append("| Metric | Value |\n|---|---|\n")
				.append("| Opportunity type, all postings | ").append(percent(typeAccuracy())).append(" |\n")
				.append("| Opportunity type, early career by both | ").append(percent(typeAccuracyOnAgreedEarlyCareer()))
				.append(" |\n")
				.append("| Domain, all postings | ").append(percent(domainAccuracy())).append(" |\n")
				.append("| Domain, early-career postings | ").append(percent(domainAccuracyOnEarlyCareer()))
				.append(" |\n\n");

			md.append("Opportunity type confusion matrix (rows: label, columns: classifier):\n\n| |");
			for (OpportunityType type : OpportunityType.values()) {
				md.append(' ').append(type).append(" |");
			}
			md.append("\n|---|").append("---|".repeat(OpportunityType.values().length)).append('\n');
			typeConfusion().forEach((expected, row) -> {
				md.append("| **").append(expected).append("** |");
				row.values().forEach(count -> md.append(' ').append(count).append(" |"));
				md.append('\n');
			});

			md.append("\n## Errors\n\n### Early career\n\n");
			appendErrors(md, earlyCareerErrors(), outcome -> (outcome.expectedEarlyCareer() ? "missed" : "false positive"));
			md.append("\n### Opportunity type (early career by both)\n\n");
			appendErrors(md, typeErrorsOnAgreedEarlyCareer(), outcome -> outcome.posting().label().opportunityType()
					+ " labelled, " + outcome.predicted().opportunityType() + " predicted");
			md.append("\n### Domain (early-career postings)\n\n");
			appendErrors(md, domainErrorsOnEarlyCareer(), outcome -> outcome.posting().label().domain() + " labelled, "
					+ outcome.predicted().domain() + " predicted");
			return md.toString();
		}

		private BinaryCounts earlyCareer(java.util.function.ToDoubleFunction<Outcome> weight) {
			double tp = 0;
			double fp = 0;
			double fn = 0;
			double tn = 0;
			for (Outcome outcome : outcomes) {
				double w = weight.applyAsDouble(outcome);
				if (outcome.expectedEarlyCareer() && outcome.predictedEarlyCareer()) {
					tp += w;
				}
				else if (outcome.predictedEarlyCareer()) {
					fp += w;
				}
				else if (outcome.expectedEarlyCareer()) {
					fn += w;
				}
				else {
					tn += w;
				}
			}
			return new BinaryCounts(tp, fp, fn, tn);
		}

		private List<Outcome> agreedEarlyCareer() {
			return outcomes.stream()
				.filter(outcome -> outcome.expectedEarlyCareer() && outcome.predictedEarlyCareer())
				.toList();
		}

		private static double share(List<Outcome> outcomes, Predicate<Outcome> correct) {
			return outcomes.isEmpty() ? Double.NaN
					: (double) outcomes.stream().filter(correct).count() / outcomes.size();
		}

		private static void appendErrors(StringBuilder md, List<Outcome> errors,
				java.util.function.Function<Outcome, String> what) {
			if (errors.isEmpty()) {
				md.append("None.\n");
				return;
			}
			md.append("| Posting | Company | Error | Rules that fired |\n|---|---|---|---|\n");
			for (Outcome outcome : errors) {
				md.append("| ").append(escape(outcome.posting().input().title()))
					.append(" | ").append(outcome.posting().company())
					.append(" | ").append(what.apply(outcome))
					.append(" | ").append(escape(outcome.predicted().reasons().stream().collect(Collectors.joining("; "))))
					.append(" |\n");
			}
		}

		private static String row(String metric, double onSample, double estimated) {
			return "| " + metric + " | " + percent(onSample) + " | " + percent(estimated) + " |\n";
		}

		private static String percent(double value) {
			return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100);
		}

		private static String format(double value) {
			return String.format(Locale.ROOT, "%.2f", value);
		}

		private static String escape(String text) {
			return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
		}

	}

}
