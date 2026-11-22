package dev.init.company;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.init.source.SourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the database match the company catalog. Idempotent: running it twice with the same catalog
 * changes nothing the second time.
 * <ul>
 * <li>Companies are matched by slug, sources by (type, identifier).</li>
 * <li>New entries are created; changed names, websites and regions are updated.</li>
 * <li>Entries missing from the catalog are deactivated, never deleted, so their postings and
 * ingestion history remain. They are reactivated if they come back.</li>
 * <li>A source cannot move to another company: its postings already reference the original one.</li>
 * </ul>
 */
@Service
public class CompanyCatalogSynchronizer {

	private final CompanyRepository companies;

	private final CompanySourceRepository sources;

	public CompanyCatalogSynchronizer(CompanyRepository companies, CompanySourceRepository sources) {
		this.companies = companies;
		this.sources = sources;
	}

	@Transactional
	public CatalogSyncResult synchronize(List<CompanyDefinition> catalog) {
		requireUniqueEntries(catalog);
		Counters counters = new Counters();
		Set<String> configuredSlugs = new HashSet<>();
		Set<SourceKey> configuredSources = new HashSet<>();

		for (CompanyDefinition definition : catalog) {
			Company company = synchronizeCompany(definition, counters);
			configuredSlugs.add(definition.slug());
			for (SourceDefinition sourceDefinition : definition.sources()) {
				synchronizeSource(company, sourceDefinition, counters);
				configuredSources.add(new SourceKey(sourceDefinition.type(), sourceDefinition.identifier()));
			}
		}

		for (CompanySource source : sources.findAll()) {
			if (!configuredSources.contains(SourceKey.of(source)) && source.deactivate()) {
				counters.sourcesDeactivated++;
			}
		}
		for (Company company : companies.findAll()) {
			if (!configuredSlugs.contains(company.getSlug()) && company.deactivate()) {
				counters.companiesDeactivated++;
			}
		}
		return counters.toResult();
	}

	private Company synchronizeCompany(CompanyDefinition definition, Counters counters) {
		Optional<Company> existing = companies.findBySlug(definition.slug());
		if (existing.isEmpty()) {
			counters.companiesCreated++;
			return companies.save(new Company(definition.name(), definition.slug(), definition.website()));
		}
		Company company = existing.get();
		boolean changed = company.updateDetails(definition.name(), definition.website());
		changed |= company.activate();
		if (changed) {
			counters.companiesUpdated++;
		}
		return company;
	}

	private void synchronizeSource(Company company, SourceDefinition definition, Counters counters) {
		Optional<CompanySource> existing = sources.findBySourceTypeAndIdentifier(definition.type(),
				definition.identifier());
		if (existing.isEmpty()) {
			sources.save(new CompanySource(company, definition.type(), definition.identifier(), definition.region()));
			counters.sourcesCreated++;
			return;
		}
		CompanySource source = existing.get();
		if (!source.getCompany().getId().equals(company.getId())) {
			throw new IllegalStateException("Source %s '%s' belongs to another company and cannot be moved to '%s'"
				.formatted(definition.type(), definition.identifier(), company.getSlug()));
		}
		boolean changed = source.updateRegion(definition.region());
		changed |= source.activate();
		if (changed) {
			counters.sourcesUpdated++;
		}
	}

	static void requireUniqueEntries(List<CompanyDefinition> catalog) {
		Set<String> slugs = new HashSet<>();
		Set<SourceKey> sourceKeys = new HashSet<>();
		for (CompanyDefinition definition : catalog) {
			if (!slugs.add(definition.slug())) {
				throw new IllegalArgumentException("Duplicate company slug in catalog: " + definition.slug());
			}
			for (SourceDefinition source : definition.sources()) {
				if (!sourceKeys.add(new SourceKey(source.type(), source.identifier()))) {
					throw new IllegalArgumentException(
							"Duplicate source in catalog: %s '%s'".formatted(source.type(), source.identifier()));
				}
			}
		}
	}

	private record SourceKey(SourceType type, String identifier) {

		static SourceKey of(CompanySource source) {
			return new SourceKey(source.getSourceType(), source.getIdentifier());
		}

	}

	private static final class Counters {

		int companiesCreated;

		int companiesUpdated;

		int companiesDeactivated;

		int sourcesCreated;

		int sourcesUpdated;

		int sourcesDeactivated;

		CatalogSyncResult toResult() {
			return new CatalogSyncResult(companiesCreated, companiesUpdated, companiesDeactivated, sourcesCreated,
					sourcesUpdated, sourcesDeactivated);
		}

	}

}
