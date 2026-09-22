package com.flaggerj.core.client;

import com.flaggerj.core.context.FeatureContext;
import com.flaggerj.core.model.FlagDefinition;
import com.flaggerj.core.model.FlagType;
import com.flaggerj.core.model.RuleOperator;
import com.flaggerj.core.model.TargetingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FlaggerClient")
class FlaggerClientTest {

    private FlaggerClient client;

    @BeforeEach
    void setUp() {
        client = FlaggerClient.create();
    }

    @Test
    @DisplayName("returns the caller-supplied default when a flag is not registered")
    void returnsDefaultForUnknownFlag() {
        assertFalse(client.getBoolean("unknown-flag", FeatureContext.empty(), false));
        assertTrue(client.getBoolean("unknown-flag", FeatureContext.empty(), true));
        assertEquals("fallback", client.getString("unknown-flag", FeatureContext.empty(), "fallback"));
        assertEquals(42, client.getInt("unknown-flag", FeatureContext.empty(), 42));
        assertEquals(3.14, client.getDouble("unknown-flag", FeatureContext.empty(), 3.14));
    }

    @Test
    @DisplayName("resolves a simple flag's default value when no rules match")
    void resolvesSimpleFlagDefault() {
        client.registerFlag(FlagDefinition.simple("new-checkout", FlagType.BOOLEAN, "false"));

        assertFalse(client.getBoolean("new-checkout", FeatureContext.empty(), true));
    }

    @Test
    @DisplayName("evaluates a targeting rule that matches the supplied context")
    void evaluatesMatchingTargetingRule() {
        TargetingRule usRule = new TargetingRule("country", RuleOperator.EQUALS, List.of("US"), "true");
        FlagDefinition flag = new FlagDefinition("new-checkout", FlagType.BOOLEAN, "false", List.of(usRule), true);
        client.registerFlag(flag);

        FeatureContext usContext = FeatureContext.builder().country("US").build();
        FeatureContext caContext = FeatureContext.builder().country("CA").build();

        assertTrue(client.getBoolean("new-checkout", usContext, false), "US context should match the targeting rule");
        assertFalse(client.getBoolean("new-checkout", caContext, false), "CA context should fall back to the default");
    }

    @Test
    @DisplayName("evaluates IN operator rules against custom attributes")
    void evaluatesInOperatorAgainstCustomAttribute() {
        TargetingRule betaRule = new TargetingRule(
                "plan", RuleOperator.IN, List.of("enterprise", "beta"), "premium-message");
        FlagDefinition flag = new FlagDefinition(
                "welcome-message", FlagType.STRING, "default-message", List.of(betaRule), true);
        client.registerFlag(flag);

        FeatureContext betaContext = FeatureContext.builder().attribute("plan", "beta").build();
        FeatureContext freeContext = FeatureContext.builder().attribute("plan", "free").build();

        assertEquals("premium-message", client.getString("welcome-message", betaContext, "unused"));
        assertEquals("default-message", client.getString("welcome-message", freeContext, "unused"));
    }

    @Test
    @DisplayName("first matching rule wins, later rules are not evaluated")
    void firstMatchingRuleWins() {
        TargetingRule firstRule = new TargetingRule("country", RuleOperator.EQUALS, List.of("US"), "10");
        TargetingRule secondRule = new TargetingRule("country", RuleOperator.EQUALS, List.of("US"), "20");
        FlagDefinition flag = new FlagDefinition(
                "max-items", FlagType.INTEGER, "5", List.of(firstRule, secondRule), true);
        client.registerFlag(flag);

        FeatureContext usContext = FeatureContext.builder().country("US").build();
        assertEquals(10, client.getInt("max-items", usContext, 5));
    }

