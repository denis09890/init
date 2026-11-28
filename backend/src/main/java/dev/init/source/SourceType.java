package dev.init.source;

/**
 * Kind of public source a company publishes its job postings through. Stored as text; the allowed
 * values are also listed in the database CHECK constraints (Flyway migrations V1 and V4).
 */
public enum SourceType {
	GREENHOUSE,
	LEVER,
	SMARTRECRUITERS,
	JSON_LD,
	ASHBY,
	PERSONIO
}
