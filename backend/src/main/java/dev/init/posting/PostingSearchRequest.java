package dev.init.posting;

import java.util.List;
import java.util.Locale;

import dev.init.classification.Domain;
import dev.init.classification.OpportunityType;
import dev.init.common.Diacritics;
import dev.init.source.RemoteType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters of {@code GET /api/postings}. Every parameter is optional.
 *
 * @param q full-text query over title and snippet; diacritics are ignored
 * @param domain one or more domains (repeat the parameter or separate with commas)
 * @param type one or more opportunity types
 * @param company company slug
 * @param city part of a location name, ignoring case and diacritics
 * @param remote workplace type
 * @param language {@code en} or {@code ro}
 * @param paid only postings that are (true) or are not (false) paid; postings that do not say are excluded
 * @param earlyCareer {@code true} by default: only postings classified as early career
 * @param romaniaOnly {@code true} by default: only postings in Romania or remote and open to it
 * @param page zero-based page number
 * @param size page size, at most {@value #MAX_SIZE}
 */
public record PostingSearchRequest(
		@Size(max = 200) String q,
		List<Domain> domain,
		List<OpportunityType> type,
		@Size(max = 100) String company,
		@Size(max = 100) String city,
		RemoteType remote,
		@Size(max = 8) String language,
		Boolean paid,
		Boolean earlyCareer,
		Boolean romaniaOnly,
		@Min(0) Integer page,
		@Min(1) @Max(PostingSearchRequest.MAX_SIZE) Integer size) {

	public static final int DEFAULT_SIZE = 20;

	public static final int MAX_SIZE = 100;

	public static PostingSearchRequest defaults() {
		return new PostingSearchRequest(null, null, null, null, null, null, null, null, null, null, null, null);
	}

	boolean earlyCareerOnly() {
		return earlyCareer == null || earlyCareer;
	}

	boolean romaniaOnlyFilter() {
		return romaniaOnly == null || romaniaOnly;
	}

	int pageNumber() {
		return page == null ? 0 : page;
	}

	int pageSize() {
		return size == null ? DEFAULT_SIZE : size;
	}

	List<Domain> domains() {
		return domain == null ? List.of() : domain;
	}

	List<OpportunityType> types() {
		return type == null ? List.of() : type;
	}

	/** The full-text query without diacritics, or null when blank. */
	String searchText() {
		return Diacritics.strip(blankToNull(q));
	}

	/** The city filter without diacritics and in lower case, or null when blank. */
	String cityText() {
		String text = Diacritics.strip(blankToNull(city));
		return text == null ? null : text.toLowerCase(Locale.ROOT);
	}

	String companySlug() {
		return blankToNull(company);
	}

	String languageCode() {
		String text = blankToNull(language);
		return text == null ? null : text.toLowerCase(Locale.ROOT);
	}

	private static String blankToNull(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return text.strip();
	}

}
