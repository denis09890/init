package dev.init.classification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyword lists of the rule-based classifier, in English and Romanian. Written without diacritics:
 * matching removes them from the text first (see {@link MatchText}). Order matters where lists are
 * checked one after another: the first list that matches wins.
 */
final class ClassificationKeywords {

	private ClassificationKeywords() {
	}

	// ----- Opportunity type, from the title (strongest signal) ---------------------------------

	static final List<String> INTERNSHIP = List.of("intern", "interns", "internship", "internships", "stagiar",
			"stagiara", "stagiari", "stagiu", "practica", "practicant", "praktikant", "praktikum", "apprentice",
			"apprenticeship", "ucenic");

	static final List<String> WORKING_STUDENT = List.of("working student", "werkstudent", "student job",
			"student worker", "student assistant", "part time student", "student");

	static final List<String> GRADUATE_PROGRAM = List.of("graduate", "graduates", "graduate program",
			"graduate programme", "trainee", "traineeship", "management trainee", "new grad", "early career",
			"early careers", "absolvent", "absolventa", "absolventi", "academy", "rotational program");

	static final List<String> JUNIOR = List.of("junior", "jr", "entry level", "beginner", "incepator", "debutant",
			"associate", "fara experienta");

	// ----- Opportunity type, from employment type / experience level fields ---------------------

	static final List<String> INTERNSHIP_HINTS = List.of("intern", "internship", "apprentice", "apprenticeship");

	static final List<String> WORKING_STUDENT_HINTS = List.of("working student", "werkstudent", "student");

	static final List<String> GRADUATE_HINTS = List.of("trainee", "graduate", "graduate program");

	static final List<String> JUNIOR_HINTS = List.of("entry level", "junior");

	// ----- Opportunity type, from the description (weakest signal) ------------------------------

	static final List<String> INTERNSHIP_DESCRIPTION = List.of("this internship", "our internship",
			"internship program", "internship programme", "summer internship", "paid internship",
			"stagiu de practica", "program de internship", "programul de internship", "practica de vara");

	static final List<String> WORKING_STUDENT_DESCRIPTION = List.of("working student", "part time student",
			"currently enrolled", "student in anul", "esti student");

	static final List<String> GRADUATE_DESCRIPTION = List.of("graduate program", "graduate programme",
			"recent graduates", "recent graduate", "fresh graduates", "fresh graduate", "proaspat absolvent",
			"proaspat absolventi", "proaspeti absolventi");

	static final List<String> JUNIOR_DESCRIPTION = List.of("entry level", "no prior experience",
			"no previous experience", "no experience required", "fara experienta", "nu este necesara experienta",
			"nu necesita experienta");

	// ----- Exclusions -----------------------------------------------------------------------------

	/** Never early career, whatever else the title says (except internship/student/graduate titles). */
	static final List<String> SENIOR_TITLES = List.of("senior", "sr", "snr", "principal", "staff engineer",
			"staff software", "staff machine", "staff data", "staff product", "expert");

	static final List<String> LEAD_TITLES = List.of("lead", "team lead", "team leader", "tech lead", "head of",
			"director", "vp", "vice president", "chief", "cto", "cfo", "ceo", "coo", "sef");

	/** Management titles: excluded unless the title itself says junior, trainee, intern, ... */
	static final List<String> MANAGER_TITLES = List.of("manager", "supervisor", "coordonator departament");

	/** Phrases that contain an exclusion word without meaning seniority. */
	static final List<String> EXCLUSION_FALSE_POSITIVES = List.of("lead generation", "leads generation");

	// ----- Domain ----------------------------------------------------------------------------------

	static final Map<Domain, List<String>> DOMAINS = domains();

