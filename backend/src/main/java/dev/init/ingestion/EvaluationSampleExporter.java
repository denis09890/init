package dev.init.ingestion;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.init.classification.Classification;
import dev.init.classification.PostingClassifier;
import dev.init.posting.Snippets;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.SourceConnectors;
import dev.init.source.SourceType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds an {@link EvaluationSample} from what is already stored: each open posting's latest raw
 * payload is normalized and classified again with the current code, without fetching anything.
 */
@Service
public class EvaluationSampleExporter {

	/** The newest stored payload of every open posting. */
	private static final String LATEST_PAYLOADS = """
			select distinct on (p.id)
			       p.id, c.slug as company, p.source_type, p.external_id, p.url, r.payload::text as payload
			from postings p
			join companies c on c.id = p.company_id
			join raw_postings r on r.company_source_id = p.company_source_id and r.external_id = p.external_id
			where p.status = 'OPEN'
			order by p.id, r.fetched_at desc, r.id desc
			""";

	private final JdbcClient jdbc;

	private final SourceConnectors connectors;

	private final PostingClassifier classifier;

	private final Clock clock;

	public EvaluationSampleExporter(JdbcClient jdbc, SourceConnectors connectors, PostingClassifier classifier,
			Clock clock) {
		this.jdbc = jdbc;
		this.connectors = connectors;
		this.classifier = classifier;
		this.clock = clock;
	}

	/**
	 * @param negatives how many postings the rules do not call early career to include; the sample is
	 * the same for the same data, so labels can be reused when it is exported again
	 */
	@Transactional(readOnly = true)
	public EvaluationSample export(int negatives) {
		if (negatives < 0) {
			throw new IllegalArgumentException("negatives must not be negative");
		}
		List<Candidate> positives = new ArrayList<>();
		List<Candidate> others = new ArrayList<>();
		int skipped = 0;
		for (StoredPosting stored : loadLatestPayloads()) {
			Candidate candidate;
			try {
				NormalizedJob job = connectors.get(stored.sourceType())
					.normalize(new RawJob(stored.externalId(), stored.payload()));
				candidate = new Candidate(stored, job, classifier.classify(job));
			}
			catch (RuntimeException ex) {
				skipped++;
				continue;
			}
			if (!candidate.verdict().locationEligible()) {
				continue;
			}
			(candidate.verdict().earlyCareer() ? positives : others).add(candidate);
		}
		positives.sort(Comparator.comparing(Candidate::key));
		// A hash of the key orders the negatives randomly but reproducibly.
		others.sort(Comparator.comparing((Candidate candidate) -> ContentHasher.sha256(candidate.key()))
			.thenComparing(Candidate::key));
		List<Candidate> sampledNegatives = others.subList(0, Math.min(negatives, others.size()));

		List<EvaluationSample.Item> items = new ArrayList<>();
		positives.forEach(candidate -> items.add(candidate.toItem(EvaluationSample.Stratum.RULES_POSITIVE)));
		sampledNegatives.forEach(candidate -> items.add(candidate.toItem(EvaluationSample.Stratum.RULES_NEGATIVE)));
		return new EvaluationSample(clock.instant(), positives.size() + others.size(), positives.size(),
				others.size(), sampledNegatives.size(), skipped, items);
	}

	private List<StoredPosting> loadLatestPayloads() {
		return jdbc.sql(LATEST_PAYLOADS)
			.query((rs, rowNumber) -> new StoredPosting(rs.getString("company"),
					SourceType.valueOf(rs.getString("source_type")), rs.getString("external_id"), rs.getString("url"),
					rs.getString("payload")))
			.list();
	}

	private record StoredPosting(String company, SourceType sourceType, String externalId, String url,
			String payload) {
	}

	private record Candidate(StoredPosting stored, NormalizedJob job, Classification verdict) {

		String key() {
			return stored.sourceType() + ":" + stored.company() + ":" + stored.externalId();
		}

		EvaluationSample.Item toItem(EvaluationSample.Stratum stratum) {
			EvaluationSample.Input input = new EvaluationSample.Input(job.title(), job.locations(), job.countryCode(),
					job.remoteType(), job.department(), job.employmentType(), job.experienceLevel(),
					Snippets.of(job.description()));
			EvaluationSample.RulesVerdict rules = new EvaluationSample.RulesVerdict(verdict.earlyCareer(),
					verdict.opportunityType(), verdict.domain(), verdict.seniority(), verdict.reasons());
			return new EvaluationSample.Item(key(), stored.company(), stored.url(), stratum, input, rules);
		}

	}

}
