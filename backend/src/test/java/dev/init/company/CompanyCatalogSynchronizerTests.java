package dev.init.company;

import java.util.List;

import dev.init.IntegrationTest;
import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class CompanyCatalogSynchronizerTests {

	@Autowired
	private CompanyCatalogSynchronizer synchronizer;

	@Autowired
	private CompanyRepository companies;

	@Autowired
	private CompanySourceRepository sources;

	@Autowired
	private CompanyCatalogProperties bundledCatalog;

	@Test
	void createsCompaniesAndSources() {
		CatalogSyncResult result = synchronizer.synchronize(List.of(
				company("acme", "Acme", source(SourceType.GREENHOUSE, "acme")),
				company("globex", "Globex", new SourceDefinition(SourceType.LEVER, "globex", SourceRegion.EU))));

		assertThat(result).isEqualTo(new CatalogSyncResult(2, 0, 0, 2, 0, 0));
		assertThat(companies.findBySlug("acme")).hasValueSatisfying(company -> {
			assertThat(company.getName()).isEqualTo("Acme");
			assertThat(company.isActive()).isTrue();
		});
		assertThat(sources.findBySourceTypeAndIdentifier(SourceType.LEVER, "globex"))
			.hasValueSatisfying(source -> assertThat(source.getRegion()).isEqualTo(SourceRegion.EU));
	}

	@Test
	void secondRunWithSameCatalogChangesNothing() {
		List<CompanyDefinition> catalog = List.of(company("acme", "Acme", source(SourceType.GREENHOUSE, "acme")));
		synchronizer.synchronize(catalog);

		assertThat(synchronizer.synchronize(catalog)).isEqualTo(CatalogSyncResult.NO_CHANGES);
		assertThat(companies.count()).isEqualTo(1);
		assertThat(sources.count()).isEqualTo(1);
	}

	@Test
	void updatesChangedCompanyDetailsAndSourceRegion() {
		synchronizer.synchronize(List.of(company("acme", "Acme", source(SourceType.LEVER, "acme"))));

		CatalogSyncResult result = synchronizer.synchronize(List.of(company("acme", "Acme Corp",
				new SourceDefinition(SourceType.LEVER, "acme", SourceRegion.EU))));

		assertThat(result).isEqualTo(new CatalogSyncResult(0, 1, 0, 0, 1, 0));
		assertThat(companies.findBySlug("acme").orElseThrow().getName()).isEqualTo("Acme Corp");
		assertThat(sources.findBySourceTypeAndIdentifier(SourceType.LEVER, "acme").orElseThrow().getRegion())
			.isEqualTo(SourceRegion.EU);
	}

	@Test
	void deactivatesEntriesRemovedFromCatalogAndReactivatesThemWhenTheyReturn() {
		List<CompanyDefinition> full = List.of(
				company("acme", "Acme", source(SourceType.GREENHOUSE, "acme"),
						source(SourceType.SMARTRECRUITERS, "Acme")),
				company("globex", "Globex", source(SourceType.GREENHOUSE, "globex")));
		synchronizer.synchronize(full);

		CatalogSyncResult removal = synchronizer
			.synchronize(List.of(company("acme", "Acme", source(SourceType.GREENHOUSE, "acme"))));

		assertThat(removal).isEqualTo(new CatalogSyncResult(0, 0, 1, 0, 0, 2));
		assertThat(companies.findBySlug("globex").orElseThrow().isActive()).isFalse();
		assertThat(sources.findBySourceTypeAndIdentifier(SourceType.SMARTRECRUITERS, "Acme").orElseThrow().isActive())
			.isFalse();
		assertThat(sources.findByActiveTrue()).extracting(CompanySource::getIdentifier).containsExactly("acme");

		CatalogSyncResult restore = synchronizer.synchronize(full);

		assertThat(restore).isEqualTo(new CatalogSyncResult(0, 1, 0, 0, 2, 0));
		assertThat(companies.findBySlug("globex").orElseThrow().isActive()).isTrue();
		assertThat(sources.findByActiveTrue()).hasSize(3);
	}

	@Test
	void rejectsDuplicateSlugs() {
		List<CompanyDefinition> catalog = List.of(company("acme", "Acme", source(SourceType.GREENHOUSE, "acme")),
				company("acme", "Acme Again", source(SourceType.LEVER, "acme")));

		assertThatThrownBy(() -> synchronizer.synchronize(catalog)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Duplicate company slug")
			.hasMessageContaining("acme");
	}

	@Test
	void rejectsSameSourceConfiguredTwice() {
		List<CompanyDefinition> catalog = List.of(company("acme", "Acme", source(SourceType.GREENHOUSE, "shared")),
				company("globex", "Globex", source(SourceType.GREENHOUSE, "shared")));

		assertThatThrownBy(() -> synchronizer.synchronize(catalog)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Duplicate source")
			.hasMessageContaining("shared");
	}

	@Test
	void refusesToMoveSourceToAnotherCompany() {
		synchronizer.synchronize(List.of(company("acme", "Acme", source(SourceType.GREENHOUSE, "shared"))));

		assertThatThrownBy(() -> synchronizer
			.synchronize(List.of(company("globex", "Globex", source(SourceType.GREENHOUSE, "shared")))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("cannot be moved");
	}

	@Test
	void bundledCatalogContainsOnlyVerifiedExamplesAndSynchronizes() {
		assertThat(bundledCatalog.syncOnStartup()).as("disabled in the test profile").isFalse();
		assertThat(bundledCatalog.companies()).extracting(CompanyDefinition::slug)
			.containsExactlyInAnyOrder("bitdefender", "cresta", "bosch");
		assertThat(bundledCatalog.companies()).allMatch(CompanyDefinition::example);

		CatalogSyncResult result = synchronizer.synchronize(bundledCatalog.companies());

		assertThat(result).isEqualTo(new CatalogSyncResult(3, 0, 0, 3, 0, 0));
		assertThat(sources.findBySourceTypeAndIdentifier(SourceType.SMARTRECRUITERS, "BoschGroup")).isPresent();
	}

	private static CompanyDefinition company(String slug, String name, SourceDefinition... sources) {
		return new CompanyDefinition(name, slug, "https://" + slug + ".example", false, List.of(sources));
	}

	private static SourceDefinition source(SourceType type, String identifier) {
		return new SourceDefinition(type, identifier, null);
	}

}
