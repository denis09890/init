package dev.init.posting;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.init.IntegrationTest;
import dev.init.classification.Classification;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.common.PageResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** The postings query on a real PostgreSQL: filters, full-text search without diacritics, ordering, paging. */
@IntegrationTest
@Transactional
class PostingSearchRepositoryTests {

	private static final Instant T = Instant.parse("2026-11-20T10:00:00Z");

	@Autowired
	private PostingSearchRepository search;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private PostingRepository postings;

	@Autowired
	private EntityManager entityManager;

	private CompanySource acme;

	private CompanySource globex;

	@BeforeEach
	void setUp() {
		acme = source("Acme", "acme");
		globex = source("Globex", "globex");
	}

	// ----- what is shown by default -------------------------------------------------------------

	@Test
	void showsOnlyOpenEarlyCareerPostingsInRomaniaByDefault() {
		posting(acme, "shown", "Software Intern").save();
		posting(acme, "not-early", "Senior Engineer").classification(classification(false, true)).save();
		posting(acme, "abroad", "Intern in Toronto").classification(classification(true, false)).save();
		posting(acme, "closed", "Closed Intern").closed().save();

		assertThat(titles(search.search(PostingSearchRequest.defaults()))).containsExactly("Software Intern");
	}

	@Test
	void canIncludeNonEarlyCareerAndForeignPostingsButNeverClosedOnes() {
		posting(acme, "shown", "Software Intern").save();
		posting(acme, "not-early", "Senior Engineer").classification(classification(false, true)).save();
		posting(acme, "abroad", "Intern in Toronto").classification(classification(true, false)).save();
		posting(acme, "closed", "Closed Intern").closed().save();

		PageResponse<PostingResponse> page = search.search(request().earlyCareer(false).romaniaOnly(false).build());

		assertThat(titles(page)).containsExactlyInAnyOrder("Software Intern", "Senior Engineer", "Intern in Toronto");
	}

	// ----- filters ------------------------------------------------------------------------------

	@Test
	void filtersByDomainAndOpportunityType() {
		posting(acme, "p1", "Software Intern").classification(of(OpportunityType.INTERNSHIP, Domain.SOFTWARE)).save();
		posting(acme, "p2", "Accounting Student")
			.classification(of(OpportunityType.WORKING_STUDENT, Domain.FINANCE))
			.save();
		posting(acme, "p3", "Junior Analyst").classification(of(OpportunityType.JUNIOR, Domain.DATA)).save();

		assertThat(titles(search.search(request().domain(Domain.FINANCE).build()))).containsExactly("Accounting Student");
		assertThat(titles(search.search(request().domain(Domain.SOFTWARE, Domain.DATA).build())))
			.containsExactlyInAnyOrder("Software Intern", "Junior Analyst");
		assertThat(titles(search.search(request().type(OpportunityType.JUNIOR, OpportunityType.WORKING_STUDENT).build())))
			.containsExactlyInAnyOrder("Accounting Student", "Junior Analyst");
		assertThat(titles(search.search(request().domain(Domain.DATA).type(OpportunityType.JUNIOR).build())))
			.containsExactly("Junior Analyst");
		assertThat(search.search(request().domain(Domain.FINANCE).type(OpportunityType.JUNIOR).build()).items())
			.isEmpty();
	}

	@Test
	void filtersByCompany() {
		posting(acme, "p1", "Acme Intern").save();
		posting(globex, "p2", "Globex Intern").save();

		assertThat(titles(search.search(request().company("globex").build()))).containsExactly("Globex Intern");
		assertThat(search.search(request().company("nobody").build()).items()).isEmpty();
	}

	@Test
	void filtersByCityIgnoringCaseAndDiacritics() {
		posting(acme, "p1", "In Timisoara").locations("Timișoara").save();
		posting(acme, "p2", "In Cluj").locations("Jucu, Cluj").save();
		posting(acme, "p3", "In Bucharest").locations("Bucharest, RO").save();
		posting(acme, "p4", "In Brasov and Iasi").locations("Brașov", "Iași").save();

		assertThat(titles(search.search(request().city("timisoara").build()))).containsExactly("In Timisoara");
		assertThat(titles(search.search(request().city("TIMIȘOARA").build()))).containsExactly("In Timisoara");
		assertThat(titles(search.search(request().city("cluj").build()))).containsExactly("In Cluj");
		assertThat(titles(search.search(request().city("iasi").build()))).containsExactly("In Brasov and Iasi");
		assertThat(titles(search.search(request().city("Brasov").build()))).containsExactly("In Brasov and Iasi");
		assertThat(titles(search.search(request().city("bucharest").build()))).containsExactly("In Bucharest");
		assertThat(search.search(request().city("sibiu").build()).items()).isEmpty();
	}

