package dev.init.ingestion;

import java.util.List;

import dev.init.company.Company;
import dev.init.company.CompanySource;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminIngestionControllerTests {

	private final IngestionOrchestrator orchestrator = mock(IngestionOrchestrator.class);

	private final AdminIngestionController controller = new AdminIngestionController(orchestrator);

	@Test
	void runsAnIngestionAndSummarizesIt() {
		SourceIngestionResult succeeded = SourceIngestionResult.succeeded(source("acme"),
				new IngestionCounts(10, 4, 3, 2, 1));
		SourceIngestionResult failed = SourceIngestionResult.failed(source("globex"),
				"GET https://example.com returned HTTP 503");
		when(orchestrator.runAll()).thenReturn(new IngestionSummary(List.of(succeeded, failed)));

		IngestionRunResponse response = controller.run();

		assertThat(response.succeeded()).isEqualTo(1);
		assertThat(response.failed()).isEqualTo(1);
		assertThat(response.totals()).isEqualTo(new IngestionCounts(10, 4, 3, 2, 1));
		assertThat(response.results()).containsExactly(succeeded, failed);
	}

	@Test
	void reportsARunInProgressAsAConflict() {
		ProblemDetail problem = controller.alreadyRunning(new IngestionAlreadyRunningException());

		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getDetail()).contains("already running");
	}

	private static CompanySource source(String slug) {
		return new CompanySource(new Company(slug, slug, null), SourceType.GREENHOUSE, slug, SourceRegion.GLOBAL);
	}

}
