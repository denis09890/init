package dev.init.ingestion;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import dev.init.IntegrationTest;
import dev.init.classification.OpportunityType;
import dev.init.company.Company;
import dev.init.company.CompanyRepository;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.posting.Posting;
import dev.init.posting.PostingRepository;
import dev.init.posting.PostingStatus;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Change detection on a real PostgreSQL: new, updated, reopened and closed postings, and raw payload storage. */
@IntegrationTest
@Transactional
class PostingSynchronizerTests {

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	private static final Instant T1 = T0.plus(1, ChronoUnit.HOURS);

	private static final Instant T2 = T0.plus(2, ChronoUnit.HOURS);

	@Autowired
	private PostingSynchronizer synchronizer;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private PostingRepository postings;

	@Autowired
	private RawPostingRepository rawPostings;

	@Autowired
	private EntityManager entityManager;

	private CompanySource source;

	@BeforeEach
	void setUp() {
		Company company = companies.save(new Company("Acme", "acme", null));
		source = sources.save(new CompanySource(company, SourceType.GREENHOUSE, "acme", SourceRegion.GLOBAL));
	}

	@Test
	void storesNewPostingsWithClassificationSnippetAndRawPayload() {
		SyncResult result = sync(T0, job("a", "Software Engineer Intern", "Summer internship in Bucharest."),
				job("b", "Senior Backend Engineer", "Lead the payments platform."));

		assertThat(result).isEqualTo(new SyncResult(2, 0, 0));
		Posting intern = reload("a");
		assertThat(intern.getTitle()).isEqualTo("Software Engineer Intern");
		assertThat(intern.getUrl()).isEqualTo("https://example.com/jobs/a");
		assertThat(intern.getLocations()).containsExactly("Bucharest, Romania");
		assertThat(intern.getSnippet()).isEqualTo("Summer internship in Bucharest.");
		assertThat(intern.getStatus()).isEqualTo(PostingStatus.OPEN);
		assertThat(intern.getFirstSeenAt()).isEqualTo(T0);
		assertThat(intern.getLastSeenAt()).isEqualTo(T0);
		assertThat(intern.getContentHash()).hasSize(64);
		assertThat(intern.isEarlyCareer()).isTrue();
		assertThat(intern.getOpportunityType()).isEqualTo(OpportunityType.INTERNSHIP);
		assertThat(intern.isLocationEligible()).isTrue();
		assertThat(reload("b").isEarlyCareer()).isFalse();
		assertThat(rawPostings.count()).isEqualTo(2);
	}

	@Test
	void storesTheSkillsThePostingMentions() {
		sync(T0, job("a", "Junior Java Developer", "Build REST APIs with Spring Boot and SQL. Good English."));

		assertThat(reload("a").getSkills()).containsExactly("Java", "SQL", "Spring", "REST APIs", "English");
	}

	@Test
	void fillsInSkillsOfUnchangedPostingsOnTheNextRun() {
		sync(T0, job("a", "Junior Java Developer", "Build APIs."));
		jdbcClearSkills();

		SyncResult result = sync(T1, job("a", "Junior Java Developer", "Build APIs."));

		assertThat(result).as("not counted as an update").isEqualTo(new SyncResult(0, 0, 0));
		assertThat(reload("a").getSkills()).containsExactly("Java");
	}

	@Test
	void storesLocationEligibilityOfPostingsOutsideRomania() {
		sync(T0, job("a", "Software Engineer Intern", "Join us.", List.of("Toronto, Canada"), "CA"));

		Posting posting = reload("a");
		assertThat(posting.isEarlyCareer()).isTrue();
		assertThat(posting.isLocationEligible()).isFalse();
	}

	@Test
	void keepsOnlyTheStartOfLongDescriptionsAsSnippet() {
		sync(T0, job("a", "Intern", "word ".repeat(200).strip()));

		assertThat(reload("a").getSnippet()).hasSizeLessThanOrEqualTo(Posting.SNIPPET_MAX_LENGTH).endsWith("…");
	}

	@Test
	void unchangedPostingOnlyGetsANewLastSeenAt() {
		sync(T0, job("a", "Software Engineer Intern", "Join us."), job("b", "Accountant", "Audit books."));
		String hash = reload("a").getContentHash();

		SyncResult result = sync(T1, job("a", "Software Engineer Intern", "Join us."),
				job("b", "Accountant", "Audit books."));

		assertThat(result).isEqualTo(new SyncResult(0, 0, 0));
		Posting posting = reload("a");
		assertThat(posting.getContentHash()).isEqualTo(hash);
		assertThat(posting.getFirstSeenAt()).isEqualTo(T0);
		assertThat(posting.getLastSeenAt()).isEqualTo(T1);
		assertThat(rawPostings.count()).as("an unchanged payload is stored once").isEqualTo(2);
	}

