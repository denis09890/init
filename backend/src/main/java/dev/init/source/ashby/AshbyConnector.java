package dev.init.source.ashby;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

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
import dev.init.source.support.WorkplaceHints;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ashby public job posting API: {@code GET /posting-api/job-board/{board}} returns every published job
 * of a board in one response, without pagination. The board name is the last path segment of the
 * board's page ({@code jobs.ashbyhq.com/{board}}). Jobs with {@code isListed: false} are meant to be
 * reached by direct link only, so they are skipped.
 *
 * @see <a href="https://developers.ashbyhq.com/docs/public-job-posting-api">Ashby public job posting API</a>
 */
@Component
public class AshbyConnector implements JobSourceConnector {

	static final String JOBS_URL = "https://api.ashbyhq.com/posting-api/job-board/{board}";

	private static final String SOURCE = "Ashby";

	private final SourceHttpClient http;

	private final ObjectMapper mapper;

	public AshbyConnector(SourceHttpClient http, ObjectMapper mapper) {
		this.http = http;
		this.mapper = mapper;
	}

	@Override
	public SourceType type() {
		return SourceType.ASHBY;
	}

	@Override
	public List<RawJob> fetch(SourceTarget target) {
		if (target.type() != type()) {
			throw new IllegalArgumentException("Not an Ashby source: " + target);
		}
		URI uri = UriComponentsBuilder.fromUriString(JOBS_URL).buildAndExpand(target.identifier()).encode().toUri();
		JsonNode jobs = SourceJson.readTree(mapper, http.getJson(uri), SOURCE).path("jobs");
		if (!jobs.isArray()) {
			throw new SourceFetchException("Ashby response for board '" + target.identifier() + "' has no jobs array");
		}
		List<RawJob> rawJobs = new ArrayList<>();
		for (JsonNode job : jobs) {
			AshbyJob parsed = SourceJson.convert(mapper, job, AshbyJob.class, SOURCE);
			if (Boolean.FALSE.equals(parsed.listed())) {
				continue;
			}
			rawJobs.add(new RawJob(SourceValues.requireText(parsed.id(), "id", SOURCE), SourceJson.write(mapper, job)));
		}
		return rawJobs;
	}

	@Override
	public NormalizedJob normalize(RawJob job) {
		AshbyJob parsed = SourceJson.read(mapper, job.payload(), AshbyJob.class, SOURCE);
		return new NormalizedJob(
				job.externalId(),
				SourceValues.requireText(parsed.title(), "title", SOURCE),
				SourceValues.requireText(parsed.jobUrl(), "jobUrl", SOURCE),
				locations(parsed),
				countryCode(parsed.address()),
				remoteType(parsed),
				description(parsed),
				SourceValues.parseInstant(parsed.publishedAt()),
				SourceValues.trimToNull(parsed.department()) != null ? SourceValues.trimToNull(parsed.department())
						: SourceValues.trimToNull(parsed.team()),
				SourceValues.trimToNull(parsed.employmentType()),
				null);
	}

	/**
	 * The primary location, then the secondary ones (other offices, or countries a remote role is open
	 * to). Without any location name, the city and country of the address are used.
	 */
	private static List<String> locations(AshbyJob job) {
		Stream<String> secondary = job.secondaryLocations() == null ? Stream.empty()
				: job.secondaryLocations().stream().filter(Objects::nonNull).map(AshbySecondaryLocation::location);
		List<String> names = Stream.concat(Stream.of(job.location()), secondary)
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		if (!names.isEmpty() || job.address() == null || job.address().postalAddress() == null) {
			return names;
		}
		AshbyPostalAddress address = job.address().postalAddress();
		return Stream.of(address.addressLocality(), address.addressCountry())
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.toList();
	}

	/** Ashby gives the country as a name ("Romania", "European Union"); only Romania matters to Init. */
	private static String countryCode(AshbyAddress address) {
		String country = address == null || address.postalAddress() == null ? null
				: SourceValues.trimToNull(address.postalAddress().addressCountry());
		if (country == null) {
			return null;
		}
		return switch (country.toLowerCase(Locale.ROOT)) {
			case "romania", "românia", "ro" -> "RO";
			default -> null;
		};
	}

	/** Documented values are "OnSite", "Remote" and "Hybrid"; {@code isRemote} is true for the last two. */
	private static RemoteType remoteType(AshbyJob job) {
		String workplace = job.workplaceType() == null ? "" : job.workplaceType().toLowerCase(Locale.ROOT);
		return switch (workplace) {
			case "remote" -> RemoteType.REMOTE;
			case "hybrid" -> RemoteType.HYBRID;
			case "onsite", "on-site" -> RemoteType.ONSITE;
			default -> Boolean.TRUE.equals(job.remote()) ? RemoteType.REMOTE : WorkplaceHints.fromText(job.location());
		};
	}

	private static String description(AshbyJob job) {
		String plain = SourceValues.trimToNull(job.descriptionPlain());
		if (plain != null) {
			return plain.replaceAll("\\s+", " ");
		}
		return HtmlText.toPlainText(job.descriptionHtml());
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbyJob(
			String id,
			String title,
			String department,
			String team,
			String employmentType,
			String location,
			List<AshbySecondaryLocation> secondaryLocations,
			@JsonProperty("isRemote") Boolean remote,
			String workplaceType,
			AshbyAddress address,
			String publishedAt,
			@JsonProperty("isListed") Boolean listed,
			String jobUrl,
			String descriptionHtml,
			String descriptionPlain) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbySecondaryLocation(String location, AshbyAddress address) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbyAddress(AshbyPostalAddress postalAddress) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AshbyPostalAddress(String addressLocality, String addressRegion, String addressCountry) {
	}

}
