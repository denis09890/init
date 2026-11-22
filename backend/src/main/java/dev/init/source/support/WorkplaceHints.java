package dev.init.source.support;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.init.source.RemoteType;

/**
 * Guesses the workplace type from short texts such as location names ("Bucharest, Romania (Hybrid)")
 * for sources without a dedicated field. Hybrid wins over remote when a posting lists both, since the
 * role then still involves an office.
 */
public final class WorkplaceHints {

	private WorkplaceHints() {
	}

	public static RemoteType fromText(String... texts) {
		String text = Stream.of(texts)
			.filter(Objects::nonNull)
			.map(value -> value.toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(" "));
		// "hybird" is a real typo seen in published postings
		if (text.contains("hybrid") || text.contains("hybird")) {
			return RemoteType.HYBRID;
		}
		if (text.contains("remote") || text.contains("la distanta") || text.contains("la distanță")) {
			return RemoteType.REMOTE;
		}
		if (text.contains("on-site") || text.contains("onsite") || text.contains("on site")) {
			return RemoteType.ONSITE;
		}
		return RemoteType.UNKNOWN;
	}

}
