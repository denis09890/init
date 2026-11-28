package dev.init.source.http;

import java.net.URI;
import java.util.Optional;

import dev.init.source.SourceFetchException;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The single place connectors use to read public sources. Every request is rate limited per host and
 * retried when that makes sense; every failure becomes a {@link SourceFetchException} that says
 * whether retrying is worthwhile.
 */
public class SourceHttpClient {

	private final RestClient restClient;

	private final HostRateLimiter rateLimiter;

	private final RetryPolicy retryPolicy;

	/** Without rate limiting or retries; for tests against a mock server. */
	public SourceHttpClient(RestClient restClient) {
		this(restClient, HostRateLimiter.unlimited(), RetryPolicy.none());
	}

	public SourceHttpClient(RestClient restClient, HostRateLimiter rateLimiter, RetryPolicy retryPolicy) {
		this.restClient = restClient;
		this.rateLimiter = rateLimiter;
		this.retryPolicy = retryPolicy;
	}

	public String getJson(URI uri) {
		return get(uri, String.class, "", MediaType.APPLICATION_JSON);
	}

	public String getHtml(URI uri) {
		return get(uri, String.class, "", MediaType.TEXT_HTML);
	}

	/**
	 * The body as bytes, so the XML parser decodes it with the encoding the document declares; a
	 * {@code text/xml} response without a charset would otherwise be read as ISO-8859-1.
	 */
	public byte[] getXml(URI uri) {
		return get(uri, byte[].class, new byte[0], MediaType.APPLICATION_XML, MediaType.TEXT_XML);
	}

	/** Like a GET for plain text, but a 404 or 410 means "not there" rather than a failure. */
	public Optional<String> getTextIfPresent(URI uri) {
		try {
			return Optional.of(get(uri, String.class, "", MediaType.TEXT_PLAIN));
		}
		catch (SourceFetchException ex) {
			int status = ex.statusCode().orElse(0);
			if (status == 404 || status == 410) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	/**
	 * The rate limit applies to each attempt, not around the retries, so a request that is waiting to
	 * be retried does not hold a slot on the host.
	 */
	private <T> T get(URI uri, Class<T> bodyType, T emptyBody, MediaType... accept) {
		String host = uri.getHost() == null ? "" : uri.getHost();
		return retryPolicy.execute(() -> rateLimiter.execute(host, () -> request(uri, bodyType, emptyBody, accept)));
	}

	private <T> T request(URI uri, Class<T> bodyType, T emptyBody, MediaType... accept) {
		try {
			T body = restClient.get().uri(uri).accept(accept).retrieve().body(bodyType);
			return body == null ? emptyBody : body;
		}
		catch (RestClientResponseException ex) {
			throw SourceFetchException.httpStatus(uri, ex.getStatusCode().value(), ex);
		}
		catch (ResourceAccessException ex) {
			throw SourceFetchException.io(uri, ex);
		}
	}

}
