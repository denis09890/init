package dev.init.source;

/**
 * Regional instance of an ATS API. Some platforms (for example Lever) host EU customers on a
 * separate API host; most sources only use {@link #GLOBAL}.
 */
public enum SourceRegion {
	GLOBAL,
	EU
}
