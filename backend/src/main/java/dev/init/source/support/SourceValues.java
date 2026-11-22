package dev.init.source.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import dev.init.source.SourceFetchException;

/** Small parsing helpers shared by connectors. */
public final class SourceValues {

	private SourceValues() {
	}

	/** Trimmed text, or {@code null} when the input is null or blank. */
	public static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** Trimmed text of a required field; a missing or blank value means the payload is unusable. */
	public static String requireText(String value, String field, String source) {
		String text = trimToNull(value);
		if (text == null) {
			throw new SourceFetchException(source + " posting without " + field);
		}
		return text;
	}

	/**
	 * Parses ISO-8601 timestamps with an offset ("2026-10-19T14:22:44-04:00", "2026-11-20T06:49:50.059Z")
	 * or plain dates ("2026-10-01", taken as midnight UTC). Unparseable or blank input gives {@code null}.
	 */
	public static Instant parseInstant(String value) {
		String text = trimToNull(value);
		if (text == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		}
		catch (DateTimeParseException notADateTime) {
			try {
				return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
			}
			catch (DateTimeParseException notADate) {
				return null;
			}
		}
	}

	/** Splits "A; B; C" into trimmed, non-empty parts. */
	public static List<String> splitList(String value, String separatorRegex) {
		if (value == null) {
			return List.of();
		}
		return Arrays.stream(value.split(separatorRegex))
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.toList();
	}

}
