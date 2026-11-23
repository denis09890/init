package dev.init.ingestion;

/**
 * What storing a batch changed. A posting that reappears after being closed counts as updated.
 */
public record SyncResult(int created, int updated, int closed) {

}
