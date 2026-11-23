package dev.init.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

import dev.init.source.NormalizedJob;

/** SHA-256 fingerprints used to detect changes without comparing whole postings. */
final class ContentHasher {

	/** Unit separator: cannot appear in the text fields, so field boundaries are unambiguous. */
	private static final String FIELD_SEPARATOR = "\u001f";

	private static final String LIST_SEPARATOR = "\u001e";

	private ContentHasher() {
	}

	/**
	 * Fingerprint of what a user sees of a posting. The posting date is left out on purpose: some
	 * sources fall back to a last-updated timestamp that changes without the posting changing, which
	 * would report an update on every run.
	 */
	static String hash(NormalizedJob job) {
		StringJoiner joiner = new StringJoiner(FIELD_SEPARATOR);
		joiner.add(job.title());
		joiner.add(job.url());
		joiner.add(String.join(LIST_SEPARATOR, job.locations()));
		joiner.add(nullToEmpty(job.countryCode()));
		joiner.add(job.remoteType().name());
		joiner.add(job.description());
		joiner.add(nullToEmpty(job.department()));
		joiner.add(nullToEmpty(job.employmentType()));
		joiner.add(nullToEmpty(job.experienceLevel()));
		return sha256(joiner.toString());
	}

	static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java runtime", ex);
		}
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
