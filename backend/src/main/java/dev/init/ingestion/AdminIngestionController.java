package dev.init.ingestion;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts an ingestion by hand. Only exists in the {@code dev} profile: it is unauthenticated and
 * the run can take a while (the request returns when every source is done).
 */
@RestController
@Profile("dev")
@RequestMapping("/api/admin/ingestion")
class AdminIngestionController {

	private final IngestionOrchestrator orchestrator;

	AdminIngestionController(IngestionOrchestrator orchestrator) {
		this.orchestrator = orchestrator;
	}

	@PostMapping("/run")
	IngestionRunResponse run() {
		return IngestionRunResponse.from(orchestrator.runAll());
	}

	@ExceptionHandler(IngestionAlreadyRunningException.class)
	ProblemDetail alreadyRunning(IngestionAlreadyRunningException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

}
