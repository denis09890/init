package dev.init.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exports a sample of stored postings for labelling by hand (see {@code docs/EVALUATION.md}). Only
 * exists in the {@code dev} profile: it is unauthenticated and returns excerpts of every sampled
 * posting.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/admin/evaluation")
class AdminEvaluationController {

	static final int MAX_NEGATIVES = 1000;

	private final EvaluationSampleExporter exporter;

	AdminEvaluationController(EvaluationSampleExporter exporter) {
		this.exporter = exporter;
	}

	@GetMapping("/sample")
	EvaluationSample sample(@RequestParam(defaultValue = "80") int negatives) {
		if (negatives < 0 || negatives > MAX_NEGATIVES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"negatives must be between 0 and " + MAX_NEGATIVES);
		}
		return exporter.export(negatives);
	}

}
