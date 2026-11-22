package dev.init.source.support;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

/** Turns HTML descriptions into a single line of plain text. */
public final class HtmlText {

	private HtmlText() {
	}

	/** Plain text of an HTML fragment; {@code null} or blank input gives an empty string. */
	public static String toPlainText(String html) {
		if (html == null || html.isBlank()) {
			return "";
		}
		return normalizeWhitespace(Jsoup.parse(html).text());
	}

	/**
	 * Plain text of HTML that was entity-escaped once more on top (Greenhouse sends
	 * {@code &lt;p&gt;} instead of {@code <p>}).
	 */
	public static String fromEscapedHtml(String escapedHtml) {
		if (escapedHtml == null || escapedHtml.isBlank()) {
			return "";
		}
		return toPlainText(Parser.unescapeEntities(escapedHtml, false));
	}

	private static String normalizeWhitespace(String text) {
		return text.replace(' ', ' ').replaceAll("\\s+", " ").strip();
	}

}
