package dev.init.classification;

import dev.init.source.NormalizedJob;

/**
 * Decides whether a posting is an early-career opportunity and what kind. Milestone 1 uses
 * {@link RuleBasedClassifier}; an LLM-based implementation can replace it behind this interface.
 */
public interface PostingClassifier {

	Classification classify(NormalizedJob job);

}
