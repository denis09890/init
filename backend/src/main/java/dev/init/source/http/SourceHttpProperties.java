package dev.init.source.http;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * HTTP settings for fetching public sources.
 *
 * @param userAgent identifies the crawler to the sites it visits, with a link to the project
 * @param maxConcurrentRequestsPerHost requests in flight at once to one host
 * @param minRequestInterval minimum time between the starts of two requests to one host
 * @param maxAttempts attempts per request, including the first; 1 disables retries
 * @param initialBackoff wait after the first failed attempt; doubles with every further failure
 * @param maxBackoff upper limit for the wait between attempts
 */
@ConfigurationProperties(prefix = "init.http")
@Validated
public record SourceHttpProperties(
		@DefaultValue("InitBot/0.1 (+https://github.com/denis09890/init)") String userAgent,
		@DefaultValue("5s") Duration connectTimeout,
		@DefaultValue("20s") Duration readTimeout,
		@DefaultValue("2") @Min(1) int maxConcurrentRequestsPerHost,
		@DefaultValue("200ms") Duration minRequestInterval,
		@DefaultValue("3") @Min(1) int maxAttempts,
		@DefaultValue("1s") Duration initialBackoff,
		@DefaultValue("30s") Duration maxBackoff) {

}