	@Test
	void aCityFilterTakesPercentAndUnderscoreLiterally() {
		posting(acme, "p1", "In Cluj").locations("Cluj-Napoca").save();

		assertThat(search.search(request().city("%").build()).items()).isEmpty();
		assertThat(search.search(request().city("clu_").build()).items()).isEmpty();
	}

	@Test
	void filtersByLanguageAndPay() {
		posting(acme, "p1", "Paid RO").classification(of(OpportunityType.INTERNSHIP, Domain.SOFTWARE, "ro", true)).save();
		posting(acme, "p2", "Unpaid EN").classification(of(OpportunityType.INTERNSHIP, Domain.SOFTWARE, "en", false)).save();
		posting(acme, "p3", "Unknown EN").classification(of(OpportunityType.INTERNSHIP, Domain.SOFTWARE, "en", null)).save();

		assertThat(titles(search.search(request().language("ro").build()))).containsExactly("Paid RO");
		assertThat(titles(search.search(request().language("EN").build())))
			.containsExactlyInAnyOrder("Unpaid EN", "Unknown EN");
		assertThat(titles(search.search(request().paid(true).build()))).containsExactly("Paid RO");
		assertThat(titles(search.search(request().paid(false).build()))).containsExactly("Unpaid EN");
	}

	@Test
	void filtersByWorkplaceType() {
		posting(acme, "p1", "Remote Intern").remoteType(RemoteType.REMOTE).save();
		posting(acme, "p2", "Hybrid Intern").remoteType(RemoteType.HYBRID).save();

		assertThat(titles(search.search(request().remote(RemoteType.REMOTE).build()))).containsExactly("Remote Intern");
	}

	// ----- full-text search ---------------------------------------------------------------------

	@Test
	void fullTextSearchIgnoresDiacriticsAndCase() {
		posting(acme, "p1", "Practică de vară").snippet("Stagiu pentru studenți la Cluj.").save();
		posting(acme, "p2", "Stagiu Ştiinţe").save();
		posting(acme, "p3", "Software Intern").save();

		assertThat(titles(search.search(request().q("practica").build()))).containsExactly("Practică de vară");
		assertThat(titles(search.search(request().q("PRACTICĂ").build()))).containsExactly("Practică de vară");
		assertThat(titles(search.search(request().q("vara").build()))).containsExactly("Practică de vară");
		assertThat(titles(search.search(request().q("studenti").build()))).containsExactly("Practică de vară");
		assertThat(titles(search.search(request().q("cluj").build()))).containsExactly("Practică de vară");
		assertThat(titles(search.search(request().q("stiinte").build()))).as("cedilla forms").containsExactly("Stagiu Ştiinţe");
		assertThat(titles(search.search(request().q("stagiu").build()))).containsExactlyInAnyOrder("Practică de vară",
				"Stagiu Ştiinţe");
		assertThat(titles(search.search(request().q("intern").build()))).containsExactly("Software Intern");
		assertThat(search.search(request().q("marketing").build()).items()).isEmpty();
	}

	@Test
	void theDatabaseAndTheApiRemoveExactlyTheSameDiacritics() {
		// a, a-circumflex, i-circumflex, s and t with comma below and with cedilla, lower and upper case
		String lower = "ăâîșşțţ";
		String upper = "ĂÂÎȘŞȚŢ";
		posting(acme, "p1", lower + " " + upper).save();

		assertThat(search.search(request().q("aaisstt").build()).items()).hasSize(1);
		assertThat(search.search(request().q(lower).build()).items()).hasSize(1);
		assertThat(search.search(request().q(upper).build()).items()).hasSize(1);
	}

	@Test
	void fullTextSearchCombinesWithFilters() {
		posting(acme, "p1", "Java Intern").save();
		posting(globex, "p2", "Java Trainee").save();

		assertThat(titles(search.search(request().q("java").company("acme").build()))).containsExactly("Java Intern");
	}

	@Test
	void aQueryWithoutSearchableWordsMatchesNothingInsteadOfFailing() {
		posting(acme, "p1", "Java Intern").save();

		assertThat(search.search(request().q("!!!").build()).items()).isEmpty();
	}

	@Test
	void fullTextSearchRanksBetterMatchesFirst() {
		posting(acme, "p1", "Java Intern").snippet("Java Java Java").postedAt(T).save();
		posting(acme, "p2", "Python Intern").snippet("We also use java").postedAt(T.plus(1, ChronoUnit.HOURS)).save();

		assertThat(titles(search.search(request().q("java").build()))).containsExactly("Java Intern", "Python Intern");
	}

