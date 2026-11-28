package dev.init.classification;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import dev.init.source.NormalizedJob;
import dev.init.source.RemoteType;
import org.springframework.stereotype.Component;

import static dev.init.classification.ClassificationKeywords.*;

/**
 * Keyword rules in English and Romanian (see ADR 0003).
 * <ol>
 * <li><b>Opportunity type</b> from the title first, then the employment-type / experience-level fields,
 * then explicit phrases in the description.</li>
 * <li><b>Exclusions</b> override that: senior or leadership titles, management titles (unless the title
 * itself says junior, trainee, ...), and descriptions asking for 3+ years of experience. Internship,
 * working-student and graduate titles are never excluded: their title is unambiguous.</li>
 * <li><b>Domain</b> by keyword score: title matches weigh 3, department 2; the description is only used
 * when neither says anything.</li>
 * <li><b>Location</b>: Romania, or remote and open to Romania/EU; unknown locations are kept.</li>
 * </ol>
 * Every rule that fires adds a human-readable reason to the result.
 */
@Component
public class RuleBasedClassifier implements PostingClassifier {

	/** Years of experience from which a posting is no longer early career. */
	static final int MAX_EARLY_CAREER_YEARS = 2;

	/** Applied to {@link MatchText#normalize normalized} text: lower case, no diacritics, no punctuation but '+'. */
	private static final String YEARS = "(?:years?|yrs|ani)\\b";

	/** A description that asks for at most this many years of experience describes a junior role. */
	static final int MAX_JUNIOR_SIGNAL_YEARS = 1;

	/**
	 * "3+ years of experience", "3-5 years experience" (normalized to "3 5"; group 2 is the upper
	 * bound), "minim 2 ani de experienta".
	 */
	private static final Pattern YEARS_THEN_EXPERIENCE = Pattern.compile(
			"\\b(\\d{1,2})(?:\\s(?:to\\s|si\\s|la\\s)?(\\d{1,2}))?\\s*\\+?\\s*" + YEARS
					+ "\\s(?:\\S+\\s){0,4}?(?:experience|experienta)");

	/** "experience of at least 3 years", "experienta de minim 3 ani". */
	private static final Pattern EXPERIENCE_THEN_YEARS = Pattern.compile(
			"(?:experience|experienta)\\s(?:\\S+\\s){0,4}?(\\d{1,2})\\s*\\+?\\s*" + YEARS);

	private static final Pattern ROMANIAN_DIACRITICS = Pattern.compile("[ăâîșțşţ]");

	@Override
	public Classification classify(NormalizedJob job) {
		List<String> reasons = new ArrayList<>();
		MatchText title = MatchText.of(job.title());
		MatchText hints = MatchText.of(job.employmentType(), job.experienceLevel());
		MatchText description = MatchText.of(job.description());

		TypeEvidence evidence = opportunityType(title, hints, description, job.description());
		evidence.reason().ifPresent(reasons::add);

		Seniority excludedAs = exclusion(job, title, evidence, reasons);
		boolean earlyCareer = evidence.type() != OpportunityType.OTHER && excludedAs == null;
		OpportunityType type = earlyCareer ? evidence.type() : OpportunityType.OTHER;
		Seniority seniority = excludedAs != null ? excludedAs : seniorityOf(evidence);
		if (!earlyCareer && excludedAs == null) {
			reasons.add("no early-career signal in title, employment type or description");
		}

		Domain domain = domain(title, MatchText.of(job.department()), description, reasons);
		boolean locationEligible = locationEligible(job, reasons);
		return new Classification(earlyCareer, type, domain, seniority, locationEligible, language(job),
				paid(description), reasons);
	}

	// ----- Opportunity type ---------------------------------------------------------------------

	private TypeEvidence opportunityType(MatchText title, MatchText hints, MatchText description,
			String rawDescription) {
		return firstMatch(title, Strength.TITLE, INTERNSHIP, WORKING_STUDENT, GRADUATE_PROGRAM, JUNIOR)
			.or(() -> firstMatch(hints, Strength.HINT, INTERNSHIP_HINTS, WORKING_STUDENT_HINTS, GRADUATE_HINTS,
					JUNIOR_HINTS))
			.or(() -> firstMatch(description, Strength.DESCRIPTION, INTERNSHIP_DESCRIPTION,
					WORKING_STUDENT_DESCRIPTION, GRADUATE_DESCRIPTION, JUNIOR_DESCRIPTION))
			.or(() -> littleExperienceAsked(rawDescription))
			.orElse(TypeEvidence.NONE);
	}

