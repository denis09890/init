package dev.init.ingestion;

/** An ingestion was requested while another one is still in progress. */
public class IngestionAlreadyRunningException extends RuntimeException {

	public IngestionAlreadyRunningException() {
		super("An ingestion is already running");
	}

}
