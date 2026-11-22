package dev.init.classification;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Text prepared for keyword matching: lower case, Romanian diacritics removed (so "practică" and
 * "practica" match), punctuation turned into spaces. Phrases match whole words only, so "intern"
 * matches "Software Intern" but not "internal" or "international".
 */
final class MatchText {

	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

	private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9+#]+");

	private final String padded;

	private MatchText(String normalized) {
		this.padded = " " + normalized + " ";
	}

	static MatchText of(String... texts) {
		StringBuilder joined = new StringBuilder();
		for (String text : texts) {
			if (text != null) {
				joined.append(' ').append(text);
			}
		}
		return new MatchText(normalize(joined.toString()));
	}

	static String normalize(String text) {
		// cedilla variants (ş ţ) and comma-below variants (ș ț) both decompose to s/t + a combining mark
		String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
		String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
		return NON_ALPHANUMERIC.matcher(withoutMarks).replaceAll(" ").strip();
	}

	boolean contains(String phrase) {
		return padded.contains(" " + normalize(phrase) + " ");
	}

	/** The first phrase of the collection found in the text. */
	Optional<String> findAny(Collection<String> phrases) {
		return phrases.stream().filter(this::contains).findFirst();
	}

	long count(Collection<String> phrases) {
		return phrases.stream().filter(this::contains).count();
	}

	String value() {
		return padded.strip();
	}

	boolean isEmpty() {
		return padded.isBlank();
	}

}
