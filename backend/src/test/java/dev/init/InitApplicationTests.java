package dev.init;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the application context starts against a real PostgreSQL (Testcontainers),
 * Flyway runs and Hibernate validates the schema.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class InitApplicationTests {

	@Test
	void contextLoads() {
	}

}