	private static Map<Domain, List<String>> domains() {
		Map<Domain, List<String>> domains = new LinkedHashMap<>();
		domains.put(Domain.SOFTWARE, List.of("software", "developer", "developers", "programmer", "programator",
				"dezvoltator", "frontend", "front end", "backend", "back end", "full stack", "fullstack", "devops",
				"sre", "site reliability", "qa", "quality assurance", "tester", "test automation", "java", "python",
				"javascript", "typescript", "c++", "c#", "react", "angular", "android", "ios", "mobile", "web",
				"cloud", "cybersecurity", "security", "soc", "penetration", "embedded", "firmware", "sysadmin",
				"system administrator", "network engineer", "it support", "it specialist", "it administrator",
				"it operations", "it engineer", "it consultant", "it security", "sap", "s4hana", "4hana",
				"information technology", "production it", "platform engineer"));
		domains.put(Domain.DATA, List.of("data", "analytics", "data analyst", "data scientist", "data science",
				"data engineer", "machine learning", "ml", "ai", "artificial intelligence", "business intelligence",
				"bi", "big data", "statistics", "statistician", "reporting specialist", "digitalization",
				"digitalisation", "digitalizare"));
		domains.put(Domain.ENGINEERING, List.of("mechanical", "electrical", "electronics", "electronic", "hardware",
				"civil", "automotive", "manufacturing", "production", "process engineer", "quality engineer",
				"mechatronics", "mecanic", "mecanica", "electric", "electronica", "constructii", "construction",
				"structural", "hvac", "chemical", "aerospace", "industrial", "maintenance", "mentenanta"));
		domains.put(Domain.DESIGN, List.of("designer", "web designer", "ux", "ui", "user experience", "user interface",
				"graphic design", "product design", "visual design", "interaction design", "motion design",
				"illustrator"));
		domains.put(Domain.PRODUCT, List.of("product manager", "product owner", "product management",
				"product analyst", "product specialist"));
		domains.put(Domain.FINANCE, List.of("finance", "financial", "accounting", "accountant", "accounts payable",
				"accounts receivable", "contabil", "contabilitate", "audit", "auditor", "tax", "taxation",
				"treasury", "controlling", "controller", "banking", "credit", "risk", "actuarial", "economist",
				"investment", "fiscal", "payroll"));
		domains.put(Domain.MARKETING, List.of("marketing", "marketer", "brand", "branding", "social media",
				"content", "copywriter", "communication", "communications", "pr", "public relations", "seo", "sem"));
		domains.put(Domain.SALES, List.of("sales", "account executive", "business development", "bdr", "sdr",
				"sales development", "account manager", "vanzari", "vanzator", "key account"));
		domains.put(Domain.HR, List.of("hr", "human resources", "recruiter", "recruitment", "recrutare",
				"talent acquisition", "people operations", "resurse umane", "learning and development",
				"training specialist", "trainer"));
		domains.put(Domain.LEGAL, List.of("legal", "lawyer", "counsel", "jurist", "avocat", "paralegal",
				"compliance", "juridic", "notar"));
		domains.put(Domain.OPERATIONS, List.of("operations", "supply chain", "logistics", "logistica",
				"procurement", "purchasing", "achizitii", "buyer", "warehouse", "depozit", "planner", "planning",
				"inventory", "claims", "project management", "project manager", "project coordinator", "pmo",
				"program manager", "programme manager", "administrative", "administrativ", "back office", "facility",
				"facilities", "transport", "cleaning"));
		domains.put(Domain.CUSTOMER_SUPPORT, List.of("customer support", "customer service", "customer care",
				"technical support", "support specialist", "support engineer", "call center", "help desk",
				"helpdesk", "customer success", "client service", "relatii clienti"));
		domains.put(Domain.RESEARCH, List.of("research", "researcher", "phd", "doctoral", "scientist", "cercetare",
				"cercetator", "laboratory"));
		// LinkedHashMap keeps the declaration order, which breaks score ties.
		return Collections.unmodifiableMap(domains);
	}

	// ----- Location ----------------------------------------------------------------------------------

	static final List<String> ROMANIAN_PLACES = List.of("romania", "ro", "bucharest", "bucuresti", "cluj",
			"cluj napoca", "iasi", "timisoara", "brasov", "constanta", "sibiu", "oradea", "craiova", "targu mures",
			"galati", "ploiesti", "arad", "pitesti", "bacau", "suceava", "baia mare", "satu mare", "alba iulia",
			"blaj", "jucu", "buzau", "botosani", "deva", "hunedoara", "targoviste", "ramnicu valcea",
			"piatra neamt", "focsani", "bistrita", "tulcea", "resita", "slatina", "zalau", "miercurea ciuc",
			"sfantu gheorghe", "otopeni", "voluntari", "mioveni", "medias", "turda", "sighisoara");

	/** Regions a remote role can be open to that include Romania. */
	static final List<String> REMOTE_REGIONS_INCLUDING_ROMANIA = List.of("europe", "emea", "eu", "european union",
			"eastern europe", "cee", "anywhere", "worldwide", "global");

	// ----- Language and pay -----------------------------------------------------------------------

	static final List<String> ROMANIAN_MARKERS = List.of("si", "pentru", "este", "sunt", "cu", "din", "care",
			"experienta", "cunostinte", "echipa", "oferim", "cerinte", "responsabilitati", "limba", "candidatul");

	static final List<String> ENGLISH_MARKERS = List.of("the", "and", "for", "with", "you", "we", "our", "are",
			"will", "experience", "team", "skills", "requirements", "responsibilities");

	static final List<String> UNPAID = List.of("unpaid", "neplatit", "neplatita", "neremunerat", "neremunerata",
			"fara plata", "volunteer", "voluntariat");

	static final List<String> PAID = List.of("paid", "stipend", "salary", "salariu", "remunerat", "remunerata",
			"platit", "platita", "bursa", "compensation", "ron", "lei");

}
