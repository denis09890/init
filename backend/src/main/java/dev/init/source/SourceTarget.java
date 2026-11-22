package dev.init.source;

import java.util.Objects;

/**
 * What a connector fetches: one board, company or career page on one platform. Kept free of
 * persistence types so connectors stay independent of the database.
 *
 * @param identifier board token, company id or career page URL, depending on the type
 */
public record SourceTarget(SourceType type, String identifier, SourceRegion region) {

	public SourceTarget {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(identifier, "identifier");
		if (region == null) {
			region = SourceRegion.GLOBAL;
		}
	}

	public static SourceTarget of(SourceType type, String identifier) {
		return new SourceTarget(type, identifier, SourceRegion.GLOBAL);
	}

}
