package dev.init.source.jsonld;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A minimal robots.txt reader following RFC 9309: the group naming our product token applies,
 * otherwise the {@code *} group, otherwise everything is allowed. Within the group the longest
 * matching rule wins and {@code Allow} wins ties. {@code *} and a trailing {@code $} are supported.
 */
public final class RobotsTxt {

	private static final RobotsTxt ALLOW_ALL = new RobotsTxt(List.of());

	private final List<Rule> rules;

	private RobotsTxt(List<Rule> rules) {
		this.rules = rules;
	}

	public static RobotsTxt allowAll() {
		return ALLOW_ALL;
	}

	/**
	 * @param productToken our crawler's name, matched case-insensitively against {@code User-agent} lines
	 */
	public static RobotsTxt parse(String content, String productToken) {
		String token = productToken.toLowerCase(Locale.ROOT);
		List<Rule> ownRules = new ArrayList<>();
		List<Rule> wildcardRules = new ArrayList<>();
		boolean ownGroupFound = false;

		List<String> groupAgents = new ArrayList<>();
		boolean readingAgents = false;
		for (String rawLine : content.split("\\R")) {
			String line = stripComment(rawLine).strip();
			int colon = line.indexOf(':');
			if (colon < 0) {
				continue;
			}
			String field = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
			String value = line.substring(colon + 1).strip();
			switch (field) {
				case "user-agent" -> {
					if (!readingAgents) {
						groupAgents.clear();
						readingAgents = true;
					}
					groupAgents.add(value.toLowerCase(Locale.ROOT));
				}
				case "allow", "disallow" -> {
					readingAgents = false;
					if (value.isEmpty()) {
						continue;
					}
					Rule rule = Rule.of(field.equals("allow"), value);
					if (groupAgents.contains(token)) {
						ownGroupFound = true;
						ownRules.add(rule);
					}
					else if (groupAgents.contains("*")) {
						wildcardRules.add(rule);
					}
				}
				default -> readingAgents = false;
			}
			if (field.equals("user-agent") && groupAgents.contains(token)) {
				ownGroupFound = true;
			}
		}
		return new RobotsTxt(List.copyOf(ownGroupFound ? ownRules : wildcardRules));
	}

	/** @param path the URL path, optionally with its query string, for example {@code /careers?page=2} */
	public boolean isAllowed(String path) {
		String target = path == null || path.isEmpty() ? "/" : path;
		Rule best = null;
		for (Rule rule : rules) {
			if (rule.matches(target) && (best == null || rule.isMoreSpecificThan(best))) {
				best = rule;
			}
		}
		return best == null || best.allow();
	}

	private static String stripComment(String line) {
		int hash = line.indexOf('#');
		return hash < 0 ? line : line.substring(0, hash);
	}

	private record Rule(boolean allow, String path, Pattern pattern) {

		static Rule of(boolean allow, String path) {
			boolean anchored = path.endsWith("$");
			String body = anchored ? path.substring(0, path.length() - 1) : path;
			String[] literals = body.split("\\*", -1);
			StringBuilder regex = new StringBuilder();
			for (int i = 0; i < literals.length; i++) {
				if (i > 0) {
					regex.append(".*");
				}
				regex.append(Pattern.quote(literals[i]));
			}
			if (anchored) {
				regex.append('$');
			}
			return new Rule(allow, path, Pattern.compile(regex.toString()));
		}

		boolean matches(String target) {
			return pattern.matcher(target).lookingAt();
		}

		boolean isMoreSpecificThan(Rule other) {
			if (path.length() != other.path.length()) {
				return path.length() > other.path.length();
			}
			return allow && !other.allow;
		}

	}

}
