package dev.init.source;

import java.net.URI;
import java.util.OptionalInt;

/** A source could not be read: network failure, error status, or a response we cannot parse. */
public class SourceFetchException extends RuntimeException {

	private final Integer statusCode;

	private final boolean retryable;

	public SourceFetchException(String message) {
		this(message, null, null, false);
	}

	public SourceFetchException(String message, Throwable cause) {
		this(message, cause, null, false);
	}

	private SourceFetchException(String message, Throwable cause, Integer statusCode, boolean retryable) {
		super(message, cause);
		this.statusCode = statusCode;
		this.retryable = retryable;
	}

	/** The server answered with an error status. 429 and 5xx are worth retrying. */
	public static SourceFetchException httpStatus(URI uri, int statusCode, Throwable cause) {
		boolean retryable = statusCode == 429 || statusCode >= 500;
		return new SourceFetchException("GET " + uri + " returned HTTP " + statusCode, cause, statusCode, retryable);
	}

	/** The request did not complete (timeout, connection refused, DNS). Worth retrying. */
	public static SourceFetchException io(URI uri, Throwable cause) {
		return new SourceFetchException("GET " + uri + " failed: " + cause.getMessage(), cause, null, true);
	}

	public OptionalInt statusCode() {
		return statusCode == null ? OptionalInt.empty() : OptionalInt.of(statusCode);
	}

	public boolean isRetryable() {
		return retryable;
	}

}
