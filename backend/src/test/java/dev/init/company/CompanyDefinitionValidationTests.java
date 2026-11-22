package dev.init.company;

import java.util.List;
import java.util.Set;

import dev.init.source.SourceRegion;
import dev.init.source.SourceType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean Validation rules for catalog entries (the same rules Spring applies when binding companies.yml). */
class CompanyDefinitionValidationTests {

	private static ValidatorFactory validatorFactory;

	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		validatorFactory = Validation.buildDefaultValidatorFactory();
		validator = validatorFactory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		validatorFactory.close();
	}

	@Test
	void acceptsValidDefinition() {
		assertThat(validator.validate(company("acme-labs", "https://acme.example", greenhouse("acme")))).isEmpty();
	}

	@Test
	void acceptsMissingWebsite() {
		assertThat(validator.validate(company("acme", null, greenhouse("acme")))).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Acme", "acme labs", "-acme", "acme-", "acme--labs", "acme_labs", "" })
	void rejectsInvalidSlug(String slug) {
		assertThat(violatedFields(company(slug, null, greenhouse("acme")))).contains("slug");
	}

	@Test
	void rejectsBlankName() {
		CompanyDefinition definition = new CompanyDefinition(" ", "acme", null, false, List.of(greenhouse("acme")));

		assertThat(violatedFields(definition)).containsExactly("name");
	}

	@Test
	void rejectsNonHttpsWebsite() {
		assertThat(violatedFields(company("acme", "http://acme.example", greenhouse("acme"))))
			.containsExactly("website");
	}

	@Test
	void rejectsCompanyWithoutSources() {
		CompanyDefinition definition = new CompanyDefinition("Acme", "acme", null, false, List.of());

		assertThat(violatedFields(definition)).containsExactly("sources");
	}

	@Test
	void rejectsSourceWithoutTypeOrIdentifier() {
		CompanyDefinition definition = company("acme", null, new SourceDefinition(null, " ", null));

		assertThat(violatedFields(definition)).containsExactlyInAnyOrder("sources[0].type", "sources[0].identifier");
	}

	@Test
	void sourceRegionDefaultsToGlobal() {
		assertThat(new SourceDefinition(SourceType.LEVER, "acme", null).region()).isEqualTo(SourceRegion.GLOBAL);
	}

	private static CompanyDefinition company(String slug, String website, SourceDefinition source) {
		return new CompanyDefinition("Acme", slug, website, false, List.of(source));
	}

	private static SourceDefinition greenhouse(String identifier) {
		return new SourceDefinition(SourceType.GREENHOUSE, identifier, null);
	}

	private static List<String> violatedFields(CompanyDefinition definition) {
		Set<ConstraintViolation<CompanyDefinition>> violations = validator.validate(definition);
		return violations.stream()
			.map(violation -> violation.getPropertyPath().toString().replace(".<list element>", ""))
			.toList();
	}

}
