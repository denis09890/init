package dev.init.ingestion;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs an LLM pass by hand ({@code dev} profile only). Does nothing unless the LLM classifier is
 * enabled and has an API key.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/admin/llm")
class AdminLlmController {

	static final int MAX_LIMIT = 100;

	private final LlmReclassifier reclassifier;

	AdminLlmController(LlmReclassifier reclassifier) {
		this.reclassifier = reclassifier;
	}

	@PostMapping("/run")
	Map<String, Integer> run(@RequestParam(defaultValue = "10") int limit) {
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
		}
		return Map.of("classified", reclassifier.run(limit));
	}

}
