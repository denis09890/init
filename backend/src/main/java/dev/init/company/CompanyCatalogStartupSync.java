package dev.init.company;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Synchronizes {@code companies.yml} into the database once the application has started. */
@Component
class CompanyCatalogStartupSync implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CompanyCatalogStartupSync.class);

	private final CompanyCatalogProperties properties;

	private final CompanyCatalogSynchronizer synchronizer;

	CompanyCatalogStartupSync(CompanyCatalogProperties properties, CompanyCatalogSynchronizer synchronizer) {
		this.properties = properties;
		this.synchronizer = synchronizer;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.syncOnStartup()) {
			log.info("Company catalog sync on startup is disabled");
			return;
		}
		CatalogSyncResult result = synchronizer.synchronize(properties.companies());
		long examples = properties.companies().stream().filter(CompanyDefinition::example).count();
		log.info("Company catalog synchronized ({} companies, {} marked as examples): {}",
				properties.companies().size(), examples, result);
	}

}
