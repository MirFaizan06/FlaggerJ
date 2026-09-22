package com.flaggerj.core.client;

import com.flaggerj.core.context.FeatureContext;
import com.flaggerj.core.model.FlagDefinition;
import com.flaggerj.core.model.TargetingRule;
import com.flaggerj.core.provider.FileFlagProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central, thread-safe FlaggerJ evaluation engine.
 *
 * <p>Holds active flag configurations in an in-memory {@link ConcurrentHashMap}, keyed by flag
 * key. Flags can be registered programmatically ({@link #registerFlag(FlagDefinition)}) or loaded
 * in bulk from a local JSON/YAML file ({@link #loadFromFile(Path)}) via {@link FileFlagProvider}.
 *
 * <p>All read and write operations are safe to call concurrently from multiple threads without
 * external synchronization. Generated {@code @FeatureContainer} implementations hold a single
 * {@code FlaggerClient} instance and delegate every flag lookup directly to one of the
 * {@code getBoolean}/{@code getString}/{@code getInt}/{@code getDouble} methods below &mdash; no
 * reflection is involved on either side of that call.
 */
public final class FlaggerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlaggerClient.class);

    private final ConcurrentHashMap<String, FlagDefinition> flags = new ConcurrentHashMap<>();
    private final FileFlagProvider fileFlagProvider = new FileFlagProvider();

    public FlaggerClient() {
    }

    public static FlaggerClient create() {
        return new FlaggerClient();
    }

    /**
     * Registers (or replaces) a single flag definition.
     */
    public void registerFlag(FlagDefinition definition) {
        flags.put(definition.getKey(), definition);
        LOGGER.debug("Registered flag '{}'", definition.getKey());
    }

    /**
     * Registers (or replaces) a batch of flag definitions.
     */
    public void registerFlags(Iterable<FlagDefinition> definitions) {
        for (FlagDefinition definition : definitions) {
            registerFlag(definition);
        }
    }

    /**
     * Removes a flag definition. Subsequent lookups for {@code key} fall back to the caller's
     * supplied default value until the flag is registered again.
     */
    public void removeFlag(String key) {
        flags.remove(key);
    }

    public FlagDefinition getFlagDefinition(String key) {
        return flags.get(key);
    }

    public boolean hasFlag(String key) {
        return flags.containsKey(key);
    }

    /**
     * The number of flag definitions currently registered.
     */
    public int size() {
        return flags.size();
    }

    /**
     * Loads and registers all flags found in a local JSON or YAML file, as a local fallback
     * source of configuration. Existing flags with the same key are replaced.
     */
    public void loadFromFile(Path path) {
        List<FlagDefinition> loaded = fileFlagProvider.load(path);
        registerFlags(loaded);
        LOGGER.info("Loaded {} flag(s) from {}", loaded.size(), path);
    }

    public boolean getBoolean(String key, FeatureContext context, boolean defaultValue) {
        String resolved = resolve(key, context, Boolean.toString(defaultValue));
        return Boolean.parseBoolean(resolved);
    }

    public String getString(String key, FeatureContext context, String defaultValue) {
        return resolve(key, context, defaultValue);
    }

    public int getInt(String key, FeatureContext context, int defaultValue) {
        String resolved = resolve(key, context, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(resolved);
        } catch (NumberFormatException e) {
            LOGGER.warn("Flag '{}' resolved to non-integer value '{}', using default {}", key, resolved,
                    defaultValue);
            return defaultValue;
        }
    }

    public double getDouble(String key, FeatureContext context, double defaultValue) {
        String resolved = resolve(key, context, Double.toString(defaultValue));
        try {
            return Double.parseDouble(resolved);
        } catch (NumberFormatException e) {
            LOGGER.warn("Flag '{}' resolved to non-double value '{}', using default {}", key, resolved,
                    defaultValue);
            return defaultValue;
        }
    }

    private String resolve(String key, FeatureContext context, String fallback) {
        FlagDefinition definition = flags.get(key);
        if (definition == null) {
            LOGGER.debug("Flag '{}' not found, using fallback '{}'", key, fallback);
            return fallback;
        }
        if (!definition.isEnabled()) {
            return definition.getDefaultValue() != null ? definition.getDefaultValue() : fallback;
        }
        FeatureContext ctx = context == null ? FeatureContext.empty() : context;
        for (TargetingRule rule : definition.getRules()) {
            String actual = ctx.getAttribute(rule.getAttribute());
            if (rule.matches(actual)) {
                return rule.getResultValue();
            }
        }
        return definition.getDefaultValue() != null ? definition.getDefaultValue() : fallback;
    }
}
