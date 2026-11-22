package dev.init.source.jsonld;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.init.source.JobSourceConnector;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import dev.init.source.http.SourceHttpClient;
import dev.init.source.support.HtmlText;
import dev.init.source.support.SourceJson;
import dev.init.source.support.SourceValues;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads schema.org {@code JobPosting} structured data (JSON-LD) embedded in a company's career page.
 * The source identifier is the page URL. The page's robots.txt is checked first; a disallowed page is
 * never fetched. Only postings embedded in the page itself are read, links are not followed.
 *
 * @see <a href="https://schema.org/JobPosting">schema.org JobPosting</a>
 */
@Component
public class JsonLdConnector implements JobSourceConnector {

	/** Product token matched against User-agent lines in robots.txt. */
	static final String ROBOTS_TOKEN = "InitBot";

	private static final String SOURCE = "JSON-LD";

	private final SourceHttpClient http;

	private final ObjectMapper mapper;

	public JsonLdConnector(SourceHttpClient http, ObjectMapper mapper) {
		this.http = http;
		this.mapper = mapper;
	}

	@Override
	public SourceType type() {
		return SourceType.JSON_LD;
	}

	@Override
	public List<RawJob> fetch(SourceTarget target) {
		if (target.type() != type()) {
			throw new IllegalArgumentException("Not a JSON-LD source: " + target);
		}
		URI page = pageUri(target.identifier());
		RobotsTxt robots = http.getTextIfPresent(page.resolve("/robots.txt"))
			.map(content -> RobotsTxt.parse(content, ROBOTS_TOKEN))
			.orElse(RobotsTxt.allowAll());
		String path = page.getRawPath() + (page.getRawQuery() == null ? "" : "?" + page.getRawQuery());
		if (!robots.isAllowed(path)) {
			throw new SourceFetchException("robots.txt of " + page.getHost() + " disallows " + page);
		}
		return extract(http.getHtml(page), page);
	}

