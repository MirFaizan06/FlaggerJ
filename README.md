# FlaggerJ

**A reflection-free, GraalVM Native Image compatible feature flag SDK for Java.**

FlaggerJ lets you define feature flags as a plain Java interface. A compile-time annotation
processor generates a concrete, hand-written-looking implementation for you — no reflection, no
dynamic proxies (CGLIB/ByteBuddy/JDK proxies), and no runtime code generation of any kind. Every
flag lookup compiles down to a direct method call, so FlaggerJ works out of the box under GraalVM
Native Image with zero reflection configuration.

```java
@FeatureContainer
public interface AppFeatures {

    @FeatureFlag(key = "new-checkout", defaultValue = "false")
    boolean isNewCheckoutEnabled(FeatureContext context);
}

// Generated at compile time — you never write this class by hand:
AppFeatures features = new AppFeaturesImpl(flaggerClient);
features.isNewCheckoutEnabled(FeatureContext.builder().country("US").build());
```

---

## Table of contents

- [Why FlaggerJ](#why-flaggerj)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
  - [`@FeatureContainer`](#featurecontainer)
  - [`@FeatureFlag`](#featureflag)
  - [`FeatureContext`](#featurecontext)
  - [`FlaggerClient`](#flaggerclient)
  - [Generated implementation classes](#generated-implementation-classes)
- [Targeting rules](#targeting-rules)
- [Loading flags from a file](#loading-flags-from-a-file)
- [GraalVM Native Image compatibility](#graalvm-native-image-compatibility)
- [Thread safety](#thread-safety)
- [Testing your flags](#testing-your-flags)
- [Compile-time validation](#compile-time-validation)
- [Project structure](#project-structure)
- [Building from source](#building-from-source)
- [Limitations](#limitations)
- [FAQ](#faq)
- [License](#license)

---

## Why FlaggerJ

Most feature flag client libraries lean on reflection or dynamic proxies to wire a friendly
interface up to an evaluation engine. That's a problem the moment you build a GraalVM native
image: reflective access needs explicit `reflect-config.json` entries, and dynamic proxy classes
need `proxy-config.json` entries — both fragile, both easy to get wrong, and both an ongoing
maintenance burden as your flag interface evolves.

FlaggerJ takes a different approach: instead of *discovering* your flag interface at runtime, it
*generates* a real implementation class for it at **compile time**, using the standard Java
Annotation Processing Tool (APT) API (`javax.annotation.processing`). The result is a normal
`.java` file, compiled by `javac` like any other class in your project. There is nothing for
GraalVM to reflect over, because there is nothing dynamic left by the time your code runs.

**Design goals:**

- **Zero reflection, zero proxies, zero bytecode generation at runtime.**
- **Type-safe flag access.** Call `features.isNewCheckoutEnabled(ctx)`, not
  `client.getBoolean("new-checkout", ctx)` scattered across your codebase.
- **No heavy dependencies.** The only runtime dependency is `slf4j-api`.
- **Fail fast, at compile time.** Misusing the annotations (wrong return type, `@FeatureContainer`
  on a class, etc.) is a `javac` error, not a runtime surprise.
- **Simple, inspectable generated code.** Open the generated `AppFeaturesImpl.java` in your
  `target/generated-sources` directory and read it top to bottom — there is no magic.

---

## Requirements

- Java 17 or newer.
- Maven (the project ships as a two-module Maven reactor).

---

## Installation

FlaggerJ has two artifacts:

| Artifact             | Scope     | Contains                                                             |
|-----------------------|-----------|------------------------------------------------------------------------|
| `flaggerj-core`       | compile   | Annotations, `FeatureContext`, `FlaggerClient`, file loading          |
| `flaggerj-processor`  | annotation processor only | The APT processor that generates `*Impl` classes at build time |

FlaggerJ is published via [JitPack](https://jitpack.io/#MirFaizan06/FlaggerJ), which builds
straight from GitHub releases — no Maven Central account needed on your end, and nothing for
consumers to install beyond adding the JitPack repository. `flaggerj-processor` only needs to run
during compilation, so wire it in as an annotation processor path rather than a regular compile
dependency:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.MirFaizan06.FlaggerJ</groupId>
        <artifactId>flaggerj-core</artifactId>
        <version>v1.0.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <release>17</release>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.github.MirFaizan06.FlaggerJ</groupId>
                        <artifactId>flaggerj-processor</artifactId>
                        <version>v1.0.0</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Gradle equivalent:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.MirFaizan06.FlaggerJ:flaggerj-core:v1.0.0'
    annotationProcessor 'com.github.MirFaizan06.FlaggerJ:flaggerj-processor:v1.0.0'
}
```

> Replace `v1.0.0` with whatever Git tag you actually release — JitPack builds a version the first
> time someone requests that tag, and caches the result afterwards. See
> [`PUBLISHING.md`](PUBLISHING.md) for the exact steps to cut a release.
>
> Prefer to skip GitHub entirely? Build and `mvn install` locally (see
> [Building from source](#building-from-source)) and depend on `com.flaggerj:flaggerj-core:1.0.0`
> from your own machine's `~/.m2` repository instead.

---

## Quick start

**1. Define a flag container interface:**

```java
package com.example.app;

import com.flaggerj.core.annotation.FeatureContainer;
import com.flaggerj.core.annotation.FeatureFlag;
import com.flaggerj.core.context.FeatureContext;

@FeatureContainer
public interface AppFeatures {

    @FeatureFlag(key = "new-checkout", defaultValue = "false")
    boolean isNewCheckoutEnabled(FeatureContext context);

    @FeatureFlag(key = "max-items-per-cart", defaultValue = "10")
    int getMaxItemsPerCart(FeatureContext context);

    @FeatureFlag(key = "checkout-discount-rate", defaultValue = "0.0")
    double getCheckoutDiscountRate(FeatureContext context);

    @FeatureFlag(key = "welcome-message", defaultValue = "Welcome!")
    String getWelcomeMessage(FeatureContext context);
}
```

**2. Build your project once.** The annotation processor generates
`com.example.app.AppFeaturesImpl` next to your interface — look for it under
`target/generated-sources/annotations`.

**3. Wire it up at startup:**

```java
FlaggerClient client = FlaggerClient.create();
client.loadFromFile(Path.of("config/flags.yaml")); // optional local fallback source

AppFeatures features = new AppFeaturesImpl(client);
```

**4. Use it anywhere, fully typed, no strings at the call site:**

```java
FeatureContext ctx = FeatureContext.builder()
        .userId(currentUser.getId())
        .country(currentUser.getCountry())
        .build();

if (features.isNewCheckoutEnabled(ctx)) {
    renderNewCheckout(ctx);
} else {
    renderLegacyCheckout(ctx);
}
```

A complete, runnable version of this example (including the YAML/JSON config files) lives under
[`examples/`](examples/).

---

## Core concepts

### `@FeatureContainer`

Applied to an **interface**. Marks it for compile-time code generation. `RetentionPolicy.SOURCE` —
it is discarded after compilation and leaves nothing behind for a reflective scan to find.

```java
@FeatureContainer
public interface AppFeatures { /* ... */ }
```

The processor generates `AppFeaturesImpl` in the same package, implementing `AppFeatures`.

### `@FeatureFlag`

Applied to a **method** inside a `@FeatureContainer` interface.

```java
@FeatureFlag(key = "new-checkout", defaultValue = "false")
boolean isNewCheckoutEnabled(FeatureContext context);
```

| Element        | Type     | Required | Description                                                                 |
|----------------|----------|----------|-------------------------------------------------------------------------------|
| `key`          | `String` | Yes      | The lookup key used against `FlaggerClient`.                                 |
| `defaultValue` | `String` | No (`""`)| The fallback value, expressed as a string and parsed at compile time into a literal matching the method's return type. |

**Supported return types:** `boolean`, `int`, `double`, `String`. Anything else is a compile
error.

**Supported parameter lists:**
- No parameters — the generated method evaluates against `FeatureContext.empty()`.
- A single `com.flaggerj.core.context.FeatureContext` parameter.

Anything else (wrong parameter type, more than one parameter, `static`/`default` modifiers on the
method) is rejected by the processor with a `javac` error pointing at the exact line.

### `FeatureContext`

An immutable, thread-safe bag of attributes describing "who" or "what" is being evaluated. Three
attributes are first-class (`userId`, `tenantId`, `country`); anything else is a free-form custom
attribute.

```java
FeatureContext ctx = FeatureContext.builder()
        .userId("user-42")
        .tenantId("acme-corp")
        .country("US")
        .attribute("plan", "enterprise")
        .attribute("betaTester", "true")
        .build();

FeatureContext empty = FeatureContext.empty();
```

`FeatureContext` instances are immutable once built (the builder copies attributes into a private
`ConcurrentHashMap` at `build()` time), so a single context can safely be shared and read from
multiple threads.

### `FlaggerClient`

The central evaluation engine. Holds a `ConcurrentHashMap<String, FlagDefinition>` of active flag
configuration and exposes typed getters plus registration/removal APIs:

```java
FlaggerClient client = FlaggerClient.create();

// Programmatic registration
client.registerFlag(FlagDefinition.simple("max-items-per-cart", FlagType.INTEGER, "10"));

// Bulk registration
client.registerFlags(listOfFlagDefinitions);

// File-based fallback / bulk load
client.loadFromFile(Path.of("config/flags.yaml"));

// Direct evaluation (what the generated *Impl classes call internally)
boolean enabled = client.getBoolean("new-checkout", ctx, false);
String message  = client.getString("welcome-message", ctx, "Welcome!");
int max         = client.getInt("max-items-per-cart", ctx, 10);
double rate     = client.getDouble("checkout-discount-rate", ctx, 0.0);

// Introspection
boolean known = client.hasFlag("new-checkout");
int total     = client.size();
client.removeFlag("new-checkout");
```

Every flag lookup follows the same resolution order:

1. If the flag key is not registered at all → return the caller-supplied default.
2. If the flag is registered but `enabled = false` → return the flag's own `defaultValue` (or the
   caller-supplied default if that is `null`).
3. Otherwise, evaluate the flag's [targeting rules](#targeting-rules) in order — the first rule
   whose attribute matches the given `FeatureContext` wins.
4. If no rule matches → return the flag's `defaultValue` (or the caller-supplied default if that
   is `null`).

`getInt`/`getDouble` fall back to the caller-supplied default (logging a warning) if the resolved
raw value cannot be parsed as that numeric type, so a malformed config entry degrades gracefully
instead of throwing at call sites.

### Generated implementation classes

For an interface `AppFeatures`, the processor writes `AppFeaturesImpl` in the same package. It:

- takes a single `FlaggerClient` constructor argument (throws `IllegalArgumentException` if
  `null`),
- implements every `@FeatureFlag` method by calling the matching `client.getXxx(...)` overload,
  using the key and default value baked in as literals at compile time,
- is a completely ordinary, `public final` class — instantiate it with `new`, no factory, no
  service loader, no proxy.

Example of what gets generated for the Quick Start interface above:

```java
package com.example.app;

import com.flaggerj.core.context.FeatureContext;
import com.flaggerj.core.client.FlaggerClient;

public final class AppFeaturesImpl implements com.example.app.AppFeatures {

    private final FlaggerClient client;

    public AppFeaturesImpl(FlaggerClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        this.client = client;
    }

    @Override
    public boolean isNewCheckoutEnabled(FeatureContext context) {
        return client.getBoolean("new-checkout", context, false);
    }

    @Override
    public int getMaxItemsPerCart(FeatureContext context) {
        return client.getInt("max-items-per-cart", context, 10);
    }

    @Override
    public double getCheckoutDiscountRate(FeatureContext context) {
        return client.getDouble("checkout-discount-rate", context, 0.0);
    }

    @Override
    public String getWelcomeMessage(FeatureContext context) {
        return client.getString("welcome-message", context, "Welcome!");
    }
}
```

---

## Targeting rules

A `FlagDefinition` carries an ordered list of `TargetingRule`s. Each rule names a context
attribute, an operator, one or more comparison values, and a result value:

```java
TargetingRule usRule = new TargetingRule(
        "country",              // attribute read from FeatureContext
        RuleOperator.EQUALS,    // operator
        List.of("US"),          // comparison value(s)
        "true");                // result if the rule matches
```

| Operator     | Matches when...                                                             |
|--------------|-------------------------------------------------------------------------------|
| `EQUALS`     | the actual value equals the single configured value                          |
| `NOT_EQUALS` | the actual value does not equal the single configured value                  |
| `IN`         | the actual value is present in the configured list of values                 |
| `NOT_IN`     | the actual value is absent from the configured list of values                |
| `CONTAINS`   | the actual value contains any configured value as a substring                |

Rules are evaluated **in order**; the first match wins and later rules are not evaluated. An
attribute that was never set on the `FeatureContext` (`getAttribute(...)` returns `null`) never
matches any rule, regardless of operator.

---

## Loading flags from a file

`FlaggerClient.loadFromFile(Path)` reads a local **JSON or YAML** file and registers every flag it
describes. Format is chosen by file extension: `.json` is parsed as JSON, anything else (typically
`.yaml`/`.yml`) is parsed as YAML.

Both parsers are hand-written and dependency-free — no Jackson, no SnakeYAML — in keeping with
FlaggerJ's zero-heavy-dependency, reflection-free design. See
[`docs/FLAG_FILE_SCHEMA.md`](docs/FLAG_FILE_SCHEMA.md) for the full schema reference, the exact
subset of YAML that's supported, and more examples. A minimal example:

```yaml
flags:
  - key: new-checkout
    type: BOOLEAN
    defaultValue: "false"
    rules:
      - attribute: country
        operator: EQUALS
        value: US
        result: "true"

  - key: max-items-per-cart
    type: INTEGER
    defaultValue: "10"
```

```java
client.loadFromFile(Path.of("config/flags.yaml"));
```

Flags loaded from file are registered exactly like programmatically-registered ones — later loads
(or `registerFlag` calls) for the same key simply replace the previous definition.

---

## GraalVM Native Image compatibility

FlaggerJ is designed to need **no** `reflect-config.json`, **no** `proxy-config.json`, and **no**
`native-image` build-time hints of any kind for its own code:

- `@FeatureContainer` and `@FeatureFlag` are `RetentionPolicy.SOURCE` — they do not exist in
  compiled `.class` files, so there is nothing for `native-image`'s reflection scanner to trip
  over.
- The `*Impl` classes are ordinary, statically-compiled Java classes generated **before**
  `native-image` ever runs — from its point of view they're indistinguishable from code you typed
  by hand.
- `FlaggerClient`, `FeatureContext`, and the file providers use only `ConcurrentHashMap`, standard
  collections, and hand-written parsers — no reflection-based (de)serialization anywhere in the
  hot path.

Because of this, building a native image of an application that uses FlaggerJ requires nothing
beyond your application's own native-image configuration.

---

## Thread safety

- `FlaggerClient` is fully thread-safe: its flag map is a `ConcurrentHashMap`, and every read/write
  method is safe to call concurrently from any number of threads without external
  synchronization.
- `FeatureContext` is immutable after `build()` and safe to share across threads.
- Generated `*Impl` classes hold no mutable state beyond the `FlaggerClient` reference, so a single
  instance can safely be shared application-wide (e.g. as a singleton bean).

---

## Testing your flags

Because `FlaggerClient` and `FeatureContext` are plain, dependency-light Java classes, testing flag
behavior needs nothing beyond JUnit — no mocking framework, no test containers:

```java
@Test
void newCheckoutIsEnabledForUsCustomers() {
    FlaggerClient client = FlaggerClient.create();
    client.registerFlag(new FlagDefinition(
            "new-checkout",
            FlagType.BOOLEAN,
            "false",
            List.of(new TargetingRule("country", RuleOperator.EQUALS, List.of("US"), "true")),
            true));

    AppFeatures features = new AppFeaturesImpl(client);

    FeatureContext us = FeatureContext.builder().country("US").build();
    FeatureContext de = FeatureContext.builder().country("DE").build();

    assertTrue(features.isNewCheckoutEnabled(us));
    assertFalse(features.isNewCheckoutEnabled(de));
}
```

See [`flaggerj-core/src/test/java/com/flaggerj/core/client/FlaggerClientTest.java`](flaggerj-core/src/test/java/com/flaggerj/core/client/FlaggerClientTest.java)
for a full suite covering defaults, targeting rules, disabled flags, JSON/YAML file loading, and
concurrent access.

---

## Compile-time validation

Misusing the annotations fails the build immediately, with a precise `javac` error — not a runtime
exception discovered in production:

```java
@FeatureContainer
public class NotAnInterface { }
```
```
error: @FeatureContainer can only be applied to interfaces
```

```java
@FeatureContainer
public interface BadFlag {
    @FeatureFlag(key = "bad")
    long notASupportedType();
}
```
```
error: @FeatureFlag method must return boolean, int, double, or String
```

Other rejected cases: `@FeatureFlag` on a `static` or `default` method, a method parameter list
other than "none" or a single `FeatureContext`, and a blank `key`.

---

## Project structure

```
FlaggerJ/
├── pom.xml                        Parent reactor (2 modules)
├── flaggerj-core/                 Runtime library
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/flaggerj/core/
│       │   ├── annotation/        @FeatureContainer, @FeatureFlag
│       │   ├── context/           FeatureContext
│       │   ├── client/            FlaggerClient
│       │   ├── model/             FlagDefinition, TargetingRule, FlagType, RuleOperator
│       │   └── provider/          FileFlagProvider + hand-written JSON/YAML parsers
│       └── test/java/...          JUnit 5 test suite
├── flaggerj-processor/            Compile-time annotation processor
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/flaggerj/processor/FeatureFlagProcessor.java
│       └── resources/META-INF/services/javax.annotation.processing.Processor
├── examples/                      Copy-pasteable reference usage (not part of the reactor build)
└── docs/
    └── FLAG_FILE_SCHEMA.md        Full JSON/YAML flag file schema reference
```

---

## Building from source

```bash
mvn clean install
```

This compiles both modules, runs the `flaggerj-core` test suite, and installs
`flaggerj-core-1.0.0.jar` and `flaggerj-processor-1.0.0.jar` into your local `~/.m2` repository so
other local projects can depend on them using the coordinates in
[Installation](#installation).

---

## Limitations

Being upfront about what FlaggerJ deliberately does *not* do:

- **No remote flag sync.** FlaggerJ is an in-memory engine with a local file fallback loader; it
  does not itself poll a remote flag management service. You can build that on top by calling
  `registerFlag`/`registerFlags`/`loadFromFile` on your own schedule.
- **YAML support is a practical subset**, not the full YAML 1.2 spec: block-style mappings and
  sequences, quoted and unquoted scalars, and comments are supported; flow-style collections
  (`[a, b]`, `{k: v}`), anchors/aliases, and multi-line scalars are not. Use block style (see
  [`docs/FLAG_FILE_SCHEMA.md`](docs/FLAG_FILE_SCHEMA.md)) or switch to the JSON format, which has
  no such restriction.
- **Flag values are always strings internally.** `FlagDefinition.getDefaultValue()` and
  `TargetingRule.getResultValue()` are `String`s; `FlaggerClient` parses them into the requested
  primitive type on each read. This keeps the storage model simple and file-format-agnostic at the
  cost of a small amount of repeated parsing (there is no per-key caching of parsed values).

---

## FAQ

**Do I need to add `flaggerj-processor` as a normal dependency?**
No — configure it as an `annotationProcessorPath` (Maven) or `annotationProcessor` (Gradle)
dependency only. It doesn't need to be on your application's runtime classpath.

**Where does the generated code go?**
Wherever your build tool puts annotation-processor output — for Maven that's
`target/generated-sources/annotations/<package>/<Interface>Impl.java` by default. Check it in? No
need; it's regenerated deterministically on every build from your `@FeatureContainer` interface.

**Can a flag method skip the `FeatureContext` argument?**
Yes — `@FeatureFlag` methods with no parameters are evaluated against `FeatureContext.empty()`,
which is useful for global flags that don't need targeting.

**What happens if I forget to load any flags at all?**
Every `@FeatureFlag` method still works — it just always returns its compiled-in `defaultValue`,
since `FlaggerClient` falls back to the caller-supplied default for any key it doesn't recognize.

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
