package dev.init.source.http;

import java.net.URI;
import java.util.Optional;

import dev.init.source.SourceFetchException;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The single place connectors use to read public sources. Translates every failure into a
 * {@link SourceFetchException} that says whether retrying makes sense.
 */
public class SourceHttpClient {

	private final RestClient restClient;

	public SourceHttpClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public String getJson(URI uri) {
		return get(uri, MediaType.APPLICATION_JSON);
	}

	public String getHtml(URI uri) {
		return get(uri, MediaType.TEXT_HTML);
	}

	/** Like a GET for plain text, but a 404 or 410 means "not there" rather than a failure. */
	public Optional<String> getTextIfPresent(URI uri) {
		try {
			return Optional.of(get(uri, MediaType.TEXT_PLAIN));
		}
		catch (SourceFetchException ex) {
			int status = ex.statusCode().orElse(0);
			if (status == 404 || status == 410) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	private String get(URI uri, MediaType accept) {
		try {
			String body = restClient.get().uri(uri).accept(accept).retrieve().body(String.class);
			return body == null ? "" : body;
		}
		catch (RestClientResponseException ex) {
			throw SourceFetchException.httpStatus(uri, ex.getStatusCode().value(), ex);
		}
		catch (ResourceAccessException ex) {
			throw SourceFetchException.io(uri, ex);
		}
	}

}
