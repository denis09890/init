package dev.init.company;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

/**
 * One company entry of {@code companies.yml}.
 *
 * @param example {@code true} for entries added as verified examples rather than from the curated list
 */
public record CompanyDefinition(
		@NotBlank String name,
		@NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
				message = "must be lowercase letters and digits separated by single hyphens") String slug,
		@Pattern(regexp = "https://\\S+", message = "must be an https URL") String website,
		boolean example,
		@NotEmpty List<@Valid SourceDefinition> sources) {

}