	@Test
	void detectsChangedContentAndClassifiesAgain() {
		sync(T0, job("a", "Software Engineer", "Build APIs."));
		Posting before = reload("a");
		assertThat(before.isEarlyCareer()).isFalse();
		// copy the value: the synchronizer updates this very (still managed) instance
		String hashBefore = before.getContentHash();

		SyncResult result = sync(T1, job("a", "Junior Software Engineer", "Build APIs."));

		assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
		Posting after = reload("a");
		assertThat(after.getTitle()).isEqualTo("Junior Software Engineer");
		assertThat(after.getContentHash()).isNotEqualTo(hashBefore);
		assertThat(after.isEarlyCareer()).isTrue();
		assertThat(after.getOpportunityType()).isEqualTo(OpportunityType.JUNIOR);
		assertThat(after.getFirstSeenAt()).isEqualTo(T0);
		assertThat(after.getLastSeenAt()).isEqualTo(T1);
		assertThat(rawPostings.count()).as("every distinct payload is kept").isEqualTo(2);
	}

	@Test
	void closesPostingsMissingFromTheLatestRun() {
		sync(T0, job("a", "Intern A", "x"), job("b", "Intern B", "x"));

		SyncResult result = sync(T1, job("a", "Intern A", "x"));

		assertThat(result).isEqualTo(new SyncResult(0, 0, 1));
		assertThat(reload("a").getStatus()).isEqualTo(PostingStatus.OPEN);
		Posting closed = reload("b");
		assertThat(closed.getStatus()).isEqualTo(PostingStatus.CLOSED);
		assertThat(closed.getClosedAt()).isEqualTo(T1);
	}

	@Test
	void aClosedPostingIsNotClosedAgainOrCountedAgain() {
		sync(T0, job("a", "Intern A", "x"), job("b", "Intern B", "x"));
		sync(T1, job("a", "Intern A", "x"));

		SyncResult result = sync(T2, job("a", "Intern A", "x"));

		assertThat(result).isEqualTo(new SyncResult(0, 0, 0));
		assertThat(reload("b").getClosedAt()).isEqualTo(T1);
	}

	@Test
	void reopensAClosedPostingThatReappears() {
		sync(T0, job("a", "Intern A", "x"), job("b", "Intern B", "x"));
		sync(T1, job("a", "Intern A", "x"));

		SyncResult result = sync(T2, job("a", "Intern A", "x"), job("b", "Intern B", "x"));

		assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
		Posting reopened = reload("b");
		assertThat(reopened.getStatus()).isEqualTo(PostingStatus.OPEN);
		assertThat(reopened.getClosedAt()).isNull();
		assertThat(reopened.getLastSeenAt()).isEqualTo(T2);
		assertThat(reopened.getFirstSeenAt()).isEqualTo(T0);
	}

	@Test
	void postingsThatCouldNotBeNormalizedAreStillConsideredPublished() {
		sync(T0, job("a", "Intern A", "x"), job("b", "Intern B", "x"));

		SyncResult result = synchronizer.synchronize(source.getId(),
				new SourceBatch(List.of(job("a", "Intern A", "x")), Set.of("b")), T1);

		assertThat(result).isEqualTo(new SyncResult(0, 0, 0));
		assertThat(reload("b").getStatus()).isEqualTo(PostingStatus.OPEN);
	}

	@Test
	void aPostingListedTwiceIsStoredOnce() {
		SyncResult result = sync(T0, job("a", "First", "x"), job("a", "Second", "y"));

		assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
		assertThat(reload("a").getTitle()).isEqualTo("First");
	}

	private SyncResult sync(Instant now, IngestedJob... jobs) {
		return synchronizer.synchronize(source.getId(), new SourceBatch(List.of(jobs), Set.of()), now);
	}

	/** Like a posting stored before skills existed. */
	private void jdbcClearSkills() {
		entityManager.flush();
		entityManager.createNativeQuery("update postings set skills = '{}'").executeUpdate();
		entityManager.clear();
	}

	/** Entities are cleared first so what is read is what the database holds. */
	private Posting reload(String externalId) {
		entityManager.flush();
		entityManager.clear();
		return postings.findByCompanySourceIdAndExternalId(source.getId(), externalId).orElseThrow();
	}

	private static IngestedJob job(String id, String title, String description) {
		return job(id, title, description, List.of("Bucharest, Romania"), "RO");
	}

	private static IngestedJob job(String id, String title, String description, List<String> locations,
			String countryCode) {
		NormalizedJob normalized = new NormalizedJob(id, title, "https://example.com/jobs/" + id, locations,
				countryCode, RemoteType.HYBRID, description, T0, null, null, null);
		String payload = "{\"id\":\"%s\",\"title\":\"%s\",\"description\":\"%s\"}".formatted(id, title, description);
		return new IngestedJob(new RawJob(id, payload), normalized);
	}

}
