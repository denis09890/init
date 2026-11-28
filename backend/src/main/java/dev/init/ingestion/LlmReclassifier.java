package dev.init.ingestion;

import java.util.List;
import java.util.Optional;

import dev.init.classification.llm.LlmClassifier;
import dev.init.classification.llm.LlmVerdict;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.SourceConnectors;
import dev.init.source.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Replaces the rules' classification with the LLM's, a few postings at a time. Runs apart from the
 * ingestion, because the LLM is slow and rate limited and the ingestion stores each source in one
 * short transaction. Only postings the feed can show are sent: open, location-eligible, not
 * duplicates, and not judged by the LLM since their content last changed ({@code llm_content_hash}).
 * The full description is read again from the stored raw payload.
 */
@Service
public class LlmReclassifier {

	private static final Logger log = LoggerFactory.getLogger(LlmReclassifier.class);

	/** Stop the pass after this many failures in a row: the API is down or the limits are reached. */
	static final int MAX_CONSECUTIVE_FAILURES = 3;

	private static final String CANDIDATES = """
			select distinct on (p.id)
			       p.id, p.source_type, p.external_id, p.content_hash, r.payload::text as payload
			from postings p
			join raw_postings r on r.company_source_id = p.company_source_id and r.external_id = p.external_id
			where p.status = 'OPEN' and p.location_eligible and p.duplicate_of_id is null
			  and p.llm_content_hash is distinct from p.content_hash
			order by p.id desc, r.fetched_at desc, r.id desc
			limit :limit
			""";

	/** Only if the content is still the one that was judged. */
	private static final String APPLY = """
			update postings
			set is_early_career = :earlyCareer, opportunity_type = :type, domain = :domain, seniority = :seniority,
			    llm_content_hash = :hash, updated_at = now()
			where id = :id and content_hash = :hash
			""";

	private final JdbcClient jdbc;

	private final SourceConnectors connectors;

	private final LlmClassifier llm;

	public LlmReclassifier(JdbcClient jdbc, SourceConnectors connectors, LlmClassifier llm) {
		this.jdbc = jdbc;
		this.connectors = connectors;
		this.llm = llm;
	}

	/** @return how many postings now carry the LLM's classification */
	public int run(int limit) {
		if (!llm.isActive()) {
			return 0;
		}
		List<Candidate> candidates = jdbc.sql(CANDIDATES)
			.param("limit", limit)
			.query((rs, rowNumber) -> new Candidate(rs.getLong("id"), SourceType.valueOf(rs.getString("source_type")),
					rs.getString("external_id"), rs.getString("content_hash"), rs.getString("payload")))
			.list();
		int applied = 0;
		int failuresInARow = 0;
		for (Candidate candidate : candidates) {
			Optional<LlmVerdict> verdict = normalize(candidate).flatMap(llm::classify);
			if (verdict.isEmpty()) {
				if (++failuresInARow >= MAX_CONSECUTIVE_FAILURES) {
					log.warn("LLM pass stopped after {} failures in a row", failuresInARow);
					break;
				}
				continue;
			}
			failuresInARow = 0;
			applied += apply(candidate, verdict.get());
		}
		log.info("LLM pass: {} of {} postings classified", applied, candidates.size());
		return applied;
	}

	private Optional<NormalizedJob> normalize(Candidate candidate) {
		try {
			return Optional.of(connectors.get(candidate.sourceType())
				.normalize(new RawJob(candidate.externalId(), candidate.payload())));
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	private int apply(Candidate candidate, LlmVerdict verdict) {
		return jdbc.sql(APPLY)
			.param("earlyCareer", verdict.earlyCareer())
			.param("type", verdict.opportunityType().name())
			.param("domain", verdict.domain().name())
			.param("seniority", verdict.seniority().name())
			.param("hash", candidate.contentHash())
			.param("id", candidate.id())
			.update();
	}

	private record Candidate(long id, SourceType sourceType, String externalId, String contentHash, String payload) {
	}

}
