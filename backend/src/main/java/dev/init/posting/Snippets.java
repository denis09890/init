package dev.init.posting;

/**
 * Builds the short public snippet shown with a posting. Only the first {@value Posting#SNIPPET_MAX_LENGTH}
 * characters of a description are ever kept; the full text stays in the internal raw payload.
 */
public final class Snippets {

	private static final String ELLIPSIS = "…";

	private Snippets() {
	}

	/**
	 * @return the description if it fits, else a prefix cut at a word boundary and ended with an
	 * ellipsis, never longer than {@link Posting#SNIPPET_MAX_LENGTH}; {@code null} for blank input
	 */
	public static String of(String description) {
		if (description == null) {
			return null;
		}
		String text = description.strip();
		if (text.isEmpty()) {
			return null;
		}
		if (text.length() <= Posting.SNIPPET_MAX_LENGTH) {
			return text;
		}
		int limit = Posting.SNIPPET_MAX_LENGTH - ELLIPSIS.length();
		int cut = text.lastIndexOf(' ', limit);
		if (cut < Posting.SNIPPET_MAX_LENGTH / 2) {
			// no sensible word boundary: cut hard, but never in the middle of a surrogate pair
			cut = limit;
			if (Character.isHighSurrogate(text.charAt(cut - 1))) {
				cut--;
			}
		}
		return text.substring(0, cut).stripTrailing() + ELLIPSIS;
	}

}
