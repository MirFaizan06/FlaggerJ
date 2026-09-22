package com.flaggerj.core.provider;

import com.flaggerj.core.model.FlagDefinition;
import com.flaggerj.core.model.FlagType;
import com.flaggerj.core.model.RuleOperator;
import com.flaggerj.core.model.TargetingRule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@link FlagDefinition}s from a local JSON or YAML file. File format is selected purely by
 * extension ({@code .json} for JSON, anything else &mdash; typically {@code .yaml}/{@code .yml}
 * &mdash; is parsed as YAML). Both formats are parsed with hand-written, dependency-free parsers
 * ({@link JsonParser}, {@link MiniYamlParser}) so no third-party JSON/YAML library is pulled onto
 * the runtime classpath.
 *
 * <p>Expected document shape (JSON shown, YAML is structurally identical):
 * <pre>{@code
 * {
 *   "flags": [
 *     {
 *       "key": "new-checkout",
 *       "type": "BOOLEAN",
 *       "defaultValue": "false",
 *       "enabled": true,
 *       "rules": [
 *         { "attribute": "country", "operator": "EQUALS", "value": "US", "result": "true" }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 */
public final class FileFlagProvider {

    public List<FlagDefinition> load(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read flag file: " + path, e);
        }
        return parse(content, path.toString());
    }

    public List<FlagDefinition> parse(String content, String sourceName) {
        Object root = isJson(sourceName) ? JsonParser.parse(content) : MiniYamlParser.parse(content);
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("Root of flag file must be a mapping: " + sourceName);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> rootMap = (Map<String, Object>) root;
        Object flagsNode = rootMap.get("flags");
        if (!(flagsNode instanceof List)) {
            throw new IllegalArgumentException("Flag file must contain a 'flags' list: " + sourceName);
        }
        List<FlagDefinition> definitions = new ArrayList<>();
        for (Object item : (List<?>) flagsNode) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Each flag entry must be a mapping: " + sourceName);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> flagMap = (Map<String, Object>) item;
            definitions.add(toFlagDefinition(flagMap));
        }
        return definitions;
    }

    private boolean isJson(String sourceName) {
        return sourceName.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private FlagDefinition toFlagDefinition(Map<String, Object> map) {
        String key = requireString(map, "key");
        FlagType type = parseType(stringify(map.get("type")));
        String defaultValue = stringify(map.get("defaultValue"));
        boolean enabled = !map.containsKey("enabled") || Boolean.parseBoolean(stringify(map.get("enabled")));

        List<TargetingRule> rules = new ArrayList<>();
        Object rulesNode = map.get("rules");
        if (rulesNode instanceof List) {
            for (Object ruleObj : (List<?>) rulesNode) {
                if (ruleObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ruleMap = (Map<String, Object>) ruleObj;
                    rules.add(toTargetingRule(ruleMap));
                }
            }
        }
        return new FlagDefinition(key, type, defaultValue, rules, enabled);
    }

    private TargetingRule toTargetingRule(Map<String, Object> map) {
        String attribute = requireString(map, "attribute");
        RuleOperator operator = RuleOperator.valueOf(requireString(map, "operator").toUpperCase(Locale.ROOT));

        List<String> values = new ArrayList<>();
        Object valuesNode = map.get("values");
        Object valueNode = map.get("value");
        if (valuesNode instanceof List) {
            for (Object v : (List<?>) valuesNode) {
                values.add(stringify(v));
            }
        } else if (valueNode != null) {
            values.add(stringify(valueNode));
        }

        String result = requireString(map, "result");
        return new TargetingRule(attribute, operator, values, result);
    }

    private FlagType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return FlagType.STRING;
        }
        switch (raw.toUpperCase(Locale.ROOT)) {
            case "BOOLEAN":
                return FlagType.BOOLEAN;
            case "STRING":
                return FlagType.STRING;
            case "INTEGER":
            case "INT":
                return FlagType.INTEGER;
            case "DOUBLE":
                return FlagType.DOUBLE;
            default:
                throw new IllegalArgumentException("Unsupported flag type: " + raw);
        }
    }

    private String requireString(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required field '" + field + "' in flag entry");
        }
        return stringify(value);
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return Long.toString((long) d);
            }
            return Double.toString(d);
        }
        return String.valueOf(value);
    }
}
