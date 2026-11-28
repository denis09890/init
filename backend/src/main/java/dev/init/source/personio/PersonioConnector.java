package dev.init.source.personio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Personio public job feed: {@code GET https://{company}.jobs.personio.de/xml} returns every published
 * position of a company in one XML document ({@code <workzag-jobs>} with one {@code <position>} each).
 *
 * <p>{@code raw_postings} stores JSON, so each position is converted to a JSON object with the same
 * element names: simple elements become strings, {@code additionalOffices} a list of office names,
 * {@code jobDescriptions} a list of {@code {name, value}} sections, other nested elements an object.
 * The feed has no link per position, so {@code jobUrl} is added from the documented pattern
 * {@code https://{company}.jobs.personio.de/job/{id}}.
 *
 * <p>The parser refuses DTDs, so a hostile feed cannot use external entities (XXE).
 *
 * @see <a href="https://developer.personio.de/docs/retrieving-open-job-positions">Retrieving open job positions</a>
 * @see <a href="https://developer.personio.de/docs/integration-of-open-positions">Integration of open positions</a>
 */
@Component
public class PersonioConnector implements JobSourceConnector {

	static final String FEED_HOST_SUFFIX = ".jobs.personio.de";

	private static final String SOURCE = "Personio";

	private static final String ROOT_ELEMENT = "workzag-jobs";

	/** The identifier becomes a host name, so only a single DNS label is accepted. */
	private static final Pattern COMPANY = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

	/** Older feeds write the offset without a colon ("2016-05-31T12:14:07+0200"). */
	private static final DateTimeFormatter OFFSET_WITHOUT_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx");

	private final SourceHttpClient http;

	private final ObjectMapper mapper;

	public PersonioConnector(SourceHttpClient http, ObjectMapper mapper) {
		this.http = http;
		this.mapper = mapper;
	}

	@Override
	public SourceType type() {
		return SourceType.PERSONIO;
	}

	@Override
	public List<RawJob> fetch(SourceTarget target) {
		if (target.type() != type()) {
			throw new IllegalArgumentException("Not a Personio source: " + target);
		}
		String company = company(target.identifier());
		Element root = parse(http.getXml(URI.create("https://" + company + FEED_HOST_SUFFIX + "/xml")))
			.getDocumentElement();
		if (!ROOT_ELEMENT.equals(root.getTagName())) {
			throw new SourceFetchException("Personio feed for '" + company + "' has an unexpected root element <"
					+ root.getTagName() + ">");
		}
		List<RawJob> rawJobs = new ArrayList<>();
		for (Element position : childElements(root)) {
			if (!"position".equals(position.getTagName())) {
				continue;
			}
			Map<String, Object> json = toJson(position);
			String id = SourceValues.requireText(json.get("id") instanceof String text ? text : null, "id", SOURCE);
			json.put("jobUrl", UriComponentsBuilder.fromUriString("https://" + company + FEED_HOST_SUFFIX + "/job/{id}")
				.buildAndExpand(id)
				.encode()
				.toUriString());
			rawJobs.add(new RawJob(id, SourceJson.write(mapper, json)));
		}
		return rawJobs;
	}

	@Override
	public NormalizedJob normalize(RawJob job) {
		PersonioPosition position = SourceJson.read(mapper, job.payload(), PersonioPosition.class, SOURCE);
		Stream<String> additional = position.additionalOffices() == null ? Stream.empty()
				: position.additionalOffices().stream();
		List<String> offices = Stream.concat(Stream.of(position.office()), additional)
			.map(SourceValues::trimToNull)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		String title = SourceValues.requireText(position.name(), "name", SOURCE);
		List<String> hints = new ArrayList<>(offices);
		hints.add(title);
		return new NormalizedJob(
				job.externalId(),
				title,
				SourceValues.requireText(position.jobUrl(), "jobUrl", SOURCE),
				offices,
				null,
				WorkplaceHints.fromText(hints.toArray(String[]::new)),
				description(position),
				parseInstant(position.createdAt()),
				SourceValues.trimToNull(position.department()) != null ? SourceValues.trimToNull(position.department())
						: SourceValues.trimToNull(position.recruitingCategory()),
				SourceValues.trimToNull(position.employmentType()),
				SourceValues.trimToNull(position.seniority()));
	}

	private static String company(String identifier) {
		String company = identifier.strip().toLowerCase(Locale.ROOT);
		if (!COMPANY.matcher(company).matches()) {
			throw new SourceFetchException("Personio company '" + identifier + "' is not a valid subdomain");
		}
		return company;
	}

	/** Each section as "heading text", in feed order. */
	private static String description(PersonioPosition position) {
		if (position.jobDescriptions() == null) {
			return "";
		}
		return position.jobDescriptions()
			.stream()
			.filter(Objects::nonNull)
			.map(section -> Stream.of(SourceValues.trimToNull(section.name()), HtmlText.toPlainText(section.value()))
				.filter(text -> text != null && !text.isEmpty())
				.collect(Collectors.joining(" ")))
			.filter(text -> !text.isEmpty())
			.collect(Collectors.joining(" "));
	}

	private static Instant parseInstant(String value) {
		Instant instant = SourceValues.parseInstant(value);
		if (instant != null || SourceValues.trimToNull(value) == null) {
			return instant;
		}
		try {
			return OffsetDateTime.parse(value.strip(), OFFSET_WITHOUT_COLON).toInstant();
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	// ----- XML ------------------------------------------------------------------------------------

	private static Document parse(byte[] xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			// Throws on fatal errors instead of printing them to stderr.
			builder.setErrorHandler(new DefaultHandler());
			return builder.parse(new ByteArrayInputStream(xml));
		}
		catch (ParserConfigurationException ex) {
			throw new IllegalStateException("The JDK XML parser does not support secure processing", ex);
		}
		catch (SAXException | IOException ex) {
			throw new SourceFetchException("Malformed XML from " + SOURCE + ": " + ex.getMessage(), ex);
		}
	}

	private static Map<String, Object> toJson(Element position) {
		Map<String, Object> json = new LinkedHashMap<>();
		for (Element child : childElements(position)) {
			String name = child.getTagName();
			switch (name) {
				case "additionalOffices" -> json.put(name, childElements(child).stream()
					.map(PersonioConnector::text)
					.filter(Objects::nonNull)
					.toList());
				case "jobDescriptions" -> json.put(name, childElements(child).stream()
					.map(PersonioConnector::section)
					.toList());
				default -> json.put(name, childElements(child).isEmpty() ? text(child) : simpleObject(child));
			}
		}
		return json;
	}

	private static Map<String, Object> section(Element jobDescription) {
		Map<String, Object> section = new LinkedHashMap<>();
		for (Element part : childElements(jobDescription)) {
			section.put(part.getTagName(), text(part));
		}
		return section;
	}

	private static Map<String, Object> simpleObject(Element element) {
		Map<String, Object> object = new LinkedHashMap<>();
		for (Element child : childElements(element)) {
			object.put(child.getTagName(), text(child));
		}
		return object;
	}

	private static String text(Element element) {
		return SourceValues.trimToNull(element.getTextContent());
	}

	private static List<Element> childElements(Element parent) {
		List<Element> elements = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element) {
				elements.add(element);
			}
		}
		return elements;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PersonioPosition(
			String id,
			String name,
			String office,
			List<String> additionalOffices,
			String department,
			String recruitingCategory,
			List<PersonioSection> jobDescriptions,
			String employmentType,
			String seniority,
			String createdAt,
			String jobUrl) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PersonioSection(String name, String value) {
	}

}
