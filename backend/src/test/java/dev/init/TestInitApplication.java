package dev.init;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application locally against a throwaway PostgreSQL started by Testcontainers,
 * without Docker Compose. Useful for quick experiments; data is lost on shutdown.
 */
public class TestInitApplication {

	public static void main(String[] args) {
		SpringApplication.from(InitApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
