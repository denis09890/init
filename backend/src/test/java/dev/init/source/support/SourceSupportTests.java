package dev.init.source.support;

import java.time.Instant;

import dev.init.source.RemoteType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SourceSupportTests {

	@Test
	void htmlBecomesSingleLinePlainText() {
		assertThat(HtmlText.toPlainText("<p>Hello&nbsp;<b>world</b></p>\n<ul><li>One</li><li>Two</li></ul>"))
			.isEqualTo("Hello world One Two");
		assertThat(HtmlText.toPlainText(null)).isEmpty();
		assertThat(HtmlText.toPlainText("   ")).isEmpty();
	}

	@Test
	void escapedHtmlIsUnescapedBeforeStrippingTags() {
		assertThat(HtmlText.fromEscapedHtml("&lt;p&gt;Stagiu de practic&amp;#259;&lt;/p&gt;"))
			.isEqualTo("Stagiu de practică");
	}

	@ParameterizedTest(name = "\"{0}\" -> {1}")
	@CsvSource(delimiter = '|', textBlock = """
			Bucharest, Romania (Hybrid)     | HYBRID
			Berlin, Germany (Hybird)        | HYBRID
			United States (Remote)          | REMOTE
			Remote - Romania                | REMOTE
			Cluj-Napoca (On-site)           | ONSITE
			Toronto Canada                  | UNKNOWN
			Bucharest, Romania              | UNKNOWN
			""")
	void workplaceTypeFromLocationText(String text, RemoteType expected) {
		assertThat(WorkplaceHints.fromText(text)).isEqualTo(expected);
	}

	@Test
	void workplaceTypeIgnoresMissingTexts() {
		assertThat(WorkplaceHints.fromText(null, null)).isEqualTo(RemoteType.UNKNOWN);
		assertThat(WorkplaceHints.fromText(null, "Hybrid")).isEqualTo(RemoteType.HYBRID);
	}

	@Test
	void parsesTimestampsWithOffsetsAndPlainDates() {
		assertThat(SourceValues.parseInstant("2026-10-19T14:22:44-04:00")).isEqualTo(Instant.parse("2026-10-19T18:22:44Z"));
		assertThat(SourceValues.parseInstant("2026-11-20T06:49:50.059Z")).isEqualTo(Instant.parse("2026-11-20T06:49:50.059Z"));
		assertThat(SourceValues.parseInstant("2026-11-02")).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
		assertThat(SourceValues.parseInstant("next week")).isNull();
		assertThat(SourceValues.parseInstant(null)).isNull();
	}

	@Test
	void splitsAndTrimsLists() {
		assertThat(SourceValues.splitList(" Cluj ; ;Iasi ", ";")).containsExactly("Cluj", "Iasi");
		assertThat(SourceValues.splitList(null, ";")).isEmpty();
	}

}
