package dev.init.company;

import java.time.Instant;
import java.util.Objects;

import dev.init.source.SourceRegion;
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
import jakarta.persistence.Table;

/**
 * Where a company's postings are fetched from: an ATS board (identified by its board token or
 * company id) or a career page URL with JSON-LD.
 */
@Entity
@Table(name = "company_sources")
public class CompanySource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SourceType sourceType;

	@Column(nullable = false, length = 500)
	private String identifier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private SourceRegion region;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected CompanySource() {
		// for JPA
	}

	public CompanySource(Company company, SourceType sourceType, String identifier, SourceRegion region) {
		this.company = Objects.requireNonNull(company, "company");
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
		this.identifier = Objects.requireNonNull(identifier, "identifier");
		this.region = Objects.requireNonNull(region, "region");
		this.active = true;
		this.createdAt = Instant.now();
	}

	/** @return {@code true} if the region changed */
	public boolean updateRegion(SourceRegion region) {
		Objects.requireNonNull(region, "region");
		boolean changed = region != this.region;
		this.region = region;
		return changed;
	}

	/** @return {@code true} if the source was inactive */
	public boolean activate() {
		boolean changed = !active;
		this.active = true;
		return changed;
	}

	/** @return {@code true} if the source was active */
	public boolean deactivate() {
		boolean changed = active;
		this.active = false;
		return changed;
	}

	public Long getId() {
		return id;
	}

	public Company getCompany() {
		return company;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public String getIdentifier() {
		return identifier;
	}

	public SourceRegion getRegion() {
		return region;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
