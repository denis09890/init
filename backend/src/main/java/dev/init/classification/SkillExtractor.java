package dev.init.classification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.init.source.NormalizedJob;
import org.springframework.stereotype.Component;

/**
 * The skills a posting mentions, from a fixed vocabulary: programming languages, frameworks, tools,
 * platforms and spoken languages. Each skill has a display name and the phrases that mean it, matched
 * like the classifier's keywords: whole words, ignoring case and diacritics (see {@link MatchText}).
 * Ambiguous words are only matched in phrases that cannot mean something else: "spring boot" rather
 * than "spring", "golang" rather than "go", "advanced excel" rather than "excel".
 * <p>
 * Reads the title and the full description, so it runs at ingestion time, where the description is
 * available. The result is in vocabulary order and at most {@value #MAX_SKILLS} long.
 */
@Component
public class SkillExtractor {

	static final int MAX_SKILLS = 15;

	static final Map<String, List<String>> SKILLS = skills();

	public List<String> extract(NormalizedJob job) {
		MatchText text = MatchText.of(job.title(), job.description());
		if (text.isEmpty()) {
			return List.of();
		}
		return SKILLS.entrySet()
			.stream()
			.filter(skill -> text.findAny(skill.getValue()).isPresent())
			.map(Map.Entry::getKey)
			.limit(MAX_SKILLS)
			.toList();
	}

	private static Map<String, List<String>> skills() {
		Map<String, List<String>> skills = new LinkedHashMap<>();
		// programming languages
		skills.put("Java", List.of("java"));
		skills.put("Python", List.of("python"));
		skills.put("JavaScript", List.of("javascript"));
		skills.put("TypeScript", List.of("typescript"));
		skills.put("C++", List.of("c++"));
		skills.put("C#", List.of("c#"));
		skills.put("C", List.of("c programming", "embedded c", "ansi c", "c language", "c c++"));
		skills.put("Go", List.of("golang", "go developer", "go programming"));
		skills.put("Kotlin", List.of("kotlin"));
		skills.put("Swift", List.of("swift", "swiftui"));
		skills.put("PHP", List.of("php"));
		skills.put("Rust", List.of("rust"));
		skills.put("Scala", List.of("scala"));
		skills.put("SQL", List.of("sql", "mysql", "postgresql", "t-sql", "pl/sql"));
		skills.put("MATLAB", List.of("matlab", "simulink"));
		skills.put("Bash", List.of("bash", "shell scripting"));
		// frameworks and libraries
		skills.put("Spring", List.of("spring boot", "spring framework"));
		skills.put(".NET", List.of("dotnet", "asp.net", ".net core", ".net framework", ".net developer"));
		skills.put("React", List.of("react", "reactjs", "react native"));
		skills.put("Angular", List.of("angular"));
		skills.put("Vue.js", List.of("vue", "vue.js", "vuejs"));
		skills.put("Node.js", List.of("node.js", "nodejs"));
		skills.put("Django", List.of("django"));
		skills.put("REST APIs", List.of("rest api", "rest apis", "restful"));
		skills.put("Microservices", List.of("microservices", "microservice"));
		// data and AI
		skills.put("Machine learning", List.of("machine learning", "deep learning", "neural networks"));
		skills.put("TensorFlow", List.of("tensorflow"));
		skills.put("PyTorch", List.of("pytorch"));
		skills.put("Pandas", List.of("pandas"));
		skills.put("Spark", List.of("apache spark", "pyspark", "spark"));
		skills.put("Snowflake", List.of("snowflake"));
		skills.put("Databricks", List.of("databricks"));
		skills.put("Power BI", List.of("power bi", "powerbi"));
		skills.put("Tableau", List.of("tableau"));
		skills.put("ETL", List.of("etl", "elt"));
		// cloud and tooling
		skills.put("AWS", List.of("aws", "amazon web services"));
		skills.put("Azure", List.of("azure"));
		skills.put("Google Cloud", List.of("gcp", "google cloud"));
		skills.put("Docker", List.of("docker"));
		skills.put("Kubernetes", List.of("kubernetes", "k8s"));
		skills.put("Terraform", List.of("terraform"));
		skills.put("Linux", List.of("linux"));
		skills.put("Git", List.of("git", "github", "gitlab"));
		skills.put("CI/CD", List.of("ci/cd", "continuous integration"));
		skills.put("Jenkins", List.of("jenkins"));
		skills.put("Selenium", List.of("selenium"));
		skills.put("Jira", List.of("jira"));
		// business software
		skills.put("SAP", List.of("sap", "s/4hana", "s4hana"));
		skills.put("Salesforce", List.of("salesforce"));
		skills.put("Excel", List.of("ms excel", "microsoft excel", "advanced excel", "excel skills", "excel vba",
				"knowledge of excel", "proficient in excel", "cunostinte excel", "cunostinte de excel"));
		skills.put("Microsoft Office", List.of("microsoft office", "ms office", "office 365", "pachetul office"));
		// engineering and design tools
		skills.put("AutoCAD", List.of("autocad"));
		skills.put("CATIA", List.of("catia"));
		skills.put("SolidWorks", List.of("solidworks"));
		skills.put("PLC", List.of("plc", "plcs"));
		skills.put("AUTOSAR", List.of("autosar"));
		skills.put("LabVIEW", List.of("labview"));
		skills.put("Figma", List.of("figma"));
		skills.put("Photoshop", List.of("photoshop"));
		// marketing
		skills.put("SEO", List.of("seo"));
		skills.put("Google Analytics", List.of("google analytics"));
		// ways of working
		skills.put("Agile", List.of("agile", "scrum", "kanban"));
		// spoken languages (English and Romanian names)
		skills.put("English", List.of("english", "limba engleza"));
		skills.put("German", List.of("german", "limba germana"));
		skills.put("French", List.of("french", "limba franceza"));
		skills.put("Italian", List.of("italian", "limba italiana"));
		skills.put("Spanish", List.of("spanish", "limba spaniola"));
		skills.put("Dutch", List.of("dutch", "limba olandeza"));
		skills.put("Hungarian", List.of("hungarian", "limba maghiara"));
		skills.put("Romanian", List.of("romanian", "limba romana"));
		return Collections.unmodifiableMap(skills);
	}

}
