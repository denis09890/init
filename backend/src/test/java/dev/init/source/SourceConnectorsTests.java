package dev.init.source;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceConnectorsTests {

	@Test
	void findsConnectorByType() {
		JobSourceConnector greenhouse = new StubConnector(SourceType.GREENHOUSE);
		SourceConnectors connectors = new SourceConnectors(List.of(greenhouse, new StubConnector(SourceType.LEVER)));

		assertThat(connectors.get(SourceType.GREENHOUSE)).isSameAs(greenhouse);
	}

	@Test
	void missingConnectorIsReported() {
		SourceConnectors connectors = new SourceConnectors(List.of(new StubConnector(SourceType.GREENHOUSE)));

		assertThatThrownBy(() -> connectors.get(SourceType.JSON_LD)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("JSON_LD");
	}

	@Test
	void twoConnectorsForOneTypeAreRejected() {
		List<JobSourceConnector> duplicates = List.of(new StubConnector(SourceType.LEVER),
				new StubConnector(SourceType.LEVER));

		assertThatThrownBy(() -> new SourceConnectors(duplicates)).isInstanceOf(IllegalStateException.class);
	}

	private record StubConnector(SourceType type) implements JobSourceConnector {

		@Override
		public List<RawJob> fetch(SourceTarget target) {
			return List.of();
		}

		@Override
		public NormalizedJob normalize(RawJob job) {
			throw new UnsupportedOperationException();
		}

	}

}
