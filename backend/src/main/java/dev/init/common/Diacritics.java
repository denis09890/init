package dev.init.common;

/**
 * Removes the Romanian diacritics, the same way the database does when it indexes postings (the
 * {@code translate} call in the V3 migration). Searching for "timisoara" must find "Timișoara".
 * Both the cedilla (ş, ţ) and the comma-below (ș, ț) forms are covered, as sources use either.
 */
public final class Diacritics {

	/** Characters replaced, upper and lower case; position {@code i} maps to position {@code i} of {@link #TO}. */
	public static final String FROM = "ăâîșşțţĂÂÎȘŞȚŢ";

	public static final String TO = "aaissttAAISSTT";

	private Diacritics() {
	}

	/** @return the text without Romanian diacritics, or {@code null} for {@code null} */
	public static String strip(String text) {
		if (text == null) {
			return null;
		}
		StringBuilder result = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			int index = FROM.indexOf(c);
			result.append(index < 0 ? c : TO.charAt(index));
		}
		return result.toString();
	}

}
