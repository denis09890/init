package dev.init.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts an ingestion every {@code init.ingestion.interval}. Uses a fixed delay, so a slow run
 * never overlaps the next one. Disabled with {@code init.ingestion.scheduler-enabled=false}.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "init.ingestion", name = "scheduler-enabled", havingValue = "true",
		matchIfMissing = true)
class IngestionScheduler {

	private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

	private final IngestionOrchestrator orchestrator;

	IngestionScheduler(IngestionOrchestrator orchestrator) {
		this.orchestrator = orchestrator;
	}

	@Scheduled(fixedDelayString = "${init.ingestion.interval:PT6H}",
			initialDelayString = "${init.ingestion.initial-delay:PT1M}")
	void run() {
		try {
			orchestrator.runAll();
		}
		catch (IngestionAlreadyRunningException ex) {
			log.info("Scheduled ingestion skipped: {}", ex.getMessage());
		}
		catch (RuntimeException ex) {
			// a scheduled task that throws is not rescheduled by every scheduler; keep the schedule alive
			log.error("Scheduled ingestion failed", ex);
		}
	}

}
