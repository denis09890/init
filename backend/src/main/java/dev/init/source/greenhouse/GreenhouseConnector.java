package dev.init.source.greenhouse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.init.source.JobSourceConnector;
import dev.init.source.NormalizedJob;
import dev.init.source.RawJob;
import dev.init.source.SourceFetchException;
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import dev.init.source.http.SourceHttpClient;
import dev.init.source.support.HtmlText;
import dev.init.source.support.SourceJson;
import dev.init.source.support.SourceValues;
import dev.init.source.support.WorkplaceHints;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Greenhouse Job Board API: {@code GET /v1/boards/{board_token}/jobs?content=true} returns every
 * published job of a board in one response, without pagination.
 *
 * @see <a href="https://developers.greenhouse.io/job-board.html">Greenhouse Job Board API</a>
 */
@Component
public class GreenhouseConnector implements JobSourceConnector {

	static final String JOBS_URL = "https://boards-api.greenhouse.io/v1/boards/{boardToken}/jobs";

	private static final String SOURCE = "Greenhouse";

	private final SourceHttpClient http;

	private final ObjectMapper mapper;

	public GreenhouseConnector(SourceHttpClient http, ObjectMapper mapper) {
		this.http = http;
		this.mapper = mapper;
	}

	@Override
	public SourceType type() {
		return SourceType.GREENHOUSE;
	}

	@Override
	public List<RawJob> fetch(SourceTarget target) {
		requireGreenhouse(target);
		URI uri = UriComponentsBuilder.fromUriString(JOBS_URL)
			.queryParam("content", true)
			.buildAndExpand(target.identifier())
			.encode()
			.toUri();
		JsonNode jobs = SourceJson.readTree(mapper, http.getJson(uri), SOURCE).path("jobs");
		if (!jobs.isArray()) {
			throw new SourceFetchException("Greenhouse response for board '" + target.identifier() + "' has no jobs array");
		}
		List<RawJob> rawJobs = new ArrayList<>();
		for (JsonNode job : jobs) {
			GreenhouseJob parsed = SourceJson.convert(mapper, job, GreenhouseJob.class, SOURCE);
			if (parsed.id() == null) {
				throw new SourceFetchException("Greenhouse job without id on board '" + target.identifier() + "'");
			}
			rawJobs.add(new RawJob(String.valueOf(parsed.id()), SourceJson.write(mapper, job)));
		}
		return rawJobs;
	}

	@Override
	public NormalizedJob normalize(RawJob job) {
		GreenhouseJob parsed = SourceJson.read(mapper, job.payload(), GreenhouseJob.class, SOURCE);
		String locationName = parsed.location() == null ? null : parsed.location().name();
		return new NormalizedJob(
				job.externalId(),
				SourceValues.requireText(parsed.title(), "title", SOURCE),
				SourceValues.requireText(parsed.absoluteUrl(), "absolute_url", SOURCE),
				SourceValues.splitList(locationName, ";"),
				null,
				WorkplaceHints.fromText(locationName, metadataValue(parsed, "work type").orElse(null)),
				HtmlText.fromEscapedHtml(parsed.content()),
				Optional.ofNullable(SourceValues.parseInstant(parsed.firstPublished()))
					.orElseGet(() -> SourceValues.parseInstant(parsed.updatedAt())),
				firstDepartment(parsed).or(() -> metadataValue(parsed, "external department name")).orElse(null),
				metadataValue(parsed, "employment type").orElse(null),
				null);
	}

	private static Optional<String> firstDepartment(GreenhouseJob job) {
		if (job.departments() == null) {
			return Optional.empty();
		}
		return job.departments()
			.stream()
			.map(GreenhouseDepartment::name)
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.findFirst();
	}

	/** Custom fields such as "Employment Type"; values can be strings, lists or null. */
	private static Optional<String> metadataValue(GreenhouseJob job, String fieldName) {
		if (job.metadata() == null) {
			return Optional.empty();
		}
		return job.metadata().stream()
			.filter(field -> field.name() != null && field.name().toLowerCase(Locale.ROOT).equals(fieldName))
			.map(GreenhouseMetadata::value)
			.filter(String.class::isInstance)
			.map(String.class::cast)
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.findFirst();
	}

	private void requireGreenhouse(SourceTarget target) {
		if (target.type() != type()) {
			throw new IllegalArgumentException("Not a Greenhouse source: " + target);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseJob(
			Long id,
			String title,
			@JsonProperty("absolute_url") String absoluteUrl,
			GreenhouseLocation location,
			String content,
			@JsonProperty("first_published") String firstPublished,
			@JsonProperty("updated_at") String updatedAt,
			List<GreenhouseMetadata> metadata,
			List<GreenhouseDepartment> departments) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseLocation(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseMetadata(String name, Object value) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record GreenhouseDepartment(String name) {
	}

}
