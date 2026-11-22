package dev.init.source;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A posting in the common shape every connector produces, before classification.
 *
 * @param locations location strings as the source wrote them (for example "Bucharest, Romania");
 * cleaning them into city names is a separate step
 * @param countryCode ISO 3166-1 alpha-2 code when the source provides one, otherwise null
 * @param description the full plain-text description; used for classification and for the
 * public snippet, never published in full
 * @param department team or job function as named by the source, nullable
 * @param employmentType for example "Full-time" or "Intern", nullable
 * @param experienceLevel for example "Internship" or "Entry Level", nullable
 */
public record NormalizedJob(
		String externalId,
		String title,
		String url,
		List<String> locations,
		String countryCode,
		RemoteType remoteType,
		String description,
		Instant postedAt,
		String department,
		String employmentType,
		String experienceLevel) {

	public NormalizedJob {
		Objects.requireNonNull(externalId, "externalId");
		Objects.requireNonNull(title, "title");
		Objects.requireNonNull(url, "url");
		locations = locations == null ? List.of() : List.copyOf(locations);
		remoteType = remoteType == null ? RemoteType.UNKNOWN : remoteType;
		description = description == null ? "" : description;
	}

}
