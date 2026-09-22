package com.flaggerj.core.model;

/**
 * The comparison applied between a {@link TargetingRule}'s configured values and the actual
 * attribute value resolved from a {@code FeatureContext} during evaluation.
 */
public enum RuleOperator {
    /** Matches when the actual value equals the single configured value. */
    EQUALS,
    /** Matches when the actual value does not equal the single configured value. */
    NOT_EQUALS,
    /** Matches when the actual value is present in the configured list of values. */
    IN,
    /** Matches when the actual value is absent from the configured list of values. */
    NOT_IN,
    /** Matches when the actual value contains any of the configured values as a substring. */
    CONTAINS
}