	/**
	 * "1+ years of experience" with no larger number of years anywhere in the description. Every
	 * mention counts, upper bounds of ranges included, so "1 year with Docker, 3 years with Java" or
	 * "1-3 years" are not taken as junior.
	 */
	private static Optional<TypeEvidence> littleExperienceAsked(String description) {
		OptionalInt most = yearsMentioned(description).max();
		if (most.isEmpty() || most.getAsInt() > MAX_JUNIOR_SIGNAL_YEARS) {
			return Optional.empty();
		}
		int years = most.getAsInt();
		return Optional.of(new TypeEvidence(OpportunityType.JUNIOR, Strength.DESCRIPTION,
				"at most " + years + (years == 1 ? " year" : " years") + " of experience"));
	}

	/** Lists in the order internship, working student, graduate program, junior. */
	private static Optional<TypeEvidence> firstMatch(MatchText text, Strength strength, List<String> internship,
			List<String> workingStudent, List<String> graduate, List<String> junior) {
		Map<OpportunityType, List<String>> ordered = new LinkedHashMap<>();
		ordered.put(OpportunityType.INTERNSHIP, internship);
		ordered.put(OpportunityType.WORKING_STUDENT, workingStudent);
		ordered.put(OpportunityType.GRADUATE_PROGRAM, graduate);
		ordered.put(OpportunityType.JUNIOR, junior);
		for (Map.Entry<OpportunityType, List<String>> entry : ordered.entrySet()) {
			Optional<String> keyword = text.findAny(entry.getValue());
			if (keyword.isPresent()) {
				return Optional.of(new TypeEvidence(entry.getKey(), strength, keyword.get()));
			}
		}
		return Optional.empty();
	}

	// ----- Exclusions ---------------------------------------------------------------------------

	/** @return the seniority that excludes the posting, or null if it is not excluded */
	private Seniority exclusion(NormalizedJob job, MatchText title, TypeEvidence evidence, List<String> reasons) {
		if (evidence.isUnambiguousTitle()) {
			return null;
		}
		MatchText cleanedTitle = MatchText.of(withoutFalsePositives(job.title()));
		Optional<String> senior = cleanedTitle.findAny(SENIOR_TITLES);
		if (senior.isPresent()) {
			reasons.add("excluded: title says '" + senior.get() + "'");
			return Seniority.SENIOR;
		}
		Optional<String> lead = cleanedTitle.findAny(LEAD_TITLES);
		if (lead.isPresent()) {
			reasons.add("excluded: title says '" + lead.get() + "'");
			return Seniority.LEAD;
		}
		Optional<String> manager = title.findAny(MANAGER_TITLES);
		if (manager.isPresent() && evidence.strength() != Strength.TITLE) {
			reasons.add("excluded: management title '" + manager.get() + "' without junior/trainee in the title");
			return Seniority.LEAD;
		}
		OptionalInt years = requiredYears(job.description());
		if (years.isPresent() && years.getAsInt() > MAX_EARLY_CAREER_YEARS) {
			reasons.add("excluded: asks for " + years.getAsInt() + "+ years of experience");
			return Seniority.MID;
		}
		return null;
	}

	private static String withoutFalsePositives(String title) {
		String normalized = MatchText.normalize(title == null ? "" : title);
		for (String phrase : EXCLUSION_FALSE_POSITIVES) {
			normalized = (" " + normalized + " ").replace(" " + phrase + " ", " ").strip();
		}
		return normalized;
	}

	/** The smallest number of years of experience the description asks for, if any. */
	static OptionalInt requiredYears(String description) {
		return yearsMentioned(description).min();
	}

	/** Every number of years of experience the description mentions, both ends of a range included. */
	private static IntStream yearsMentioned(String description) {
		String text = MatchText.normalize(description == null ? "" : description);
		IntStream.Builder years = IntStream.builder();
		for (Pattern pattern : List.of(YEARS_THEN_EXPERIENCE, EXPERIENCE_THEN_YEARS)) {
			Matcher matcher = pattern.matcher(text);
			while (matcher.find()) {
				years.add(Integer.parseInt(matcher.group(1)));
				if (matcher.groupCount() >= 2 && matcher.group(2) != null) {
					years.add(Integer.parseInt(matcher.group(2)));
				}
			}
		}
		return years.build();
	}

