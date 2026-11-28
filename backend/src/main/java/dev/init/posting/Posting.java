package dev.init.posting;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.init.classification.Classification;
import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.classification.Seniority;
import dev.init.company.Company;
import dev.init.company.CompanySource;
import dev.init.source.RemoteType;
import dev.init.source.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A normalized job posting. Only {@link #SNIPPET_MAX_LENGTH} characters of the description are
 * kept here; the full source payload lives in {@code raw_postings}.
 */
@Entity
@Table(name = "postings")
public class Posting {

	public static final int SNIPPET_MAX_LENGTH = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_source_id", nullable = false)
	private CompanySource companySource;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SourceType sourceType;

	@Column(nullable = false, length = 200)
	private String externalId;

	@Column(nullable = false, length = 500)
	private String title;

	@Column(nullable = false, length = 2000)
	private String url;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> locations = new ArrayList<>();

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RemoteType remoteType;

	@Column(length = SNIPPET_MAX_LENGTH)
	private String snippet;

	private Instant postedAt;

	@Column(nullable = false)
	private Instant firstSeenAt;

	@Column(nullable = false)
	private Instant lastSeenAt;

	private Instant closedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private PostingStatus status;

	@Column(nullable = false, length = 64)
	private String contentHash;

	@Column(name = "is_early_career", nullable = false)
	private boolean earlyCareer;

	@Column(nullable = false)
	private boolean locationEligible;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OpportunityType opportunityType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private Domain domain;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Seniority seniority;

	@Column(length = 8)
	private String language;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false)
	private List<String> skills = new ArrayList<>();

	private Boolean paid;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Posting() {
		// for JPA
	}

	public Posting(CompanySource companySource, String externalId, String title, String url, List<String> locations,
			RemoteType remoteType, String snippet, Instant postedAt, String contentHash, Instant seenAt) {
		this.companySource = Objects.requireNonNull(companySource, "companySource");
		this.company = companySource.getCompany();
		this.sourceType = companySource.getSourceType();
		this.externalId = Objects.requireNonNull(externalId, "externalId");
		this.title = Objects.requireNonNull(title, "title");
		this.url = Objects.requireNonNull(url, "url");
		this.locations = new ArrayList<>(Objects.requireNonNull(locations, "locations"));
		this.remoteType = Objects.requireNonNull(remoteType, "remoteType");
		this.snippet = requireValidSnippet(snippet);
		this.postedAt = postedAt;
		this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
		this.firstSeenAt = Objects.requireNonNull(seenAt, "seenAt");
		this.lastSeenAt = seenAt;
		this.status = PostingStatus.OPEN;
		this.earlyCareer = false;
		this.locationEligible = true;
		this.opportunityType = OpportunityType.OTHER;
		this.domain = Domain.OTHER;
		this.seniority = Seniority.UNKNOWN;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	/** Stores the classifier's verdict. {@code language} and {@code paid} may be null when unknown. */
	public void applyClassification(Classification classification) {
		Objects.requireNonNull(classification, "classification");
		this.earlyCareer = classification.earlyCareer();
		this.locationEligible = classification.locationEligible();
		this.opportunityType = classification.opportunityType();
		this.domain = classification.domain();
		this.seniority = classification.seniority();
		this.language = classification.language();
		this.paid = classification.paid();
	}

	/** The skills the posting mentions, in the extractor's order. */
	public void applySkills(List<String> skills) {
		List<String> copy = List.copyOf(Objects.requireNonNull(skills, "skills"));
		if (!copy.equals(this.skills)) {
			this.skills = new ArrayList<>(copy);
		}
	}

	/**
	 * Replaces the content fields after the source changed the posting. Lifecycle fields
	 * ({@code firstSeenAt}, {@code status}, ...) are untouched; use {@link #markSeen}.
	 */
	public void refresh(String title, String url, List<String> locations, RemoteType remoteType, String snippet,
			Instant postedAt, String contentHash) {
		this.title = Objects.requireNonNull(title, "title");
		this.url = Objects.requireNonNull(url, "url");
		this.locations = new ArrayList<>(Objects.requireNonNull(locations, "locations"));
		this.remoteType = Objects.requireNonNull(remoteType, "remoteType");
		this.snippet = requireValidSnippet(snippet);
		this.postedAt = postedAt;
		this.contentHash = Objects.requireNonNull(contentHash, "contentHash");
	}

	/**
	 * Records that the posting was still published at {@code seenAt}. A closed posting that shows up
	 * again is reopened.
	 * @return {@code true} if the posting was closed and is open again
	 */
	public boolean markSeen(Instant seenAt) {
		this.lastSeenAt = Objects.requireNonNull(seenAt, "seenAt");
		if (status == PostingStatus.CLOSED) {
			this.status = PostingStatus.OPEN;
			this.closedAt = null;
			return true;
		}
		return false;
	}

	/** The source no longer publishes the posting. */
	public void close(Instant closedAt) {
		this.status = PostingStatus.CLOSED;
		this.closedAt = Objects.requireNonNull(closedAt, "closedAt");
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	private static String requireValidSnippet(String snippet) {
		if (snippet != null && snippet.length() > SNIPPET_MAX_LENGTH) {
			throw new IllegalArgumentException(
					"snippet must be at most " + SNIPPET_MAX_LENGTH + " characters, was " + snippet.length());
		}
		return snippet;
	}

	public Long getId() {
		return id;
	}

	public Company getCompany() {
		return company;
	}

	public CompanySource getCompanySource() {
		return companySource;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getTitle() {
		return title;
	}

	public String getUrl() {
		return url;
	}

	public List<String> getLocations() {
		return List.copyOf(locations);
	}

	public RemoteType getRemoteType() {
		return remoteType;
	}

	public String getSnippet() {
		return snippet;
	}

	public Instant getPostedAt() {
		return postedAt;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public PostingStatus getStatus() {
		return status;
	}

	public String getContentHash() {
		return contentHash;
	}

	public boolean isEarlyCareer() {
		return earlyCareer;
	}

	public boolean isLocationEligible() {
		return locationEligible;
	}

	public OpportunityType getOpportunityType() {
		return opportunityType;
	}

	public Domain getDomain() {
		return domain;
	}

	public Seniority getSeniority() {
		return seniority;
	}

	public String getLanguage() {
		return language;
	}

	public List<String> getSkills() {
		return List.copyOf(skills);
	}

	public Boolean getPaid() {
		return paid;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