	// ----- ordering and paging ------------------------------------------------------------------

	@Test
	void ordersNewestFirstWithUndatedPostingsLast() {
		posting(acme, "a", "A").postedAt(T.plus(2, ChronoUnit.HOURS)).save();
		posting(acme, "b", "B").postedAt(T.plus(1, ChronoUnit.HOURS)).save();
		posting(acme, "c", "C").postedAt(null).save();
		posting(acme, "d", "D").postedAt(null).save();

		assertThat(titles(search.search(PostingSearchRequest.defaults()))).containsExactly("A", "B", "D", "C");
	}

	@Test
	void pagesThroughTheResults() {
		for (int i = 0; i < 5; i++) {
			posting(acme, "p" + i, "P" + i).postedAt(T.plus(i, ChronoUnit.HOURS)).save();
		}

		PageResponse<PostingResponse> first = search.search(request().size(2).build());
		PageResponse<PostingResponse> second = search.search(request().size(2).page(1).build());
		PageResponse<PostingResponse> last = search.search(request().size(2).page(2).build());
		PageResponse<PostingResponse> beyond = search.search(request().size(2).page(3).build());

		assertThat(titles(first)).containsExactly("P4", "P3");
		assertThat(titles(second)).containsExactly("P2", "P1");
		assertThat(titles(last)).containsExactly("P0");
		assertThat(beyond.items()).isEmpty();
		assertThat(first.totalItems()).isEqualTo(5);
		assertThat(first.totalPages()).isEqualTo(3);
		assertThat(beyond.totalItems()).isEqualTo(5);
		assertThat(first.page()).isZero();
		assertThat(second.page()).isEqualTo(1);
		assertThat(first.size()).isEqualTo(2);
	}

	@Test
	void pageSizeDefaultsToTwenty() {
		for (int i = 0; i < 25; i++) {
			posting(acme, "p" + i, "P" + i).save();
		}

		PageResponse<PostingResponse> page = search.search(PostingSearchRequest.defaults());

		assertThat(page.items()).hasSize(20);
		assertThat(page.size()).isEqualTo(20);
		assertThat(page.totalItems()).isEqualTo(25);
		assertThat(page.totalPages()).isEqualTo(2);
	}

	// ----- one posting --------------------------------------------------------------------------

