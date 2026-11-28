package dev.init.classification;

import java.util.List;

import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SkillExtractorTests {

	private final SkillExtractor extractor = new SkillExtractor();

	@Test
	void findsSkillsInTheTitleAndTheDescriptionInVocabularyOrder() {
		List<String> skills = extract("Junior Backend Developer (Java)",
				"You will build REST APIs with Spring Boot, PostgreSQL and Docker on AWS. Fluent English.");

		assertThat(skills).containsExactly("Java", "SQL", "Spring", "REST APIs", "AWS", "Docker", "English");
	}

	@Test
	void keepsSymbolsThatAreFullSkillNames() {
		assertThat(extract("Embedded Developer", "C++ and C# on Node.js, deployed with CI/CD."))
			.containsExactly("C++", "C#", "Node.js", "CI/CD");
	}

	@ParameterizedTest(name = "\"{0}\" has no skill")
	@CsvSource(delimiter = '|', textBlock = """
			Spring Internship 2027              | Join us this spring in Cluj.
			Office Assistant                    | Keep the office tidy and help the team excel at service.
			Internal Auditor                    | Audit the javascripts folder of the internal wiki.
			Go-To-Market Specialist             | Help us go to market faster.
			""")
	void ambiguousWordsAloneAreNotSkills(String title, String description) {
		assertThat(extract(title, description)).isEmpty();
	}

	@Test
	void javaScriptIsNotJava() {
		assertThat(extract("Frontend Developer", "Modern JavaScript and TypeScript.")).containsExactly("JavaScript",
				"TypeScript");
	}

	@Test
	void readsRomanianLanguageRequirements() {
		assertThat(extract("Customer Support Representative", "Cunoștințe de limba germană și Excel; cunoștințe de Excel."))
			.containsExactly("Excel", "German");
	}

	@Test
	void returnsAtMostFifteenSkills() {
		String everything = String.join(" ", "java python javascript typescript c++ c# golang kotlin php rust scala",
				"sql matlab bash spring boot react angular django docker kubernetes");

		assertThat(extract("Engineer", everything)).hasSize(SkillExtractor.MAX_SKILLS);
	}

	@Test
	void anEmptyPostingHasNoSkills() {
		assertThat(extract("", "")).isEmpty();
	}

	private List<String> extract(String title, String description) {
		return extractor.extract(new NormalizedJob("1", title, "https://example.com/1", List.of(), null,
				RemoteType.UNKNOWN, description, null, null, null, null));
	}

}