	private static Seniority seniorityOf(TypeEvidence evidence) {
		return switch (evidence.type()) {
			case INTERNSHIP, WORKING_STUDENT -> Seniority.INTERN;
			case GRADUATE_PROGRAM -> Seniority.ENTRY;
			case JUNIOR -> evidence.keyword().contains("entry") || evidence.keyword().contains("experienta")
					|| evidence.keyword().contains("experience") ? Seniority.ENTRY : Seniority.JUNIOR;
			case OTHER -> Seniority.UNKNOWN;
		};
	}

	// ----- Domain -------------------------------------------------------------------------------

	private static Domain domain(MatchText title, MatchText department, MatchText description, List<String> reasons) {
		Domain best = Domain.OTHER;
		long bestScore = 0;
		for (Map.Entry<Domain, List<String>> entry : DOMAINS.entrySet()) {
			long score = 3 * title.count(entry.getValue()) + 2 * department.count(entry.getValue());
			if (score > bestScore) {
				best = entry.getKey();
				bestScore = score;
			}
		}
		if (bestScore == 0) {
			for (Map.Entry<Domain, List<String>> entry : DOMAINS.entrySet()) {
				long score = description.count(entry.getValue());
				if (score > bestScore) {
					best = entry.getKey();
					bestScore = score;
				}
			}
			if (bestScore > 0) {
				reasons.add("domain " + best + " from the description");
			}
		}
		else {
			reasons.add("domain " + best + " from the title/department");
		}
		return best;
	}

	// ----- Location -----------------------------------------------------------------------------

	private static boolean locationEligible(NormalizedJob job, List<String> reasons) {
		if ("RO".equalsIgnoreCase(job.countryCode())) {
			reasons.add("location: country RO");
			return true;
		}
		MatchText locations = MatchText.of(job.locations().toArray(String[]::new));
		Optional<String> place = locations.findAny(ROMANIAN_PLACES);
		if (place.isPresent()) {
			reasons.add("location: " + place.get());
			return true;
		}
		if (job.remoteType() == RemoteType.REMOTE) {
			Optional<String> region = locations.findAny(REMOTE_REGIONS_INCLUDING_ROMANIA);
			if (region.isPresent() || locations.isEmpty()) {
				reasons.add("location: remote, open to " + region.orElse("any location"));
				return true;
			}
		}
		if (locations.isEmpty() && job.countryCode() == null) {
			reasons.add("location: unknown, kept");
			return true;
		}
		reasons.add("location: outside Romania (" + String.join("; ", job.locations())
				+ (job.countryCode() == null ? "" : ", " + job.countryCode()) + ")");
		return false;
	}

	// ----- Language and pay ---------------------------------------------------------------------

	private static String language(NormalizedJob job) {
		String raw = (job.title() + " " + job.description()).toLowerCase(Locale.ROOT);
		MatchText text = MatchText.of(raw);
		long romanian = text.count(ROMANIAN_MARKERS) + (ROMANIAN_DIACRITICS.matcher(raw).find() ? 3 : 0);
		long english = text.count(ENGLISH_MARKERS);
		if (romanian >= 2 && romanian > english) {
			return "ro";
		}
		if (english >= 2) {
			return "en";
		}
		return null;
	}

	private static Boolean paid(MatchText description) {
		if (description.findAny(UNPAID).isPresent()) {
			return Boolean.FALSE;
		}
		return description.findAny(PAID).isPresent() ? Boolean.TRUE : null;
	}

	// ----- Evidence -------------------------------------------------------------------------------

	private enum Strength {
		TITLE, HINT, DESCRIPTION
	}

	private record TypeEvidence(OpportunityType type, Strength strength, String keyword) {

		static final TypeEvidence NONE = new TypeEvidence(OpportunityType.OTHER, null, null);

		/** Internship, working-student and graduate titles leave no doubt, whatever else they contain. */
		boolean isUnambiguousTitle() {
			return strength == Strength.TITLE && type != OpportunityType.JUNIOR;
		}

		Optional<String> reason() {
			if (type == OpportunityType.OTHER) {
				return Optional.empty();
			}
			String where = switch (strength) {
				case TITLE -> "title";
				case HINT -> "employment type";
				case DESCRIPTION -> "description";
			};
			return Optional.of(type + ": " + where + " says '" + keyword + "'");
		}

	}

}