	@Test
	void mapsEveryFieldOfAPosting() {
		Posting saved = posting(acme, "p1", "Software Intern").snippet("Join us.")
			.locations("Cluj-Napoca", "Bucharest")
			.remoteType(RemoteType.HYBRID)
			.classification(new Classification(true, OpportunityType.INTERNSHIP, Domain.SOFTWARE, Seniority.INTERN,
					true, "en", true, List.of()))
			.save();

		PostingResponse response = search.findById(saved.getId()).orElseThrow();

		assertThat(response.id()).isEqualTo(saved.getId());
		assertThat(response.title()).isEqualTo("Software Intern");
		assertThat(response.company()).isEqualTo(new PostingResponse.CompanyRef("Acme", "acme"));
		assertThat(response.url()).isEqualTo("https://example.com/jobs/p1");
		assertThat(response.locations()).containsExactly("Cluj-Napoca", "Bucharest");
		assertThat(response.remoteType()).isEqualTo(RemoteType.HYBRID);
		assertThat(response.snippet()).isEqualTo("Join us.");
		assertThat(response.postedAt()).isEqualTo(T);
		assertThat(response.firstSeenAt()).isEqualTo(T);
		assertThat(response.lastSeenAt()).isEqualTo(T);
		assertThat(response.closedAt()).isNull();
		assertThat(response.status()).isEqualTo(PostingStatus.OPEN);
		assertThat(response.earlyCareer()).isTrue();
		assertThat(response.locationEligible()).isTrue();
		assertThat(response.opportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
		assertThat(response.domain()).isEqualTo(Domain.SOFTWARE);
		assertThat(response.seniority()).isEqualTo(Seniority.INTERN);
		assertThat(response.language()).isEqualTo("en");
		assertThat(response.paid()).isTrue();
	}

	@Test
	void findsAClosedPostingByIdAndNothingForAnUnknownId() {
		Posting closed = posting(acme, "p1", "Closed Intern").closed().save();

		PostingResponse response = search.findById(closed.getId()).orElseThrow();

		assertThat(response.status()).isEqualTo(PostingStatus.CLOSED);
		assertThat(response.closedAt()).isEqualTo(T);
		assertThat(search.findById(closed.getId() + 1000)).isEmpty();
	}

	// ----- helpers ------------------------------------------------------------------------------

	private CompanySource source(String name, String slug) {
		Company company = companies.save(new Company(name, slug, "https://" + slug + ".example"));
		return sources.save(new CompanySource(company, SourceType.GREENHOUSE, slug, SourceRegion.GLOBAL));
	}

	private static List<String> titles(PageResponse<PostingResponse> page) {
		return page.items().stream().map(PostingResponse::title).toList();
	}

	private static Classification classification(boolean earlyCareer, boolean locationEligible) {
		return new Classification(earlyCareer, OpportunityType.INTERNSHIP, Domain.SOFTWARE, Seniority.INTERN,
				locationEligible, "en", null, List.of());
	}

	private static Classification of(OpportunityType type, Domain domain) {
		return of(type, domain, "en", null);
	}

	private static Classification of(OpportunityType type, Domain domain, String language, Boolean paid) {
		return new Classification(true, type, domain, Seniority.INTERN, true, language, paid, List.of());
	}

	private PostingBuilder posting(CompanySource source, String externalId, String title) {
		return new PostingBuilder(source, externalId, title);
	}

	private static RequestBuilder request() {
		return new RequestBuilder();
	}

	/** Builds and stores a posting; early career, in Romania and open unless said otherwise. */
	private final class PostingBuilder {

		private final CompanySource source;

		private final String externalId;

		private final String title;

		private String snippet;

		private List<String> locations = List.of("Bucharest, Romania");

		private RemoteType remoteType = RemoteType.UNKNOWN;

		private Instant postedAt = T;

		private Classification classification = of(OpportunityType.INTERNSHIP, Domain.SOFTWARE);

		private boolean closed;

		PostingBuilder(CompanySource source, String externalId, String title) {
			this.source = source;
			this.externalId = externalId;
			this.title = title;
		}

		PostingBuilder snippet(String snippet) {
			this.snippet = snippet;
			return this;
		}

		PostingBuilder locations(String... locations) {
			this.locations = List.of(locations);
			return this;
		}

		PostingBuilder remoteType(RemoteType remoteType) {
			this.remoteType = remoteType;
			return this;
		}

		PostingBuilder postedAt(Instant postedAt) {
			this.postedAt = postedAt;
			return this;
		}

		PostingBuilder classification(Classification classification) {
			this.classification = classification;
			return this;
		}

		PostingBuilder closed() {
			this.closed = true;
			return this;
		}

		Posting save() {
			Posting posting = new Posting(source, externalId, title, "https://example.com/jobs/" + externalId,
					locations, remoteType, snippet, postedAt, "h".repeat(64), T);
			posting.applyClassification(classification);
			if (closed) {
				posting.close(T);
			}
			postings.save(posting);
			entityManager.flush();
			return posting;
		}

	}

	private static final class RequestBuilder {

		private String q;

		private List<Domain> domain;

		private List<OpportunityType> type;

		private String company;

		private String city;

		private RemoteType remote;

		private String language;

		private Boolean paid;

		private Boolean earlyCareer;

		private Boolean romaniaOnly;

		private Integer page;

		private Integer size;

		RequestBuilder q(String q) {
			this.q = q;
			return this;
		}

		RequestBuilder domain(Domain... domain) {
			this.domain = List.of(domain);
			return this;
		}

		RequestBuilder type(OpportunityType... type) {
			this.type = List.of(type);
			return this;
		}

		RequestBuilder company(String company) {
			this.company = company;
			return this;
		}

		RequestBuilder city(String city) {
			this.city = city;
			return this;
		}

		RequestBuilder remote(RemoteType remote) {
			this.remote = remote;
			return this;
		}

		RequestBuilder language(String language) {
			this.language = language;
			return this;
		}

		RequestBuilder paid(Boolean paid) {
			this.paid = paid;
			return this;
		}

		RequestBuilder earlyCareer(Boolean earlyCareer) {
			this.earlyCareer = earlyCareer;
			return this;
		}

		RequestBuilder romaniaOnly(Boolean romaniaOnly) {
			this.romaniaOnly = romaniaOnly;
			return this;
		}

		RequestBuilder page(Integer page) {
			this.page = page;
			return this;
		}

		RequestBuilder size(Integer size) {
			this.size = size;
			return this;
		}

		PostingSearchRequest build() {
			return new PostingSearchRequest(q, domain, type, company, city, remote, language, paid, earlyCareer,
					romaniaOnly, page, size);
		}

	}

}
