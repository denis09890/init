package dev.init.ingestion;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Ingestion settings.
 *
 * @param schedulerEnabled whether ingestion runs on a schedule; switched off in tests
 * @param interval time between the end of one scheduled run and the start of the next
 * @param initialDelay wait after startup before the first scheduled run
 * @param maxParallelSources sources ingested at the same time; keep it below the database pool size
 */
@ConfigurationProperties(prefix = "init.ingestion")
@Validated
public record IngestionProperties(
		@DefaultValue("true") boolean schedulerEnabled,
		@DefaultValue("PT6H") Duration interval,
		@DefaultValue("PT1M") Duration initialDelay,
		@DefaultValue("4") @Min(1) int maxParallelSources) {

}
