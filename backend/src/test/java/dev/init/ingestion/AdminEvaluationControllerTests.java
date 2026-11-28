package dev.init.ingestion;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminEvaluationControllerTests {

	private final EvaluationSampleExporter exporter = mock(EvaluationSampleExporter.class);

	private final AdminEvaluationController controller = new AdminEvaluationController(exporter);

	@Test
	void exportsTheRequestedSample() {
		EvaluationSample sample = new EvaluationSample(Instant.parse("2026-11-28T10:00:00Z"), 10, 2, 8, 5, 0, List.of());
		when(exporter.export(5)).thenReturn(sample);

		assertThat(controller.sample(5)).isSameAs(sample);
	}

	@Test
	void rejectsSampleSizesOutOfRange() {
		assertThatThrownBy(() -> controller.sample(-1)).isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("between 0 and 1000");
		assertThatThrownBy(() -> controller.sample(1001)).isInstanceOf(ResponseStatusException.class);
		verifyNoInteractions(exporter);
	}

}
