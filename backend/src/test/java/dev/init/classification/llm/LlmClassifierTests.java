package dev.init.classification.llm;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.init.classification.Classification;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmClassifierTests {

	private static final String URL = "https://api.groq.test/openai/v1/chat/completions";

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	private final ObjectMapper mapper = JsonMapper.builder().build();

	@Test
	void sendsThePostingWithAStrictSchemaAndReadsTheVerdict() {
		server.expect(requestTo(URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
			.andExpect(jsonPath("$.temperature").value(0))
			.andExpect(jsonPath("$.messages[0].role").value("system"))
			.andExpect(jsonPath("$.response_format.type").value("json_schema"))
			.andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
			.andExpect(jsonPath("$.response_format.json_schema.schema.additionalProperties").value(false))
			.andExpect(jsonPath("$.response_format.json_schema.schema.properties.domain.enum[0]").value("SOFTWARE"))
			.andRespond(withSuccess(chat("""
					{"earlyCareer": true, "opportunityType": "INTERNSHIP", "domain": "SOFTWARE",
					 "seniority": "INTERN", "reason": "A summer internship for students."}
					"""), MediaType.APPLICATION_JSON));

		LlmVerdict verdict = classifier(true).classify(job("Software Engineer Intern", "Summer internship.")).orElseThrow();

		server.verify();
		assertThat(verdict).isEqualTo(new LlmVerdict(true, OpportunityType.INTERNSHIP, Domain.SOFTWARE, Seniority.INTERN,
				"A summer internship for students."));
	}

	@Test
	void aPostingThatIsNotEarlyCareerHasNoOpportunityType() {
		server.expect(requestTo(URL)).andRespond(withSuccess(chat("""
				{"earlyCareer": false, "opportunityType": "JUNIOR", "domain": "DATA", "seniority": "SENIOR",
				 "reason": "Asks for 5 years."}
				"""), MediaType.APPLICATION_JSON));

		LlmVerdict verdict = classifier(true).classify(job("Data Engineer", "5+ years.")).orElseThrow();

		assertThat(verdict.opportunityType()).isEqualTo(OpportunityType.OTHER);
	}

	@Test
	void anAnswerOutsideTheSchemaIsIgnored() {
		server.expect(requestTo(URL)).andRespond(withSuccess(chat("""
				{"earlyCareer": true, "opportunityType": "APPRENTICE", "domain": "DATA", "seniority": "INTERN",
				 "reason": "?"}
				"""), MediaType.APPLICATION_JSON));

		assertThat(classifier(true).classify(job("Data Intern", ""))).isEmpty();
	}

	@Test
	void anEarlyCareerAnswerWithoutAnOpportunityTypeIsIgnored() {
		server.expect(requestTo(URL)).andRespond(withSuccess(chat("""
				{"earlyCareer": true, "opportunityType": "OTHER", "domain": "DATA", "seniority": "ENTRY",
				 "reason": "?"}
				"""), MediaType.APPLICATION_JSON));

		assertThat(classifier(true).classify(job("Data Analyst", ""))).isEmpty();
	}

	@Test
	void aRateLimitGivesNoVerdict() {
		server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThat(classifier(true).classify(job("Data Intern", ""))).isEmpty();
	}

	@Test
	void doesNothingWhenDisabledOrWithoutAKey() {
		assertThat(classifier(false).classify(job("Data Intern", ""))).isEmpty();
		LlmClassifier noKey = new LlmClassifier(new LlmProperties(true, " ", "openai/gpt-oss-20b",
				"https://api.groq.test/openai/v1", Duration.ofSeconds(5), Duration.ZERO, 10, 300, Duration.ofHours(3)),
				builder.build(), mapper);
		assertThat(noKey.isActive()).isFalse();
		assertThat(noKey.classify(job("Data Intern", ""))).isEmpty();

		server.verify();
	}

	@Test
	void sendsOnlyTheStartOfALongDescription() {
		Map<String, Object> request = classifier(true).request(job("Intern", "a".repeat(300) + "TAIL"));

		@SuppressWarnings("unchecked")
		List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
		assertThat(messages.get(1).get("content")).doesNotContain("TAIL").contains("\"title\":\"Intern\"");
	}

	@Test
	void theVerdictKeepsLocationLanguageAndPayFromTheRules() {
		Classification rules = new Classification(false, OpportunityType.OTHER, Domain.OTHER, Seniority.UNKNOWN, true,
				"ro", Boolean.TRUE, List.of("no early-career signal"));
		LlmVerdict verdict = new LlmVerdict(true, OpportunityType.JUNIOR, Domain.FINANCE, Seniority.ENTRY,
				"Entry-level accountant.");

		Classification merged = verdict.over(rules);

		assertThat(merged.earlyCareer()).isTrue();
		assertThat(merged.opportunityType()).isEqualTo(OpportunityType.JUNIOR);
		assertThat(merged.locationEligible()).isTrue();
		assertThat(merged.language()).isEqualTo("ro");
		assertThat(merged.paid()).isTrue();
		assertThat(merged.reasons()).containsExactly("LLM: Entry-level accountant.");
	}

	private LlmClassifier classifier(boolean enabled) {
		return new LlmClassifier(new LlmProperties(enabled, "test-key", "openai/gpt-oss-20b",
				"https://api.groq.test/openai/v1", Duration.ofSeconds(5), Duration.ZERO, 10, 300, Duration.ofHours(3)),
				builder.build(), mapper);
	}

	private String chat(String content) {
		return mapper.writeValueAsString(Map.of("choices",
				List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", content)))));
	}

	private static NormalizedJob job(String title, String description) {
		return new NormalizedJob("1", title, "https://example.com/1", List.of("Bucharest"), "RO", RemoteType.HYBRID,
				description, null, null, null, null);
	}

}
