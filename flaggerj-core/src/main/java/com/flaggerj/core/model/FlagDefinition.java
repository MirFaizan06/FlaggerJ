package com.flaggerj.core.model;

import java.util.List;
import java.util.Objects;

/**
 * The full configuration of a single feature flag: its type, default value, ordered list of
 * {@link TargetingRule}s, and enabled state. Instances are immutable and safe to share across
 * threads, which allows {@code FlaggerClient} to hold them in a {@code ConcurrentHashMap} and
 * swap them atomically without any external synchronization.
 */
public final class FlagDefinition {

    private final String key;
    private final FlagType type;
    private final String defaultValue;
    private final List<TargetingRule> rules;
    private final boolean enabled;

    public FlagDefinition(String key, FlagType type, String defaultValue, List<TargetingRule> rules,
                           boolean enabled) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.defaultValue = defaultValue;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.enabled = enabled;
    }

    /**
     * Convenience factory for a flag with no targeting rules that is always enabled.
     */
    public static FlagDefinition simple(String key, FlagType type, String defaultValue) {
        return new FlagDefinition(key, type, defaultValue, List.of(), true);
    }

    public String getKey() {
        return key;
    }

    public FlagType getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<TargetingRule> getRules() {
        return rules;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "FlagDefinition{"
                + "key='" + key + '\''
                + ", type=" + type
                + ", defaultValue='" + defaultValue + '\''
                + ", rules=" + rules
                + ", enabled=" + enabled
                + '}';
    }
}
