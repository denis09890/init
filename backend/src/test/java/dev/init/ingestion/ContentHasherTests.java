package dev.init.ingestion;

import java.time.Instant;
import java.util.List;

import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTests {

	private static final Instant POSTED = Instant.parse("2026-11-01T00:00:00Z");

	@Test
	void sameContentGivesTheSameHash() {
		assertThat(ContentHasher.hash(job("Intern", "Build things.")))
			.isEqualTo(ContentHasher.hash(job("Intern", "Build things.")));
	}

	@Test
	void hashIsLowerCaseHexSha256() {
		assertThat(ContentHasher.hash(job("Intern", "Build things."))).matches("[0-9a-f]{64}");
		assertThat(ContentHasher.sha256("abc"))
			.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void changedTitleOrDescriptionChangesTheHash() {
		String base = ContentHasher.hash(job("Intern", "Build things."));

		assertThat(ContentHasher.hash(job("Junior", "Build things."))).isNotEqualTo(base);
		assertThat(ContentHasher.hash(job("Intern", "Build other things."))).isNotEqualTo(base);
	}

	@Test
	void changedLocationOrWorkplaceChangesTheHash() {
		String base = ContentHasher.hash(job("Intern", "x"));

		assertThat(ContentHasher.hash(withLocations(List.of("Cluj-Napoca")))).isNotEqualTo(base);
		assertThat(ContentHasher.hash(withRemoteType(RemoteType.REMOTE))).isNotEqualTo(base);
	}

	@Test
	void postingDateDoesNotChangeTheHash() {
		NormalizedJob earlier = job("Intern", "x");
		NormalizedJob later = new NormalizedJob("1", "Intern", "https://example.com/1", List.of("Bucharest"), "RO",
				RemoteType.HYBRID, "x", POSTED.plusSeconds(3600), "Engineering", "Intern", null);

		assertThat(ContentHasher.hash(later)).isEqualTo(ContentHasher.hash(earlier));
	}

	@Test
	void fieldBoundariesAreNotAmbiguous() {
		NormalizedJob first = new NormalizedJob("1", "ab", "c", List.of(), null, RemoteType.UNKNOWN, "", null, null,
				null, null);
		NormalizedJob second = new NormalizedJob("1", "a", "bc", List.of(), null, RemoteType.UNKNOWN, "", null, null,
				null, null);

		assertThat(ContentHasher.hash(first)).isNotEqualTo(ContentHasher.hash(second));
	}

	@Test
	void missingOptionalFieldsAreAccepted() {
		NormalizedJob job = new NormalizedJob("1", "Intern", "https://example.com/1", null, null, null, null, null,
				null, null, null);

		assertThat(ContentHasher.hash(job)).hasSize(64);
	}

	private static NormalizedJob job(String title, String description) {
		return new NormalizedJob("1", title, "https://example.com/1", List.of("Bucharest"), "RO", RemoteType.HYBRID,
				description, POSTED, "Engineering", "Intern", null);
	}

	private static NormalizedJob withLocations(List<String> locations) {
		return new NormalizedJob("1", "Intern", "https://example.com/1", locations, "RO", RemoteType.HYBRID, "x",
				POSTED, "Engineering", "Intern", null);
	}

	private static NormalizedJob withRemoteType(RemoteType remoteType) {
		return new NormalizedJob("1", "Intern", "https://example.com/1", List.of("Bucharest"), "RO", remoteType, "x",
				POSTED, "Engineering", "Intern", null);
	}

}
