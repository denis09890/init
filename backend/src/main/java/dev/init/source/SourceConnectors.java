package dev.init.source;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Looks up the connector for a source type. */
@Component
public class SourceConnectors {

	private final Map<SourceType, JobSourceConnector> byType = new EnumMap<>(SourceType.class);

	public SourceConnectors(List<JobSourceConnector> connectors) {
		for (JobSourceConnector connector : connectors) {
			JobSourceConnector previous = byType.putIfAbsent(connector.type(), connector);
			if (previous != null) {
				throw new IllegalStateException("Two connectors for " + connector.type() + ": "
						+ previous.getClass().getSimpleName() + " and " + connector.getClass().getSimpleName());
			}
		}
	}

	public JobSourceConnector get(SourceType type) {
		JobSourceConnector connector = byType.get(type);
		if (connector == null) {
			throw new IllegalArgumentException("No connector registered for " + type);
		}
		return connector;
	}

}
