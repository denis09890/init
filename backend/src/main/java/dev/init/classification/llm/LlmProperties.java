package dev.init.classification.llm;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the LLM classifier (docs/adr/0007-llm-classifier.md). Off unless {@code enabled} is
 * true and an API key is set.
 *
 * @param apiKey Groq API key, from the {@code GROQ_API_KEY} environment variable; never committed
 * @param model a Groq model that supports strict structured outputs
 * @param minInterval pause between two requests, to stay under the account's token and request limits
 * @param maxPerRun postings classified per scheduled pass; the rest wait for the next pass
 * @param maxDescriptionChars how much of the description is sent
 * @param interval time between the end of one pass and the start of the next
 */
@ConfigurationProperties(prefix = "init.llm")
@Validated
public record LlmProperties(
		@DefaultValue("false") boolean enabled,
		String apiKey,
		@DefaultValue("openai/gpt-oss-20b") String model,
		@DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
		@DefaultValue("PT60S") Duration timeout,
		@DefaultValue("PT12S") Duration minInterval,
		@DefaultValue("15") @Min(1) int maxPerRun,
		@DefaultValue("2500") @Min(300) int maxDescriptionChars,
		@DefaultValue("PT3H") Duration interval) {

	/** Enabled and given a key. */
	public boolean isActive() {
		return enabled && apiKey != null && !apiKey.isBlank();
	}

}
