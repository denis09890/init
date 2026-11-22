package dev.init.company;

/** What a catalog synchronization changed in the database. */
public record CatalogSyncResult(
		int companiesCreated,
		int companiesUpdated,
		int companiesDeactivated,
		int sourcesCreated,
		int sourcesUpdated,
		int sourcesDeactivated) {

	public static final CatalogSyncResult NO_CHANGES = new CatalogSyncResult(0, 0, 0, 0, 0, 0);

}
