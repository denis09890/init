package dev.init.classification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures {@link RuleBasedClassifier} against the hand-labelled sample in
 * {@code src/test/resources/evaluation/} and writes the full report to
 * {@code target/evaluation-report.md}. The floors make a rule change that lowers the scores fail the
 * build; they sit a little below the measured values (see {@code docs/EVALUATION.md}).
 * <p>
 * The classifier only sees the 300-character excerpt here, not the full description, so the scores
 * are a lower bound of what it does in production.
 */
class RuleBasedClassifierEvaluationTests {

	static final Path REPORT = Path.of("target", "evaluation-report.md");

	private static ClassifierEvaluation.Report report;

	@BeforeAll
	static void evaluate() throws IOException {
		LabeledSet set = LabeledSet.load("/evaluation/sample.json", "/evaluation/labels.json");
		report = ClassifierEvaluation.evaluate(new RuleBasedClassifier(), set);
		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, "# Rule-based classifier: evaluation report\n\n" + report.toMarkdown());
	}

	@Test
	void earlyCareerPrecisionAndRecall() {
		ClassifierEvaluation.BinaryCounts estimate = report.earlyCareerEstimated();

		// first measured (2026-11-28): precision 100%, estimated recall 87.0%, recall on the sample 95.8%
		assertThat(estimate.precision()).as("precision").isGreaterThanOrEqualTo(0.95);
		assertThat(estimate.recall()).as("estimated recall").isGreaterThanOrEqualTo(0.80);
		assertThat(report.earlyCareerOnSample().recall()).as("recall on the sample").isGreaterThanOrEqualTo(0.90);
	}

	@Test
	void opportunityTypeAccuracy() {
		// first measured: 98.4% on all postings, 100% on postings both call early career
		assertThat(report.typeAccuracy()).isGreaterThanOrEqualTo(0.95);
		assertThat(report.typeAccuracyOnAgreedEarlyCareer()).isGreaterThanOrEqualTo(0.95);
	}

	@Test
	void domainAccuracy() {
		// 87.3% / 85.4% before the keyword changes, 96.8% / 95.8% after (2026-11-28)
		assertThat(report.domainAccuracy()).isGreaterThanOrEqualTo(0.93);
		assertThat(report.domainAccuracyOnEarlyCareer()).isGreaterThanOrEqualTo(0.93);
	}

}
