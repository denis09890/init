package dev.init.classification;

import java.util.List;

import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedClassifierTests {

	private final RuleBasedClassifier classifier = new RuleBasedClassifier();

	@Nested
	class EarlyCareerFromTitle {

		@ParameterizedTest(name = "\"{0}\" -> {1}, {2}")
		@CsvSource(delimiter = '|', textBlock = """
				Software Engineer Intern                 | INTERNSHIP       | INTERN
				Summer Internship - Finance              | INTERNSHIP       | INTERN
				Stagiar Contabilitate                    | INTERNSHIP       | INTERN
				Practică de vară - Marketing             | INTERNSHIP       | INTERN
				Program de practică pentru studenți      | INTERNSHIP       | INTERN
				Working Student Claims Management        | WORKING_STUDENT  | INTERN
				Werkstudent IT Support                   | WORKING_STUDENT  | INTERN
				Graduate Program 2027 - Audit            | GRADUATE_PROGRAM | ENTRY
				Management Trainee                       | GRADUATE_PROGRAM | ENTRY
				Absolvent - Inginer Proiectare           | GRADUATE_PROGRAM | ENTRY
				Java Academy                             | GRADUATE_PROGRAM | ENTRY
				Junior Java Developer                    | JUNIOR           | JUNIOR
				Jr. Data Analyst                         | JUNIOR           | JUNIOR
				Entry Level Accountant                   | JUNIOR           | ENTRY
				Associate Security Analyst               | JUNIOR           | JUNIOR
				Programator începător                    | JUNIOR           | JUNIOR
				Junior Project Manager                   | JUNIOR           | JUNIOR
				""")
		void recognizesEarlyCareerTitles(String title, OpportunityType type, Seniority seniority) {
			Classification result = classifier.classify(job(title).build());

			assertThat(result.earlyCareer()).isTrue();
			assertThat(result.opportunityType()).isEqualTo(type);
			assertThat(result.seniority()).isEqualTo(seniority);
		}

		@Test
		void explainsWhichRuleFired() {
			Classification result = classifier.classify(job("Software Engineer Intern").build());

			assertThat(result.reasons()).contains("INTERNSHIP: title says 'intern'");
		}

		@Test
		void doesNotMistakeInternalOrInternationalForIntern() {
			assertThat(classifier.classify(job("Internal Auditor").build()).earlyCareer()).isFalse();
			assertThat(classifier.classify(job("International Sales Specialist").build()).earlyCareer()).isFalse();
		}

	}

	@Nested
	class Exclusions {

		@ParameterizedTest(name = "\"{0}\" -> {1}")
		@CsvSource(delimiter = '|', textBlock = """
				Senior Software Engineer, Backend (Bucharest) | SENIOR
				Sr. Accountant                                | SENIOR
				Staff Software Engineer, Backend (Iasi)       | SENIOR
				Principal Solutions Engineer                  | SENIOR
				Lead Engineer - AI Agent Voice Experience     | LEAD
				Head of Marketing                             | LEAD
				Director, Enterprise Sales                    | LEAD
				Marketing Manager                             | LEAD
				Șef de tură producție                         | LEAD
				Junior Team Lead                              | LEAD
				""")
		void excludesSeniorAndLeadershipTitles(String title, Seniority seniority) {
			Classification result = classifier.classify(job(title).build());

			assertThat(result.earlyCareer()).isFalse();
			assertThat(result.opportunityType()).isEqualTo(OpportunityType.OTHER);
			assertThat(result.seniority()).isEqualTo(seniority);
			assertThat(result.reasons()).anyMatch(reason -> reason.startsWith("excluded:"));
		}

		@Test
		void juniorTitleAsking3PlusYearsIsExcluded() {
			Classification result = classifier
				.classify(job("Junior Developer").description("Requirements: minimum 3 years of experience with Java.").build());

			assertThat(result.earlyCareer()).isFalse();
			assertThat(result.seniority()).isEqualTo(Seniority.MID);
			assertThat(result.reasons()).contains("excluded: asks for 3+ years of experience");
		}

		@Test
		void romanianExperienceRequirementIsRecognized() {
			Classification result = classifier
				.classify(job("Junior Contabil").description("Experiență de minim 3 ani în contabilitate.").build());

			assertThat(result.earlyCareer()).isFalse();
		}

		@Test
		void upToTwoYearsIsStillEarlyCareer() {
			Classification result = classifier
				.classify(job("Junior Developer").description("1-2 years of experience is a plus.").build());

			assertThat(result.earlyCareer()).isTrue();
		}

		@Test
		void internshipTitleIsNeverExcludedByTheDescription() {
			Classification result = classifier
				.classify(job("Software Engineering Intern").description("Ideally 3+ years of coding experience.").build());

			assertThat(result.earlyCareer()).isTrue();
		}

		@Test
		void managerWithoutJuniorTitleIsExcludedEvenIfTheTextMentionsAnInternship() {
			Classification result = classifier
				.classify(job("Product Manager").description("You will mentor students in this internship.").build());

			assertThat(result.earlyCareer()).isFalse();
			assertThat(result.seniority()).isEqualTo(Seniority.LEAD);
		}

		@Test
		void leadGenerationIsNotALeadershipRole() {
			Classification result = classifier
				.classify(job("Lead Generation Specialist").description("Join our internship program in Cluj.").build());

			assertThat(result.earlyCareer()).isTrue();
			assertThat(result.opportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
		}

		@ParameterizedTest(name = "\"{0}\" -> {1}")
		@CsvSource(delimiter = '|', textBlock = """
				3+ years of experience                     | 3
				3-5 years of relevant experience           | 3
				at least 5 years experience                | 5
				minim 2 ani de experiență                  | 2
				experiență de minim 4 ani                  | 4
				experience of at least 6 years             | 6
				founded in 2026, a team of 40 people       | -1
				""")
		void readsRequiredYearsOfExperience(String description, int expected) {
			assertThat(RuleBasedClassifier.requiredYears(description).orElse(-1)).isEqualTo(expected);
		}

	}

	@Nested
	class WeakerSignals {

		@Test
		void employmentTypeFieldMarksInternships() {
			Classification result = classifier.classify(job("Claims Specialist").employmentType("Intern").build());

			assertThat(result.opportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
			assertThat(result.reasons()).contains("INTERNSHIP: employment type says 'intern'");
		}

		@Test
		void experienceLevelFieldMarksEntryLevel() {
			Classification result = classifier.classify(job("Analyst").experienceLevel("Entry Level").build());

			assertThat(result.opportunityType()).isEqualTo(OpportunityType.JUNIOR);
			assertThat(result.seniority()).isEqualTo(Seniority.ENTRY);
		}

		@Test
		void smartRecruitersScholarshipWithInternshipLevel() {
			Classification result = classifier.classify(job("Scholarship in AI for PhD Students")
				.employmentType("Full-time")
				.experienceLevel("Internship")
				.build());

			assertThat(result.earlyCareer()).isTrue();
			assertThat(result.opportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
		}

		@Test
		void descriptionCanRevealAnInternship() {
			Classification result = classifier
				.classify(job("Software Developer").description("Apply for our summer internship in Cluj.").build());

			assertThat(result.opportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
			assertThat(result.reasons()).contains("INTERNSHIP: description says 'summer internship'");
		}

		@Test
		void romanianDescriptionCanRevealAGraduateRole() {
			Classification result = classifier.classify(
					job("Inginer proiectare").description("Căutăm proaspeți absolvenți pasionați de tehnologie.").build());

			assertThat(result.opportunityType()).isEqualTo(OpportunityType.GRADUATE_PROGRAM);
		}

		@Test
		void neutralPostingIsNotEarlyCareer() {
			Classification result = classifier.classify(job("Software Developer").description("Build APIs.").build());

			assertThat(result.earlyCareer()).isFalse();
			assertThat(result.seniority()).isEqualTo(Seniority.UNKNOWN);
			assertThat(result.reasons()).contains("no early-career signal in title, employment type or description");
		}

	}

	@Nested
	class Domains {

		@ParameterizedTest(name = "\"{0}\" -> {1}")
		@CsvSource(delimiter = '|', textBlock = """
				Software Engineer Intern                  | SOFTWARE
				Machine Learning Engineering Intern       | DATA
				Data Engineer                             | DATA
				Electronics Hardware Design Engineer      | ENGINEERING
				Product Design Intern                     | DESIGN
				Web Designer                              | DESIGN
				Stagiar Contabilitate                     | FINANCE
				Accountant                                | FINANCE
				Junior Recruiter                          | HR
				Paralegal Intern                          | LEGAL
				Supply Chain Trainee                      | OPERATIONS
				Customer Success Manager                  | CUSTOMER_SUPPORT
				Sales Development Representative          | SALES
				Social Media Intern                       | MARKETING
				Dentist                                   | OTHER
				""")
		void domainFromTitle(String title, Domain domain) {
			assertThat(classifier.classify(job(title).build()).domain()).isEqualTo(domain);
		}

		@Test
		void departmentAddsToTheTitle() {
			Classification result = classifier
				.classify(job("Working Student Claims Management").department("Administrative").build());

			assertThat(result.domain()).isEqualTo(Domain.OPERATIONS);
		}

		@Test
		void departmentAloneDecidesForNeutralTitles() {
			assertThat(classifier.classify(job("Intern").department("Finance").build()).domain()).isEqualTo(Domain.FINANCE);
		}

		@Test
		void descriptionIsTheFallback() {
			Classification result = classifier
				.classify(job("Intern").description("You will help our accounting team with invoices.").build());

			assertThat(result.domain()).isEqualTo(Domain.FINANCE);
			assertThat(result.reasons()).contains("domain FINANCE from the description");
		}

	}

	@Nested
	class Locations {

		@Test
		void romanianCountryCode() {
			assertThat(classify(List.of("Blaj"), "RO", RemoteType.HYBRID)).isTrue();
		}

		@ParameterizedTest(name = "\"{0}\"")
		@CsvSource(delimiter = '|', textBlock = """
				Bucharest, Romania (Hybrid)
				Cluj, Romania (Hybrid)
				Iasi, Romania (Hybrid)
				Bucharest, RO
				București
				Timișoara, TM, Romania
				""")
		void romanianPlaces(String location) {
			assertThat(classify(List.of(location), null, RemoteType.UNKNOWN)).isTrue();
		}

		@Test
		void foreignOnsiteRoleIsNotEligible() {
			assertThat(classify(List.of("Toronto Canada"), null, RemoteType.UNKNOWN)).isFalse();
			assertThat(classify(List.of("Atlanta, Georgia"), "US", RemoteType.HYBRID)).isFalse();
		}

		@Test
		void remoteRoleMustBeOpenToRomania() {
			assertThat(classify(List.of("United States (Remote)"), null, RemoteType.REMOTE)).isFalse();
			assertThat(classify(List.of("Remote - Europe"), null, RemoteType.REMOTE)).isTrue();
			assertThat(classify(List.of(), null, RemoteType.REMOTE)).isTrue();
		}

		@Test
		void unknownLocationIsKept() {
			assertThat(classify(List.of(), null, RemoteType.UNKNOWN)).isTrue();
		}

		private boolean classify(List<String> locations, String countryCode, RemoteType remoteType) {
			return classifier.classify(job("Intern").locations(locations).country(countryCode).remote(remoteType).build())
				.locationEligible();
		}

	}

	@Nested
	class LanguageAndPay {

		@Test
		void detectsEnglish() {
			assertThat(classifier
				.classify(job("Intern").description("We are looking for an intern to join our team and grow your skills.")
					.build())
				.language()).isEqualTo("en");
		}

		@Test
		void detectsRomanian() {
			assertThat(classifier
				.classify(job("Stagiar").description("Căutăm un stagiar pentru echipa noastră și oferim mentorat.").build())
				.language()).isEqualTo("ro");
		}

		@Test
		void languageIsUnknownForTooLittleText() {
			assertThat(classifier.classify(job("Intern").build()).language()).isNull();
		}

		@ParameterizedTest(name = "\"{0}\" -> {1}")
		@CsvSource(delimiter = '|', nullValues = "null", textBlock = """
				This is a paid internship.           | true
				Bursă lunară de 2000 RON.             | true
				Unpaid internship for students.      | false
				Stagiu neremunerat, 3 luni.          | false
				Learn from great mentors.            | null
				""")
		void readsPayFromTheDescription(String description, Boolean paid) {
			assertThat(classifier.classify(job("Intern").description(description).build()).paid()).isEqualTo(paid);
		}

	}

	private static JobBuilder job(String title) {
		return new JobBuilder(title);
	}

	private static final class JobBuilder {

		private final String title;

		private String description = "";

		private List<String> locations = List.of("Bucharest, Romania");

		private String countryCode;

		private RemoteType remoteType = RemoteType.UNKNOWN;

		private String department;

		private String employmentType;

		private String experienceLevel;

		JobBuilder(String title) {
			this.title = title;
		}

		JobBuilder description(String description) {
			this.description = description;
			return this;
		}

		JobBuilder locations(List<String> locations) {
			this.locations = locations;
			return this;
		}

		JobBuilder country(String countryCode) {
			this.countryCode = countryCode;
			return this;
		}

		JobBuilder remote(RemoteType remoteType) {
			this.remoteType = remoteType;
			return this;
		}

		JobBuilder department(String department) {
			this.department = department;
			return this;
		}

		JobBuilder employmentType(String employmentType) {
			this.employmentType = employmentType;
			return this;
		}

		JobBuilder experienceLevel(String experienceLevel) {
			this.experienceLevel = experienceLevel;
			return this;
		}

		NormalizedJob build() {
			return new NormalizedJob("1", title, "https://example.com/jobs/1", locations, countryCode, remoteType,
					description, null, department, employmentType, experienceLevel);
		}

	}

}
