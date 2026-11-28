package dev.init.ingestion;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.init.classification.PostingClassifier;
import dev.init.classification.SkillExtractor;
import dev.init.company.CompanySource;
import dev.init.company.CompanySourceRepository;
import dev.init.posting.Posting;
import dev.init.posting.PostingRepository;
import dev.init.posting.PostingStatus;
import dev.init.posting.Snippets;
import dev.init.source.NormalizedJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores one source's batch in a single transaction and works out what changed since the last run:
 * <ul>
 * <li><b>new</b>: the (source, external id) pair was never seen;</li>
 * <li><b>updated</b>: the content hash changed, or the posting was closed and reappeared; a changed
 * posting is classified again;</li>
 * <li><b>closed</b>: an open posting that the source no longer publishes.</li>
 * </ul>
 * Closing only happens here, after a batch was fetched successfully: a failed fetch never gets this
 * far, so an outage cannot close every posting of a company.
 * <p>
 * Every payload is also kept in {@code raw_postings}, once per distinct content, so postings can be
 * normalized and classified again later without fetching.
 */
@Service
public class PostingSynchronizer {

	private final CompanySourceRepository sources;

	private final PostingRepository postings;

	private final RawPostingRepository rawPostings;

	private final PostingClassifier classifier;

	private final SkillExtractor skills;

	public PostingSynchronizer(CompanySourceRepository sources, PostingRepository postings,
			RawPostingRepository rawPostings, PostingClassifier classifier, SkillExtractor skills) {
		this.sources = sources;
		this.postings = postings;
		this.rawPostings = rawPostings;
		this.classifier = classifier;
		this.skills = skills;
	}

	@Transactional
	public SyncResult synchronize(Long sourceId, SourceBatch batch, Instant now) {
		CompanySource source = sources.findById(sourceId)
			.orElseThrow(() -> new IllegalArgumentException("No company source " + sourceId));
		Map<String, Posting> existing = postings.findByCompanySourceId(sourceId)
			.stream()
			.collect(Collectors.toMap(Posting::getExternalId, Function.identity()));
		Set<String> stillPublished = new HashSet<>(batch.skippedIds());

		int created = 0;
		int updated = 0;
		for (IngestedJob job : uniqueByExternalId(batch)) {
			NormalizedJob normalized = job.normalized();
			String externalId = normalized.externalId();
			stillPublished.add(externalId);
			storeRawPayload(source, job, now);

			Posting posting = existing.get(externalId);
			if (posting == null) {
				create(source, normalized, now);
				created++;
			}
			else if (update(posting, normalized, now)) {
				updated++;
			}
		}

		int closed = 0;
		for (Posting posting : existing.values()) {
			if (posting.getStatus() == PostingStatus.OPEN && !stillPublished.contains(posting.getExternalId())) {
				posting.close(now);
				closed++;
			}
		}
		return new SyncResult(created, updated, closed);
	}

	private void create(CompanySource source, NormalizedJob job, Instant now) {
		Posting posting = new Posting(source, job.externalId(), job.title(), job.url(), job.locations(),
				job.remoteType(), Snippets.of(job.description()), job.postedAt(), ContentHasher.hash(job), now);
		posting.applyClassification(classifier.classify(job));
		posting.applySkills(skills.extract(job));
		postings.save(posting);
	}

	/** @return whether the posting changed or was reopened */
	private boolean update(Posting posting, NormalizedJob job, Instant now) {
		String hash = ContentHasher.hash(job);
		boolean changed = !hash.equals(posting.getContentHash());
		if (changed) {
			posting.refresh(job.title(), job.url(), job.locations(), job.remoteType(), Snippets.of(job.description()),
					job.postedAt(), hash);
			posting.applyClassification(classifier.classify(job));
		}
		// cheap, and fills in skills for postings stored before skills existed; unchanged lists write nothing
		posting.applySkills(skills.extract(job));
		boolean reopened = posting.markSeen(now);
		return changed || reopened;
	}

	private void storeRawPayload(CompanySource source, IngestedJob job, Instant now) {
		String payload = job.raw().payload();
		String payloadHash = ContentHasher.sha256(payload);
		String externalId = job.raw().externalId();
		if (!rawPostings.existsByCompanySourceIdAndExternalIdAndPayloadHash(source.getId(), externalId, payloadHash)) {
			rawPostings.save(new RawPosting(source, externalId, payload, payloadHash, now));
		}
	}

	/** A source that lists the same posting twice must not insert it twice; the first occurrence wins. */
	private static Iterable<IngestedJob> uniqueByExternalId(SourceBatch batch) {
		Map<String, IngestedJob> unique = new LinkedHashMap<>();
		for (IngestedJob job : batch.jobs()) {
			unique.putIfAbsent(job.normalized().externalId(), job);
		}
		return unique.values();
	}

}
