package dev.init.source.support;

import dev.init.source.SourceFetchException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** JSON helpers that turn parsing problems into {@link SourceFetchException}s naming the source. */
public final class SourceJson {

	private SourceJson() {
	}

	public static JsonNode readTree(ObjectMapper mapper, String json, String source) {
		try {
			return mapper.readTree(json);
		}
		catch (JacksonException ex) {
			throw new SourceFetchException("Malformed JSON from " + source, ex);
		}
	}

	public static <T> T read(ObjectMapper mapper, String json, Class<T> type, String source) {
		try {
			return mapper.readValue(json, type);
		}
		catch (JacksonException ex) {
			throw new SourceFetchException("Unexpected " + type.getSimpleName() + " payload from " + source, ex);
		}
	}

	public static <T> T convert(ObjectMapper mapper, JsonNode node, Class<T> type, String source) {
		try {
			return mapper.treeToValue(node, type);
		}
		catch (JacksonException ex) {
			throw new SourceFetchException("Unexpected " + type.getSimpleName() + " payload from " + source, ex);
		}
	}

	public static String write(ObjectMapper mapper, Object value) {
		return mapper.writeValueAsString(value);
	}

}