	List<RawJob> extract(String html, URI page) {
		List<JsonNode> postings = new ArrayList<>();
		for (Element script : Jsoup.parse(html, page.toString()).select("script[type=application/ld+json]")) {
			JsonNode root;
			try {
				root = mapper.readTree(script.data());
			}
			catch (JacksonException malformedBlock) {
				// Pages sometimes ship broken JSON-LD next to valid blocks; skip only the broken one.
				continue;
			}
			collectJobPostings(root, postings);
		}
		List<RawJob> rawJobs = new ArrayList<>();
		for (JsonNode posting : postings) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("pageUrl", page.toString());
			payload.put("jobPosting", posting);
			JobPostingLd parsed = SourceJson.convert(mapper, posting, JobPostingLd.class, SOURCE);
			rawJobs.add(new RawJob(externalId(parsed), SourceJson.write(mapper, payload)));
		}
		return rawJobs;
	}

	@Override
	public NormalizedJob normalize(RawJob job) {
		JsonLdPayload payload = SourceJson.read(mapper, job.payload(), JsonLdPayload.class, SOURCE);
		JobPostingLd posting = payload.jobPosting();
		if (posting == null) {
			throw new SourceFetchException("JSON-LD payload without jobPosting");
		}
		List<Map<?, ?>> places = places(posting.jobLocation());
		boolean telecommute = "TELECOMMUTE".equalsIgnoreCase(SourceValues.trimToNull(posting.jobLocationType()));
		return new NormalizedJob(
				job.externalId(),
				SourceValues.requireText(posting.title(), "title", SOURCE),
				SourceValues.requireText(posting.url() != null ? posting.url() : payload.pageUrl(), "url", SOURCE),
				places.stream().map(JsonLdConnector::placeName).filter(Objects::nonNull).toList(),
				places.stream().map(JsonLdConnector::countryCode).filter(Objects::nonNull).findFirst().orElse(null),
				telecommute ? RemoteType.REMOTE : RemoteType.UNKNOWN,
				HtmlText.toPlainText(posting.description()),
				SourceValues.parseInstant(posting.datePosted()),
				null,
				joinValues(posting.employmentType()),
				null);
	}

	private void collectJobPostings(JsonNode node, List<JsonNode> out) {
		if (node.isArray()) {
			for (JsonNode child : node) {
				collectJobPostings(child, out);
			}
			return;
		}
		if (!node.isObject()) {
			return;
		}
		if (isJobPosting(node)) {
			out.add(node);
		}
		JsonNode graph = node.get("@graph");
		if (graph != null) {
			collectJobPostings(graph, out);
		}
	}

	private boolean isJobPosting(JsonNode node) {
		JsonNode type = node.get("@type");
		if (type == null) {
			return false;
		}
		Object value = SourceJson.convert(mapper, type, Object.class, SOURCE);
		if (value instanceof List<?> types) {
			return types.contains("JobPosting");
		}
		return "JobPosting".equals(value);
	}

	/** The posting's own identifier, else its URL, else a stable hash of what identifies it on the page. */
	private static String externalId(JobPostingLd posting) {
		Object identifier = posting.identifier();
		if (identifier instanceof Map<?, ?> map && map.get("value") != null) {
			return String.valueOf(map.get("value"));
		}
		if (identifier instanceof String text && !text.isBlank()) {
			return text.strip();
		}
		if (identifier instanceof Number number) {
			return number.toString();
		}
		String url = SourceValues.trimToNull(posting.url());
		if (url != null) {
			return url;
		}
		return "sha256:" + sha256(posting.title() + "|" + posting.datePosted());
	}

	private static URI pageUri(String identifier) {
		URI uri;
		try {
			uri = URI.create(identifier.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("JSON-LD source is not a valid URL: " + identifier, ex);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		boolean http = scheme.equals("https") || scheme.equals("http");
		if (!http || uri.getHost() == null) {
			throw new IllegalArgumentException("JSON-LD source must be an http(s) page URL: " + identifier);
		}
		return uri;
	}

	/** {@code jobLocation} is a Place or a list of Places. */
	private static List<Map<?, ?>> places(Object jobLocation) {
		if (jobLocation instanceof Map<?, ?> place) {
			return List.of(place);
		}
		if (jobLocation instanceof List<?> list) {
			return list.stream().filter(Map.class::isInstance).<Map<?, ?>>map(item -> (Map<?, ?>) item).toList();
		}
		return List.of();
	}

	private static String placeName(Map<?, ?> place) {
		if (!(place.get("address") instanceof Map<?, ?> address)) {
			return place.get("address") instanceof String text ? SourceValues.trimToNull(text) : null;
		}
		for (String key : List.of("addressLocality", "addressRegion")) {
			if (address.get(key) instanceof String text && SourceValues.trimToNull(text) != null) {
				return text.strip();
			}
		}
		return countryName(address.get("addressCountry"));
	}

	private static String countryCode(Map<?, ?> place) {
		if (place.get("address") instanceof Map<?, ?> address) {
			String country = countryName(address.get("addressCountry"));
			if (country != null && country.length() == 2) {
				return country.toUpperCase(Locale.ROOT);
			}
		}
		return null;
	}

	/** {@code addressCountry} is a country code, a name, or a Country object with a name. */
	private static String countryName(Object addressCountry) {
		if (addressCountry instanceof String text) {
			return SourceValues.trimToNull(text);
		}
		if (addressCountry instanceof Map<?, ?> country && country.get("name") instanceof String name) {
			return SourceValues.trimToNull(name);
		}
		return null;
	}

	/** {@code employmentType} is a single value or a list ("FULL_TIME", ["INTERN", "PART_TIME"]). */
	private static String joinValues(Object value) {
		if (value instanceof String text) {
			return SourceValues.trimToNull(text);
		}
		if (value instanceof List<?> list) {
			String joined = list.stream().filter(String.class::isInstance).map(String.class::cast)
				.map(SourceValues::trimToNull).filter(Objects::nonNull).collect(Collectors.joining(", "));
			return joined.isEmpty() ? null : joined;
		}
		return null;
	}

	private static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required by every Java runtime", ex);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record JsonLdPayload(String pageUrl, JobPostingLd jobPosting) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record JobPostingLd(
			@JsonProperty("@type") Object type,
			String title,
			String description,
			String datePosted,
			Object identifier,
			String url,
			Object jobLocation,
			String jobLocationType,
			Object employmentType) {
	}

}
