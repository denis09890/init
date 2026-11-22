package dev.init.source.smartrecruiters;

import java.net.URI;
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
import dev.init.source.SourceTarget;
import dev.init.source.SourceType;
import dev.init.source.http.SourceHttpClient;
import dev.init.source.support.HtmlText;
import dev.init.source.support.SourceJson;
import dev.init.source.support.SourceValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SmartRecruiters Posting API. The list endpoint ({@code GET /v1/companies/{id}/postings}, paged
 * with offset/limit, at most 100 per page) is filtered to Romania with {@code country=ro}; it carries
 * no description, so each posting's details are fetched as well and stored as the raw payload.
 *
 * @see <a href="https://developers.smartrecruiters.com/docs/posting-api">SmartRecruiters Posting API</a>
 */
@Component
public class SmartRecruitersConnector implements JobSourceConnector {

	static final String LIST_URL = "https://api.smartrecruiters.com/v1/companies/{companyId}/postings";

	static final String DETAIL_URL = "https://api.smartrecruiters.com/v1/companies/{companyId}/postings/{postingId}";

	/** Init covers Romania; companies like Bosch publish thousands of postings worldwide. */
	static final String COUNTRY = "ro";

	static final int DEFAULT_PAGE_SIZE = 100;

	static final int MAX_PAGES = 100;

	private static final String SOURCE = "SmartRecruiters";

	private final SourceHttpClient http;

	private final ObjectMapper mapper;

	private final int pageSize;

	@Autowired
	public SmartRecruitersConnector(SourceHttpClient http, ObjectMapper mapper) {
		this(http, mapper, DEFAULT_PAGE_SIZE);
	}

	SmartRecruitersConnector(SourceHttpClient http, ObjectMapper mapper, int pageSize) {
		this.http = http;
		this.mapper = mapper;
		this.pageSize = pageSize;
	}

	@Override
	public SourceType type() {
		return SourceType.SMARTRECRUITERS;
	}

	@Override
	public List<RawJob> fetch(SourceTarget target) {
		if (target.type() != type()) {
			throw new IllegalArgumentException("Not a SmartRecruiters source: " + target);
		}
		List<RawJob> rawJobs = new ArrayList<>();
		for (String postingId : listPostingIds(target.identifier())) {
			URI detailUri = UriComponentsBuilder.fromUriString(DETAIL_URL)
				.buildAndExpand(target.identifier(), postingId)
				.encode()
				.toUri();
			rawJobs.add(new RawJob(postingId, http.getJson(detailUri)));
		}
		return rawJobs;
	}

	private List<String> listPostingIds(String companyId) {
		List<String> ids = new ArrayList<>();
		for (int page = 0; page < MAX_PAGES; page++) {
			int offset = page * pageSize;
			URI uri = UriComponentsBuilder.fromUriString(LIST_URL)
				.queryParam("country", COUNTRY)
				.queryParam("offset", offset)
				.queryParam("limit", pageSize)
				.buildAndExpand(companyId)
				.encode()
				.toUri();
			JsonNode root = SourceJson.readTree(mapper, http.getJson(uri), SOURCE);
			SmartRecruitersPage meta = SourceJson.convert(mapper, root, SmartRecruitersPage.class, SOURCE);
			JsonNode content = root.path("content");
			if (!content.isArray()) {
				throw new SourceFetchException("SmartRecruiters response for '" + companyId + "' has no content array");
			}
			int count = 0;
			for (JsonNode item : content) {
				SmartRecruitersListItem listItem = SourceJson.convert(mapper, item, SmartRecruitersListItem.class, SOURCE);
				ids.add(SourceValues.requireText(listItem.id(), "id", SOURCE));
				count++;
			}
			if (count == 0 || offset + count >= meta.totalFound()) {
				return ids;
			}
		}
		throw new SourceFetchException("SmartRecruiters company '" + companyId + "' returned more than "
				+ MAX_PAGES + " pages; refusing to continue");
	}

	@Override
	public NormalizedJob normalize(RawJob job) {
		SmartRecruitersPosting posting = SourceJson.read(mapper, job.payload(), SmartRecruitersPosting.class, SOURCE);
		SmartRecruitersLocation location = posting.location();
		return new NormalizedJob(
				job.externalId(),
				SourceValues.requireText(posting.name(), "name", SOURCE),
				SourceValues.requireText(posting.postingUrl(), "postingUrl", SOURCE),
				locations(location),
				location == null || location.country() == null ? null : location.country().toUpperCase(Locale.ROOT),
				remoteType(location),
				description(posting.jobAd()),
				SourceValues.parseInstant(posting.releasedDate()),
				label(posting.function()),
				label(posting.typeOfEmployment()),
				label(posting.experienceLevel()));
	}

	private static List<String> locations(SmartRecruitersLocation location) {
		if (location == null) {
			return List.of();
		}
		String city = SourceValues.trimToNull(location.city());
		if (city != null) {
			return List.of(city);
		}
		String full = SourceValues.trimToNull(location.fullLocation());
		return full == null ? List.of() : List.of(full);
	}

	/** SmartRecruiters states remote and hybrid explicitly, so anything else is on site. */
	private static RemoteType remoteType(SmartRecruitersLocation location) {
		if (location == null) {
			return RemoteType.UNKNOWN;
		}
		if (Boolean.TRUE.equals(location.remote())) {
			return RemoteType.REMOTE;
		}
		if (Boolean.TRUE.equals(location.hybrid())) {
			return RemoteType.HYBRID;
		}
		return RemoteType.ONSITE;
	}

	/** The job itself: description, qualifications and additional information, not the company blurb. */
	private static String description(SmartRecruitersJobAd jobAd) {
		if (jobAd == null || jobAd.sections() == null) {
			return "";
		}
		SmartRecruitersSections sections = jobAd.sections();
		return Stream.of(sections.jobDescription(), sections.qualifications(), sections.additionalInformation())
			.filter(Objects::nonNull)
			.map(section -> HtmlText.toPlainText(section.text()))
			.filter(text -> !text.isEmpty())
			.collect(Collectors.joining(" "));
	}

	private static String label(SmartRecruitersLabel label) {
		return label == null ? null : SourceValues.trimToNull(label.label());
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersPage(int totalFound) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersListItem(String id) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersPosting(
			String id,
			String name,
			String postingUrl,
			SmartRecruitersLocation location,
			String releasedDate,
			SmartRecruitersJobAd jobAd,
			SmartRecruitersLabel function,
			SmartRecruitersLabel typeOfEmployment,
			SmartRecruitersLabel experienceLevel) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersLocation(String city, String region, String country, Boolean remote, Boolean hybrid,
			String fullLocation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersJobAd(SmartRecruitersSections sections) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersSections(
			SmartRecruitersSection companyDescription,
			SmartRecruitersSection jobDescription,
			SmartRecruitersSection qualifications,
			SmartRecruitersSection additionalInformation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersSection(String title, String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SmartRecruitersLabel(String id, String label) {
	}

}
