package dev.init.source.lever;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.init.source.JobSourceConnector;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.RemoteType;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceRegion;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import dev.init.source.http.SourceHttpClient;
import dev.init.source.support.HtmlText;
import dev.init.source.support.SourceJson;
import dev.init.source.support.SourceValues;
import dev.init.source.support.WorkplaceHints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lever Postings API: {@code GET /v0/postings/{site}?mode=json&skip=&limit=}, paged with skip/limit.
 * EU customers are served from a separate host, selected by the source region.
 *
 * @see <a href="https://github.com/lever/postings-api">Lever Postings API</a>
 */
@Component
public class LeverConnector implements JobSourceConnector {

	static final String GLOBAL_URL = "https://api.lever.co/v0/postings/{site}";

	static final String EU_URL = "https://api.eu.lever.co/v0/postings/{site}";

	static final int DEFAULT_PAGE_SIZE = 100;

	/** Guards against a source that never returns a short page. */
	static final int MAX_PAGES = 100;

	private static final String SOURCE = "Lever";

	private final SourceHttpClient http;

	private final ObjectMapper mapper;

	private final int pageSize;

	@Autowired
	public LeverConnector(SourceHttpClient http, ObjectMapper mapper) {
		this(http, mapper, DEFAULT_PAGE_SIZE);
	}

	LeverConnector(SourceHttpClient http, ObjectMapper mapper, int pageSize) {
		this.http = http;
		this.mapper = mapper;
		this.pageSize = pageSize;
	}

	@Override
	public SourceType type() {
		return SourceType.LEVER;
	}

	@Override
	public List<RawJob> fetch(SourceTarget target) {
		if (target.type() != type()) {
			throw new IllegalArgumentException("Not a Lever source: " + target);
		}
		String baseUrl = target.region() == SourceRegion.EU ? EU_URL : GLOBAL_URL;
		List<RawJob> rawJobs = new ArrayList<>();
		for (int page = 0; page < MAX_PAGES; page++) {
			URI uri = UriComponentsBuilder.fromUriString(baseUrl)
				.queryParam("mode", "json")
				.queryParam("skip", page * pageSize)
				.queryParam("limit", pageSize)
				.buildAndExpand(target.identifier())
				.encode()
				.toUri();
			JsonNode postings = SourceJson.readTree(mapper, http.getJson(uri), SOURCE);
			if (!postings.isArray()) {
				throw new SourceFetchException("Lever response for site '" + target.identifier() + "' is not an array");
			}
			int count = 0;
			for (JsonNode posting : postings) {
				LeverPosting parsed = SourceJson.convert(mapper, posting, LeverPosting.class, SOURCE);
				rawJobs.add(new RawJob(SourceValues.requireText(parsed.id(), "id", SOURCE),
						SourceJson.write(mapper, posting)));
				count++;
			}
			if (count < pageSize) {
				return rawJobs;
			}
		}
		throw new SourceFetchException("Lever site '" + target.identifier() + "' returned more than "
				+ MAX_PAGES + " pages; refusing to continue");
	}

	@Override
	public NormalizedJob normalize(RawJob job) {
		LeverPosting parsed = SourceJson.read(mapper, job.payload(), LeverPosting.class, SOURCE);
		LeverCategories categories = parsed.categories() == null ? LeverCategories.EMPTY : parsed.categories();
		List<String> locations = categories.allLocations() != null && !categories.allLocations().isEmpty()
				? categories.allLocations().stream().map(SourceValues::trimToNull).filter(Objects::nonNull).toList()
				: SourceValues.splitList(categories.location(), ";");
		return new NormalizedJob(
				job.externalId(),
				SourceValues.requireText(parsed.text(), "text", SOURCE),
				SourceValues.requireText(parsed.hostedUrl(), "hostedUrl", SOURCE),
				locations,
				countryCode(parsed.country()),
				remoteType(parsed.workplaceType(), categories.location()),
				description(parsed),
				parsed.createdAt() == null ? null : Instant.ofEpochMilli(parsed.createdAt()),
				SourceValues.trimToNull(
						categories.department() != null ? categories.department() : categories.team()),
				SourceValues.trimToNull(categories.commitment()),
				SourceValues.trimToNull(categories.level()));
	}

	/** Lever documents "on-site", but live responses use "onsite"; accept both. */
	private static RemoteType remoteType(String workplaceType, String location) {
		String value = workplaceType == null ? "" : workplaceType.toLowerCase(Locale.ROOT);
		return switch (value) {
			case "remote" -> RemoteType.REMOTE;
			case "hybrid" -> RemoteType.HYBRID;
			case "onsite", "on-site" -> RemoteType.ONSITE;
			default -> WorkplaceHints.fromText(location);
		};
	}

	private static String countryCode(String country) {
		String code = SourceValues.trimToNull(country);
		return code == null ? null : code.toUpperCase(Locale.ROOT);
	}

	/** Main description, then each titled list ("Requirements: ..."), then the closing text. */
	private static String description(LeverPosting posting) {
		// List titles already end with a colon ("Skill Set:"), so title and items are joined with a space.
		Stream<String> lists = posting.lists() == null ? Stream.empty()
				: posting.lists().stream().map(list -> joinNonBlank(" ", list.text(), HtmlText.toPlainText(list.content())));
		return Stream.of(Stream.of(posting.descriptionPlain()), lists, Stream.of(posting.additionalPlain()))
			.flatMap(stream -> stream)
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.map(text -> text.replaceAll("\\s+", " "))
			.collect(Collectors.joining(" "));
	}

	private static String joinNonBlank(String separator, String... parts) {
		return Stream.of(parts).map(SourceValues::trimToNull).filter(Objects::nonNull)
			.collect(Collectors.joining(separator));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeverPosting(
			String id,
			String text,
			String hostedUrl,
			LeverCategories categories,
			String country,
			String workplaceType,
			Long createdAt,
			String descriptionPlain,
			String additionalPlain,
			List<LeverList> lists) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeverCategories(
			String location,
			List<String> allLocations,
			String department,
			String team,
			String commitment,
			String level) {

		static final LeverCategories EMPTY = new LeverCategories(null, null, null, null, null, null);

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeverList(String text, String content) {
	}

}
