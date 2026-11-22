package dev.init.ingestion;

import java.time.Instant;
import java.util.Objects;

import dev.init.company.CompanySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The full payload of a posting exactly as the source returned it. Internal only: it allows
 * re-normalizing and re-classifying without fetching again, and is never exposed through the API.
 */
@Entity
@Table(name = "raw_postings")
public class RawPosting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_source_id", nullable = false)
	private CompanySource companySource;

	@Column(nullable = false, length = 200)
	private String externalId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Column(nullable = false, length = 64)
	private String payloadHash;

	@Column(nullable = false)
	private Instant fetchedAt;

	protected RawPosting() {
		// for JPA
	}

	public RawPosting(CompanySource companySource, String externalId, String payload, String payloadHash,
			Instant fetchedAt) {
		this.companySource = Objects.requireNonNull(companySource, "companySource");
		this.externalId = Objects.requireNonNull(externalId, "externalId");
		this.payload = Objects.requireNonNull(payload, "payload");
		this.payloadHash = Objects.requireNonNull(payloadHash, "payloadHash");
		this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt");
	}

	public Long getId() {
		return id;
	}

	public CompanySource getCompanySource() {
		return companySource;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getPayload() {
		return payload;
	}

	public String getPayloadHash() {
		return payloadHash;
	}

	public Instant getFetchedAt() {
		return fetchedAt;
	}

}
