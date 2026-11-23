package dev.init.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	/** The application's time source; services take it as a dependency so tests can fix the time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
