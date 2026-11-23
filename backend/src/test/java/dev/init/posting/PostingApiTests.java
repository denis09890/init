package dev.init.posting;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.init.IntegrationTest;
import dev.init.classification.Classification;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.company.Company;
import dev.init.company.CompanyRepository;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.source.RemoteType;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The REST API over HTTP, against the real application and database. */
@IntegrationTest
@Transactional
class PostingApiTests {

	private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private PostingRepository postings;

	@Autowired
	private EntityManager entityManager;

	private Posting softwareIntern;

	@BeforeEach
	void setUp() {
		CompanySource acme = source("Acme", "acme");
		CompanySource globex = source("Globex", "globex");
		softwareIntern = save(acme, "p1", "Software Intern", "Join our summer internship.", List.of("Bucharest"),
				T.plus(1, ChronoUnit.HOURS), classification(true, OpportunityType.INTERNSHIP, Domain.SOFTWARE));
		save(acme, "p2", "Accounting Trainee", null, List.of("Cluj-Napoca"), T,
				classification(true, OpportunityType.GRADUATE_PROGRAM, Domain.FINANCE));
		save(globex, "p3", "Senior Engineer", null, List.of("Bucharest"), T,
				classification(false, OpportunityType.OTHER, Domain.SOFTWARE));
	}

	// ----- GET /api/postings --------------------------------------------------------------------

	@Test
	void listsTheDefaultFeedNewestFirst() throws Exception {
		mockMvc.perform(get("/api/postings"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.items[0].title").value("Software Intern"))
			.andExpect(jsonPath("$.items[0].company.slug").value("acme"))
			.andExpect(jsonPath("$.items[0].opportunityType").value("INTERNSHIP"))
			.andExpect(jsonPath("$.items[0].locations[0]").value("Bucharest"))
			.andExpect(jsonPath("$.items[1].title").value("Accounting Trainee"));
	}

	@Test
	void appliesFiltersFromTheQueryString() throws Exception {
		mockMvc.perform(get("/api/postings").param("domain", "FINANCE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Accounting Trainee"));
		mockMvc.perform(get("/api/postings").param("type", "INTERNSHIP"))
			.andExpect(jsonPath("$.items[0].title").value("Software Intern"))
			.andExpect(jsonPath("$.totalItems").value(1));
		mockMvc.perform(get("/api/postings").param("city", "cluj"))
			.andExpect(jsonPath("$.items[0].title").value("Accounting Trainee"));
		mockMvc.perform(get("/api/postings").param("q", "trainee"))
			.andExpect(jsonPath("$.items[0].title").value("Accounting Trainee"))
			.andExpect(jsonPath("$.totalItems").value(1));
		mockMvc.perform(get("/api/postings").param("company", "globex").param("earlyCareer", "false"))
			.andExpect(jsonPath("$.items[0].title").value("Senior Engineer"))
			.andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void acceptsSeveralValuesRepeatedOrSeparatedByCommas() throws Exception {
		mockMvc.perform(get("/api/postings").param("domain", "FINANCE", "SOFTWARE"))
			.andExpect(jsonPath("$.totalItems").value(2));
		mockMvc.perform(get("/api/postings").param("domain", "FINANCE,SOFTWARE"))
			.andExpect(jsonPath("$.totalItems").value(2));
	}

	@Test
	void pagesThroughTheResults() throws Exception {
		mockMvc.perform(get("/api/postings").param("size", "1").param("page", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Accounting Trainee"))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
	}

	@ParameterizedTest(name = "{0}={1} is rejected")
	@CsvSource({ "size, 0", "size, 101", "size, abc", "page, -1", "domain, NOPE", "type, NOPE", "remote, SIDEWAYS" })
	void rejectsInvalidParameters(String name, String value) throws Exception {
		mockMvc.perform(get("/api/postings").param(name, value)).andExpect(status().isBadRequest());
	}

	// ----- GET /api/postings/{id} ---------------------------------------------------------------

	@Test
	void showsOnePostingWithALinkToTheOriginal() throws Exception {
		mockMvc.perform(get("/api/postings/{id}", softwareIntern.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(softwareIntern.getId()))
			.andExpect(jsonPath("$.title").value("Software Intern"))
			.andExpect(jsonPath("$.company.name").value("Acme"))
			.andExpect(jsonPath("$.url").value("https://example.com/jobs/p1"))
			.andExpect(jsonPath("$.snippet").value("Join our summer internship."))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.domain").value("SOFTWARE"))
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.payload").doesNotExist());
	}

	@Test
	void anUnknownPostingIsNotFound() throws Exception {
		mockMvc.perform(get("/api/postings/{id}", 999_999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void aNonNumericIdIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/postings/abc")).andExpect(status().isBadRequest());
	}

	// ----- GET /api/companies -------------------------------------------------------------------

	@Test
	void listsCompaniesWithTheirPostingCounts() throws Exception {
		mockMvc.perform(get("/api/companies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("Acme"))
			.andExpect(jsonPath("$[0].slug").value("acme"))
			.andExpect(jsonPath("$[0].website").value("https://acme.example"))
			.andExpect(jsonPath("$[0].openPostings").value(2))
			.andExpect(jsonPath("$[0].earlyCareerPostings").value(2))
			.andExpect(jsonPath("$[1].slug").value("globex"))
			.andExpect(jsonPath("$[1].openPostings").value(1))
			.andExpect(jsonPath("$[1].earlyCareerPostings").value(0));
	}

	// ----- admin --------------------------------------------------------------------------------

	@Test
	void theManualIngestionEndpointExistsOnlyInTheDevProfile() throws Exception {
		mockMvc.perform(post("/api/admin/ingestion/run")).andExpect(status().isNotFound());
	}

	// ----- helpers ------------------------------------------------------------------------------

	private CompanySource source(String name, String slug) {
		Company company = companies.save(new Company(name, slug, "https://" + slug + ".example"));
		return sources.save(new CompanySource(company, SourceType.GREENHOUSE, slug, SourceRegion.GLOBAL));
	}

	private Posting save(CompanySource source, String externalId, String title, String snippet, List<String> locations,
			Instant postedAt, Classification classification) {
		Posting posting = new Posting(source, externalId, title, "https://example.com/jobs/" + externalId, locations,
				RemoteType.UNKNOWN, snippet, postedAt, "h".repeat(64), T);
		posting.applyClassification(classification);
		postings.save(posting);
		entityManager.flush();
		return posting;
	}

	private static Classification classification(boolean earlyCareer, OpportunityType type, Domain domain) {
		return new Classification(earlyCareer, type, domain, Seniority.INTERN, true, "en", null, List.of());
	}

}