    @Test
    @DisplayName("a disabled flag always resolves to its default value, ignoring rules")
    void disabledFlagIgnoresRules() {
        TargetingRule alwaysMatches = new TargetingRule("country", RuleOperator.EQUALS, List.of("US"), "true");
        FlagDefinition flag = new FlagDefinition("new-checkout", FlagType.BOOLEAN, "false", List.of(alwaysMatches), false);
        client.registerFlag(flag);

        FeatureContext usContext = FeatureContext.builder().country("US").build();
        assertFalse(client.getBoolean("new-checkout", usContext, true));
    }

    @Test
    @DisplayName("removeFlag falls back to the caller default afterwards")
    void removeFlagFallsBackToDefault() {
        client.registerFlag(FlagDefinition.simple("max-items", FlagType.INTEGER, "10"));
        assertTrue(client.hasFlag("max-items"));

        client.removeFlag("max-items");

        assertFalse(client.hasFlag("max-items"));
        assertEquals(99, client.getInt("max-items", FeatureContext.empty(), 99));
    }

    @Test
    @DisplayName("getDouble falls back to caller default when the stored value cannot be parsed")
    void getDoubleFallsBackOnUnparsableValue() {
        client.registerFlag(FlagDefinition.simple("ratio", FlagType.DOUBLE, "not-a-number"));

        assertEquals(1.5, client.getDouble("ratio", FeatureContext.empty(), 1.5));
    }

    @Test
    @DisplayName("loadFromFile parses a JSON flag file and registers its flags")
    void loadsFlagsFromJsonFile(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("flags.json");
        Files.writeString(jsonFile, """
                {
                  "flags": [
                    {
                      "key": "new-checkout",
                      "type": "BOOLEAN",
                      "defaultValue": "false",
                      "rules": [
                        { "attribute": "country", "operator": "EQUALS", "value": "US", "result": "true" }
                      ]
                    },
                    {
                      "key": "max-items",
                      "type": "INTEGER",
                      "defaultValue": "10"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        client.loadFromFile(jsonFile);

        assertEquals(2, client.size());
        assertTrue(client.getBoolean("new-checkout", FeatureContext.builder().country("US").build(), false));
        assertFalse(client.getBoolean("new-checkout", FeatureContext.builder().country("CA").build(), false));
        assertEquals(10, client.getInt("max-items", FeatureContext.empty(), 0));
    }

    @Test
    @DisplayName("loadFromFile parses a YAML flag file and registers its flags")
    void loadsFlagsFromYamlFile(@TempDir Path tempDir) throws IOException {
        Path yamlFile = tempDir.resolve("flags.yaml");
        Files.writeString(yamlFile, """
                flags:
                  - key: new-checkout
                    type: BOOLEAN
                    defaultValue: "false"
                    rules:
                      - attribute: country
                        operator: EQUALS
                        value: US
                        result: "true"
                  - key: welcome-message
                    type: STRING
                    defaultValue: "Hello"
                """, StandardCharsets.UTF_8);

        client.loadFromFile(yamlFile);

        assertEquals(2, client.size());
        assertTrue(client.getBoolean("new-checkout", FeatureContext.builder().country("US").build(), false));
        assertEquals("Hello", client.getString("welcome-message", FeatureContext.empty(), "unused"));
    }

    @Test
    @DisplayName("concurrent reads and writes across many threads do not corrupt flag state")
    void isThreadSafeUnderConcurrentAccess() throws InterruptedException {
        client.registerFlag(FlagDefinition.simple("concurrent-flag", FlagType.INTEGER, "0"));

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulReads = new AtomicInteger();
        try {
            for (int i = 0; i < threadCount; i++) {
                int value = i;
                executor.submit(() -> {
                    client.registerFlag(FlagDefinition.simple("concurrent-flag", FlagType.INTEGER, String.valueOf(value)));
                    int resolved = client.getInt("concurrent-flag", FeatureContext.empty(), -1);
                    if (resolved >= 0) {
                        successfulReads.incrementAndGet();
                    }
                });
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(threadCount, successfulReads.get());
    }
}
