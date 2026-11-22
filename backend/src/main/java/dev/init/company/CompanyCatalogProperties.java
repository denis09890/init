package dev.init.company;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The company catalog bound from {@code companies.yml}. Invalid entries stop the application at
 * startup with a message naming the offending field.
 *
 * @param syncOnStartup whether the catalog is synchronized into the database when the app starts
 * @param companies the configured companies
 */
@ConfigurationProperties(prefix = "init.catalog")
@Validated
public record CompanyCatalogProperties(
		@DefaultValue("true") boolean syncOnStartup,
		@DefaultValue List<@Valid CompanyDefinition> companies) {

}
