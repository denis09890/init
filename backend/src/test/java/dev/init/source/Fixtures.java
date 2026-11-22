package dev.init.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import dev.init.source.http.SourceHttpClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Test helpers for connectors: recorded fixtures and an HTTP client backed by a mock server. */
public final class Fixtures {

	private Fixtures() {
	}

	/** Reads {@code src/test/resources/fixtures/<path>}. */
	public static String read(String path) {
		try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
			if (in == null) {
				throw new IllegalArgumentException("No fixture at /fixtures/" + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static ObjectMapper objectMapper() {
		return JsonMapper.builder().build();
	}

	/** A {@link SourceHttpClient} whose requests are answered by the returned mock server. */
	public static MockedHttp mockedHttp() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		return new MockedHttp(new SourceHttpClient(builder.build()), server);
	}

	public record MockedHttp(SourceHttpClient client, MockRestServiceServer server) {
	}

}
