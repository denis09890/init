package dev.init.company;

/**
 * A company with the number of postings it currently has.
 *
 * @param openPostings every open posting
 * @param earlyCareerPostings open postings that are early career and in Romania, what the feed shows by default
 */
public record CompanySummary(String name, String slug, String website, long openPostings, long earlyCareerPostings) {

}
