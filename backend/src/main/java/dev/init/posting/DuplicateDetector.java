package dev.init.posting;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks open postings that repeat an earlier open posting of the same company: the same link, or a
 * title that is almost the same (trigram similarity, pg_trgm) with at least one location in common.
 * The earliest posting stays visible; the others point to it through {@code duplicate_of_id} and are
 * left out of the feed and the counts. Nothing is deleted, and the marks are worked out again from
 * scratch after every ingestion, so a duplicate becomes visible again when its original closes.
 * <p>
 * Same title in another city is not a duplicate: it is a different place to work.
 */
@Service
public class DuplicateDetector {

	/** Similarity from which two titles of one company count as the same role (1 is identical). */
	static final double TITLE_SIMILARITY = 0.9;

	private static final String CLEAR = "update postings set duplicate_of_id = null where duplicate_of_id is not null";

	private static final String MARK = """
			update postings p
			set duplicate_of_id = pairs.original_id
			from (
			    select later.id as duplicate_id, min(earlier.id) as original_id
			    from postings later
			    join postings earlier
			      on earlier.company_id = later.company_id
			     and earlier.id < later.id
			     and earlier.status = 'OPEN'
			     and (earlier.url = later.url
			          or (similarity(lower(earlier.title), lower(later.title)) >= :similarity
			              and earlier.locations && later.locations))
			    where later.status = 'OPEN'
			    group by later.id
			) pairs
			where p.id = pairs.duplicate_id
			""";

	private final JdbcClient jdbc;

	public DuplicateDetector(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** @return how many open postings are duplicates now */
	@Transactional
	public int refresh() {
		jdbc.sql(CLEAR).update();
		return jdbc.sql(MARK).param("similarity", TITLE_SIMILARITY).update();
	}

}
