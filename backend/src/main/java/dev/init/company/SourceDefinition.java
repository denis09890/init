package dev.init.company;

import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** One source entry of a company in {@code companies.yml}. The region defaults to {@code GLOBAL}. */
public record SourceDefinition(@NotNull SourceType type, @NotBlank String identifier, SourceRegion region) {

	public SourceDefinition {
		if (region == null) {
			region = SourceRegion.GLOBAL;
		}
	}

}
