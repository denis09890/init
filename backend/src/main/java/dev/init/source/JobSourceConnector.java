package dev.init.source;

import java.util.List;

/**
 * Reads postings from one kind of public source. Supporting a new ATS means adding one
 * implementation of this interface.
 * <p>
 * Fetching and normalizing are separate so that stored raw payloads can be normalized again after
 * the normalization improves, without fetching them again.
 */
public interface JobSourceConnector {

	SourceType type();

	/**
	 * Fetches all currently published postings of the target.
	 * @throws SourceFetchException if the source cannot be reached or answers unexpectedly
	 */
	List<RawJob> fetch(SourceTarget target);

	/**
	 * Converts a raw posting produced by {@link #fetch} into the common shape.
	 * @throws SourceFetchException if the payload is not a valid posting of this source
	 */
	NormalizedJob normalize(RawJob job);

}
