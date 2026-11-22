package dev.init;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: the application context starts against a real PostgreSQL (Testcontainers),
 * Flyway runs and Hibernate validates the schema.
 */
@IntegrationTest
class InitApplicationTests {

	@Test
	void contextLoads() {
	}

}
