package dev.init.source.http;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * HTTP settings for fetching public sources.
 *
 * @param userAgent identifies the crawler to the sites it visits, with a link to the project
 */
@ConfigurationProperties(prefix = "init.http")
public record SourceHttpProperties(
		@DefaultValue("InitBot/0.1 (+https://github.com/denis09890/init)") String userAgent,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("20s") Duration readTimeout) {

}
