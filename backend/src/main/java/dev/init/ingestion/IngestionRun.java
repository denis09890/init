package dev.init.ingestion;

import java.time.Instant;
import java.util.Objects;

import dev.init.company.CompanySource;
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

/** One ingestion attempt for one company source, with its outcome and counters. */
@Entity
@Table(name = "ingestion_runs")
public class IngestionRun {

	static final int ERROR_MESSAGE_MAX_LENGTH = 4000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_source_id", nullable = false)
	private CompanySource companySource;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SourceType sourceType;

	@Column(nullable = false)
	private Instant startedAt;

	private Instant finishedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private IngestionRunStatus status;

	@Column(nullable = false)
	private int fetchedCount;

	@Column(nullable = false)
	private int newCount;

	@Column(nullable = false)
	private int updatedCount;

	@Column(nullable = false)
	private int closedCount;

	@Column(length = ERROR_MESSAGE_MAX_LENGTH)
	private String errorMessage;

	protected IngestionRun() {
		// for JPA
	}

	private IngestionRun(CompanySource companySource, Instant startedAt) {
		this.companySource = Objects.requireNonNull(companySource, "companySource");
		this.sourceType = companySource.getSourceType();
		this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
		this.status = IngestionRunStatus.RUNNING;
	}

	public static IngestionRun start(CompanySource companySource, Instant startedAt) {
		return new IngestionRun(companySource, startedAt);
	}

	public void succeed(int fetched, int created, int updated, int closed, Instant finishedAt) {
		requireRunning();
		this.fetchedCount = fetched;
		this.newCount = created;
		this.updatedCount = updated;
		this.closedCount = closed;
		this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
		this.status = IngestionRunStatus.SUCCEEDED;
	}

	/**
	 * Like {@link #succeed(int, int, int, int, Instant)}, with a note about postings that could not be
	 * processed. The run still counts as succeeded: the source answered and its other postings were stored.
	 */
	public void succeed(int fetched, int created, int updated, int closed, String warning, Instant finishedAt) {
		succeed(fetched, created, updated, closed, finishedAt);
		this.errorMessage = truncate(warning);
	}

	public void fail(String errorMessage, Instant finishedAt) {
		requireRunning();
		this.errorMessage = truncate(errorMessage);
		this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
		this.status = IngestionRunStatus.FAILED;
	}

	private void requireRunning() {
		if (status != IngestionRunStatus.RUNNING) {
			throw new IllegalStateException("Ingestion run " + id + " is already " + status);
		}
	}

	private static String truncate(String message) {
		if (message == null || message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
			return message;
		}
		return message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
	}

	public Long getId() {
		return id;
	}

	public CompanySource getCompanySource() {
		return companySource;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public IngestionRunStatus getStatus() {
		return status;
	}

	public int getFetchedCount() {
		return fetchedCount;
	}

	public int getNewCount() {
		return newCount;
	}

	public int getUpdatedCount() {
		return updatedCount;
	}

	public int getClosedCount() {
		return closedCount;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}
