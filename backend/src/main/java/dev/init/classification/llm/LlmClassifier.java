package dev.init.classification.llm;

import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.source.NormalizedJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Asks an LLM (Groq's OpenAI-compatible chat completions API) to classify a posting, with a strict
 * JSON schema so the answer can only use Init's own categories. Any failure (no key, HTTP error,
 * rate limit, an answer that does not fit) gives an empty result, and the caller keeps the rules'
 * classification.
 * <p>
 * Requests are paced ({@code init.llm.min-interval}) to stay under the account's limits.
 *
 * @see <a href="https://console.groq.com/docs/api-reference">Groq API reference</a>
 * @see <a href="https://console.groq.com/docs/structured-outputs">Groq structured outputs</a>
 */
@Component
public class LlmClassifier {

	private static final Logger log = LoggerFactory.getLogger(LlmClassifier.class);

	static final String SYSTEM_PROMPT = """
			You classify job postings for Init, a site that lists early-career opportunities in Romania.
			Early career means suitable for a student or a graduate with 0-2 years of experience, entry-level
			jobs in any field included. A posting that asks for more than 2 years, or a senior, lead or
			management role, is not early career.
			Opportunity types: INTERNSHIP (internships, summer practice, apprenticeships, scholarships),
			WORKING_STUDENT (part-time roles for enrolled students), GRADUATE_PROGRAM (graduate, trainee
			and new-grad programmes), JUNIOR (junior and entry-level jobs), OTHER (not early career).
			Domain is the kind of work, not the department name: SOFTWARE (software, IT, security, SAP),
			DATA (data, analytics, BI, AI), ENGINEERING (mechanical, electrical, manufacturing), DESIGN,
			PRODUCT, FINANCE, MARKETING, SALES, HR, LEGAL, OPERATIONS (logistics, purchasing, project and
			facility management), CUSTOMER_SUPPORT, RESEARCH, OTHER.
			Seniority: INTERN, ENTRY, JUNIOR, MID, SENIOR, LEAD or UNKNOWN.
			Postings may be in English or Romanian. Answer only with JSON that matches the schema, with a
			one-sentence reason in English.
			""";

	private final LlmProperties properties;

	private final RestClient restClient;

	private final ObjectMapper mapper;

	private final ReentrantLock pacing = new ReentrantLock();

	private long nextRequestNanos = System.nanoTime();

	@Autowired
	public LlmClassifier(LlmProperties properties, ObjectMapper mapper) {
		this(properties, RestClient.builder().requestFactory(requestFactory(properties)).build(), mapper);
	}

	LlmClassifier(LlmProperties properties, RestClient restClient, ObjectMapper mapper) {
		this.properties = properties;
		this.restClient = restClient;
		this.mapper = mapper;
	}

	public boolean isActive() {
		return properties.isActive();
	}

	public Optional<LlmVerdict> classify(NormalizedJob job) {
		if (!isActive()) {
			return Optional.empty();
		}
		try {
			pace();
			String response = restClient.post()
				.uri(properties.baseUrl() + "/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
				.body(mapper.writeValueAsString(request(job)))
				.retrieve()
				.body(String.class);
			return parse(response);
		}
		catch (RestClientException | JacksonException ex) {
			log.warn("LLM classification of '{}' failed: {}", job.title(), ex.getMessage());
			return Optional.empty();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	Map<String, Object> request(NormalizedJob job) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("model", properties.model());
		request.put("temperature", 0);
		request.put("max_completion_tokens", 1024);
		request.put("messages", List.of(
				Map.of("role", "system", "content", SYSTEM_PROMPT),
				Map.of("role", "user", "content", mapper.writeValueAsString(postingFields(job)))));
		request.put("response_format", Map.of("type", "json_schema", "json_schema",
				Map.of("name", "posting_classification", "strict", true, "schema", schema())));
		return request;
	}

	private Map<String, Object> postingFields(NormalizedJob job) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("title", job.title());
		fields.put("locations", job.locations());
		fields.put("department", job.department());
		fields.put("employmentType", job.employmentType());
		fields.put("experienceLevel", job.experienceLevel());
		String description = job.description();
		fields.put("description", description.length() <= properties.maxDescriptionChars() ? description
				: description.substring(0, properties.maxDescriptionChars()));
		return fields;
	}

	static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("earlyCareer", Map.of("type", "boolean"));
		properties.put("opportunityType", Map.of("type", "string", "enum", names(OpportunityType.values())));
		properties.put("domain", Map.of("type", "string", "enum", names(Domain.values())));
		properties.put("seniority", Map.of("type", "string", "enum", names(Seniority.values())));
		properties.put("reason", Map.of("type", "string"));
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", List.copyOf(properties.keySet()));
		schema.put("additionalProperties", false);
		return schema;
	}

	/** The answer, if it is valid JSON with known values; an early-career posting needs a real type. */
	Optional<LlmVerdict> parse(String response) {
		ChatResponse chat = mapper.readValue(response, ChatResponse.class);
		if (chat.choices() == null || chat.choices().isEmpty() || chat.choices().getFirst().message() == null) {
			log.warn("LLM response without a message");
			return Optional.empty();
		}
		String content = chat.choices().getFirst().message().content();
		if (content == null || content.isBlank()) {
			return Optional.empty();
		}
		Answer answer = mapper.readValue(content, Answer.class);
		try {
			OpportunityType type = OpportunityType.valueOf(answer.opportunityType());
			if (answer.earlyCareer() == null || (answer.earlyCareer() && type == OpportunityType.OTHER)) {
				return Optional.empty();
			}
			return Optional.of(new LlmVerdict(answer.earlyCareer(), type, Domain.valueOf(answer.domain()),
					Seniority.valueOf(answer.seniority()), answer.reason()));
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			log.warn("LLM answer outside the schema: {}", content);
			return Optional.empty();
		}
	}

	private void pace() throws InterruptedException {
		pacing.lock();
		try {
			long wait = nextRequestNanos - System.nanoTime();
			if (wait > 0) {
				TimeUnit.NANOSECONDS.sleep(wait);
			}
			nextRequestNanos = System.nanoTime() + properties.minInterval().toNanos();
		}
		finally {
			pacing.unlock();
		}
	}

	private static List<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).toList();
	}

	private static JdkClientHttpRequestFactory requestFactory(LlmProperties properties) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
		factory.setReadTimeout(properties.timeout());
		return factory;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(Message message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Answer(Boolean earlyCareer, String opportunityType, String domain, String seniority, String reason) {
	}

}
