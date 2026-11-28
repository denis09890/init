package dev.init.ingestion;

import dev.init.classification.llm.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs an LLM pass every {@code init.llm.interval}, only when {@code init.llm.enabled=true}. */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "init.llm", name = "enabled", havingValue = "true")
class LlmReclassificationScheduler {

	private static final Logger log = LoggerFactory.getLogger(LlmReclassificationScheduler.class);

	private final LlmReclassifier reclassifier;

	private final LlmProperties properties;

	LlmReclassificationScheduler(LlmReclassifier reclassifier, LlmProperties properties) {
		this.reclassifier = reclassifier;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${init.llm.interval:PT3H}", initialDelayString = "${init.llm.initial-delay:PT5M}")
	void run() {
		try {
			reclassifier.run(properties.maxPerRun());
		}
		catch (RuntimeException ex) {
			log.error("LLM pass failed", ex);
		}
	}

}
