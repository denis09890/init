package dev.init.classification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import dev.init.classification.llm.LlmClassifier;
import dev.init.classification.llm.LlmProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the LLM classifier on the same labelled sample as the rules (docs/EVALUATION.md). It calls
 * the real API, so it only runs when {@code GROQ_API_KEY} is set in the environment, never in CI:
 * <pre>
 * $env:GROQ_API_KEY = "..."; .\mvnw.cmd -B test "-Dtest=LlmClassifierEvaluationTests"
 * </pre>
 * With the default pacing a run takes about 20 minutes and stays inside the free plan's limits. The
 * report goes to {@code target/evaluation-report-llm.md}. When the LLM gives no usable answer, the
 * rules' classification is used, as in production, and the report says how often that happened.
 */
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class LlmClassifierEvaluationTests {

	static final Path REPORT = Path.of("target", "evaluation-report-llm.md");

	@Test
	void measuresTheLlmOnTheLabelledSample() throws IOException {
		String model = System.getenv().getOrDefault("LLM_MODEL", "openai/gpt-oss-20b");
		long intervalSeconds = Long.parseLong(System.getenv().getOrDefault("LLM_MIN_INTERVAL_SECONDS", "10"));
		LlmClassifier llm = new LlmClassifier(new LlmProperties(true, System.getenv("GROQ_API_KEY"), model,
				"https://api.groq.com/openai/v1", Duration.ofSeconds(60), Duration.ofSeconds(intervalSeconds), 1000,
				2500, Duration.ofHours(3)), JsonMapper.builder().build());
		RuleBasedClassifier rules = new RuleBasedClassifier();
		AtomicInteger fallbacks = new AtomicInteger();
		PostingClassifier llmWithFallback = job -> {
			Classification byRules = rules.classify(job);
			return llm.classify(job).map(verdict -> verdict.over(byRules)).orElseGet(() -> {
				fallbacks.incrementAndGet();
				return byRules;
			});
		};

		LabeledSet set = LabeledSet.load("/evaluation/sample.json", "/evaluation/labels.json");
		ClassifierEvaluation.Report report = ClassifierEvaluation.evaluate(llmWithFallback, set);

		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, "# LLM classifier (" + model + "): evaluation report\n\n" + "The rules were used for "
				+ fallbacks.get() + " of " + set.postings().size() + " postings (no usable answer).\n\n"
				+ report.toMarkdown());
		assertThat(fallbacks.get()).as("postings the LLM did not classify").isLessThanOrEqualTo(set.postings().size() / 10);
	}

}
