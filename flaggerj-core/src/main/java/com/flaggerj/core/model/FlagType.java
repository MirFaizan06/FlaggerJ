package com.flaggerj.core.model;

/**
 * The primitive value type a {@link FlagDefinition} evaluates to. Used only for descriptive and
 * validation purposes when loading flag definitions from file; evaluation itself is always
 * performed against the flag's raw string representation.
 */
public enum FlagType {
    BOOLEAN,
    STRING,
    INTEGER,
    DOUBLE
}
