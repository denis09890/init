package dev.init.classification;

import java.util.List;

import dev.init.source.RemoteType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** The metrics themselves, on a tiny set with a stub classifier. */
class ClassifierEvaluationTests {

	/** Calls anything with "Intern" in the title an internship, always in software. */
	private static final PostingClassifier STUB = job -> {
		boolean intern = job.title().contains("Intern");
		return new Classification(intern, intern ? OpportunityType.INTERNSHIP : OpportunityType.OTHER, Domain.SOFTWARE,
				Seniority.UNKNOWN, true, "en", null, List.of(intern ? "title says intern" : "nothing"));
	};

	private static final LabeledSet SET = new LabeledSet("test", new LabeledSet.Population(22, 2, 20), List.of(
			posting("Software Intern", "RULES_POSITIVE", true, OpportunityType.INTERNSHIP, Domain.SOFTWARE),
			posting("Intern Relations Manager", "RULES_POSITIVE", false, OpportunityType.OTHER, Domain.HR),
			posting("Junior Analyst", "RULES_NEGATIVE", true, OpportunityType.JUNIOR, Domain.FINANCE),
			posting("Senior Engineer", "RULES_NEGATIVE", false, OpportunityType.OTHER, Domain.SOFTWARE)));

	private final ClassifierEvaluation.Report report = ClassifierEvaluation.evaluate(STUB, SET);

	@Test
	void countsTheEarlyCareerFlagOnTheSample() {
		ClassifierEvaluation.BinaryCounts counts = report.earlyCareerOnSample();

		assertThat(counts).isEqualTo(new ClassifierEvaluation.BinaryCounts(1, 1, 1, 1));
		assertThat(counts.precision()).isEqualTo(0.5);
		assertThat(counts.recall()).isEqualTo(0.5);
		assertThat(counts.f1()).isEqualTo(0.5);
		assertThat(counts.accuracy()).isEqualTo(0.5);
	}

	@Test
	void weightsSampledNegativesByTheSizeOfTheirStratum() {
		ClassifierEvaluation.BinaryCounts estimate = report.earlyCareerEstimated();

		assertThat(report.negativeWeight()).as("20 negatives in the population, 2 labelled").isEqualTo(10);
		assertThat(estimate).isEqualTo(new ClassifierEvaluation.BinaryCounts(1, 1, 10, 10));
		assertThat(estimate.precision()).as("positives are a census").isEqualTo(0.5);
		assertThat(estimate.recall()).isCloseTo(1.0 / 11, within(1e-9));
	}

	@Test
	void measuresTypeAndDomain() {
		assertThat(report.typeAccuracy()).isEqualTo(0.5);
		assertThat(report.typeAccuracyOnAgreedEarlyCareer()).isEqualTo(1.0);
		assertThat(report.domainAccuracy()).isEqualTo(0.5);
		assertThat(report.domainAccuracyOnEarlyCareer()).as("software intern right, junior analyst wrong")
			.isEqualTo(0.5);
		assertThat(report.typeConfusion().get(OpportunityType.JUNIOR).get(OpportunityType.OTHER)).isEqualTo(1);
		assertThat(report.typeConfusion().get(OpportunityType.OTHER).get(OpportunityType.INTERNSHIP)).isEqualTo(1);
	}

	@Test
	void listsTheErrors() {
		assertThat(report.earlyCareerErrors()).extracting(outcome -> outcome.posting().input().title())
			.containsExactly("Intern Relations Manager", "Junior Analyst");
		assertThat(report.domainErrorsOnEarlyCareer()).extracting(outcome -> outcome.posting().input().title())
			.containsExactly("Junior Analyst");
	}

	@Test
	void rendersAMarkdownReport() {
		String markdown = report.toMarkdown();

		assertThat(markdown).contains("| Precision | 50.0% | 50.0% |")
			.contains("| Recall | 50.0% | 9.1% |")
			.contains("| Intern Relations Manager | acme | false positive | title says intern |")
			.contains("| Junior Analyst | acme | missed | nothing |");
	}

	@Test
	void undefinedRatiosAreNotNumbers() {
		ClassifierEvaluation.BinaryCounts none = new ClassifierEvaluation.BinaryCounts(0, 0, 0, 5);

		assertThat(none.precision()).isNaN();
		assertThat(none.f1()).isNaN();
		assertThat(none.accuracy()).isEqualTo(1.0);
	}

	private static LabeledSet.LabeledPosting posting(String title, String stratum, boolean earlyCareer,
			OpportunityType type, Domain domain) {
		LabeledSet.Input input = new LabeledSet.Input(title, List.of("Bucharest, Romania"), null, RemoteType.HYBRID,
				null, null, null, "Short excerpt.");
		return new LabeledSet.LabeledPosting("GREENHOUSE:acme:" + title.hashCode(), "acme",
				"https://example.com/" + title.hashCode(), stratum, input,
				new LabeledSet.Label(earlyCareer, type, domain), null);
	}

}
