package dev.init.source.jsonld;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtTests {

	@Test
	void wildcardGroupAppliesWhenThereIsNoGroupForUs() {
		RobotsTxt robots = RobotsTxt.parse("""
				User-agent: *
				Disallow: /private
				""", "InitBot");

		assertThat(robots.isAllowed("/private/page")).isFalse();
		assertThat(robots.isAllowed("/careers")).isTrue();
	}

	@Test
	void ownGroupReplacesTheWildcardGroup() {
		RobotsTxt robots = RobotsTxt.parse("""
				User-agent: *
				Disallow: /

				User-agent: initbot
				Disallow: /admin
				""", "InitBot");

		assertThat(robots.isAllowed("/careers")).as("own group allows it, despite * disallowing all").isTrue();
		assertThat(robots.isAllowed("/admin/users")).isFalse();
	}

	@Test
	void consecutiveUserAgentLinesShareOneGroup() {
		RobotsTxt robots = RobotsTxt.parse("""
				User-agent: Googlebot
				User-agent: InitBot
				Disallow: /search
				""", "InitBot");

		assertThat(robots.isAllowed("/search?q=intern")).isFalse();
	}

	@Test
	void longestMatchWinsAndAllowWinsTies() {
		RobotsTxt robots = RobotsTxt.parse("""
				User-agent: *
				Disallow: /jobs
				Allow: /jobs/romania
				Disallow: /same
				Allow: /same
				""", "InitBot");

		assertThat(robots.isAllowed("/jobs/germany")).isFalse();
		assertThat(robots.isAllowed("/jobs/romania/cluj")).isTrue();
		assertThat(robots.isAllowed("/same")).isTrue();
	}

	@Test
	void supportsWildcardsAndEndAnchors() {
		RobotsTxt robots = RobotsTxt.parse("""
				User-agent: *
				Disallow: /*.pdf$
				Disallow: /*?session=
				""", "InitBot");

		assertThat(robots.isAllowed("/files/brochure.pdf")).isFalse();
		assertThat(robots.isAllowed("/files/brochure.pdf.html")).isTrue();
		assertThat(robots.isAllowed("/careers?session=42")).isFalse();
		assertThat(robots.isAllowed("/careers?page=2")).isTrue();
	}

	@Test
	void emptyDisallowAndCommentsAllowEverything() {
		RobotsTxt robots = RobotsTxt.parse("""
				# all welcome
				User-agent: *
				Disallow:
				""", "InitBot");

		assertThat(robots.isAllowed("/anything")).isTrue();
	}

	@Test
	void emptyFileAllowsEverything() {
		assertThat(RobotsTxt.parse("", "InitBot").isAllowed("/careers")).isTrue();
	}

}
