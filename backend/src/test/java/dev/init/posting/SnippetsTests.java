package dev.init.posting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetsTests {

	@Test
	void blankOrMissingDescriptionHasNoSnippet() {
		assertThat(Snippets.of(null)).isNull();
		assertThat(Snippets.of("")).isNull();
		assertThat(Snippets.of("   ")).isNull();
	}

	@Test
	void shortDescriptionIsKeptWhole() {
		assertThat(Snippets.of("  Build APIs.  ")).isEqualTo("Build APIs.");
	}

	@Test
	void descriptionOfExactlyTheLimitIsKept() {
		String text = "a".repeat(Posting.SNIPPET_MAX_LENGTH);

		assertThat(Snippets.of(text)).isEqualTo(text);
	}

	@Test
	void longDescriptionIsCutAtAWordBoundaryAndEndsWithAnEllipsis() {
		String text = "word ".repeat(100).strip();

		String snippet = Snippets.of(text);

		assertThat(snippet).hasSizeLessThanOrEqualTo(Posting.SNIPPET_MAX_LENGTH).endsWith("word…");
		assertThat(text).startsWith(snippet.substring(0, snippet.length() - 1));
	}

	@Test
	void textWithoutSpacesIsCutHard() {
		String snippet = Snippets.of("a".repeat(500));

		assertThat(snippet).hasSize(Posting.SNIPPET_MAX_LENGTH).endsWith("a…");
	}

	@Test
	void neverCutsInTheMiddleOfASurrogatePair() {
		String snippet = Snippets.of("😀".repeat(400));

		assertThat(snippet.codePoints().count()).isEqualTo(150);
		assertThat(Character.isLowSurrogate(snippet.charAt(snippet.length() - 2))).isTrue();
	}

}
