# Flag file schema reference

`FlaggerClient.loadFromFile(Path)` accepts a local JSON or YAML file describing a set of flags.
This document is the complete schema reference for that file format, for both supported formats.

Format is selected purely by file extension: a path ending in `.json` is parsed as JSON via
`com.flaggerj.core.provider.JsonParser`; anything else is parsed as YAML via
`com.flaggerj.core.provider.MiniYamlParser`. Both are hand-written, dependency-free parsers
bundled in `flaggerj-core` — see [Supported YAML subset](#supported-yaml-subset) below for exactly
what the YAML parser understands.

## Top-level shape

```json
{
  "flags": [ /* array of flag objects, see below */ ]
}
```

The root document must be a mapping with a single required key, `flags`, whose value is an array.
Anything else (a bare array at the root, a missing `flags` key, `flags` not being an array) raises
`IllegalArgumentException` when the file is loaded.

## Flag object

| Field          | Type                          | Required | Default    | Description                                                            |
|----------------|-------------------------------|----------|------------|----------------------------------------------------------------------------|
| `key`          | string                        | **Yes**  | —          | The lookup key, matched against `@FeatureFlag(key = ...)`.               |
| `type`         | string enum, see below        | No       | `STRING`   | Documents the intended value type. Informational only — see note below.  |
| `defaultValue` | string, number, or boolean    | No       | `null`     | Value returned when no rule matches. Coerced to a string internally.     |
| `enabled`      | boolean                       | No       | `true`     | When `false`, `defaultValue` is returned unconditionally and `rules` are never evaluated. |
| `rules`        | array of rule objects         | No       | `[]`       | Ordered targeting rules; see [Rule object](#rule-object).                |

**`type` values:** `BOOLEAN`, `STRING`, `INTEGER` (alias `INT`), `DOUBLE`. This field does not
change how `FlaggerClient` behaves — it exists purely as documentation/tooling metadata about what
the flag represents. `FlaggerClient.getBoolean`/`getInt`/`getDouble`/`getString` each attempt to
parse the stored string value regardless of the declared `type`; a mismatch (e.g. calling
`getInt(...)` on a flag whose value is `"not-a-number"`) falls back to the caller-supplied default
rather than throwing.

**`defaultValue` coercion:** JSON numbers and booleans are converted to their string form (e.g.
JSON `10` becomes the string `"10"`; JSON `0.25` becomes `"0.25"`). It's simplest and least
ambiguous to always quote `defaultValue` as a string in your config files, which both examples
below do.

## Rule object

| Field       | Type                                    | Required | Description                                                    |
|-------------|------------------------------------------|----------|--------------------------------------------------------------------|
| `attribute` | string                                   | **Yes**  | Attribute name resolved from `FeatureContext.getAttribute(...)`. |
| `operator`  | string enum: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `CONTAINS` (case-insensitive) | **Yes** | Comparison to apply. |
| `value`     | string, number, or boolean               | One of `value`/`values` required for `EQUALS`/`NOT_EQUALS` | Single comparison value. |
| `values`    | array of string/number/boolean           | One of `value`/`values` required for `IN`/`NOT_IN`/`CONTAINS` | Multiple comparison values. |
| `result`    | string, number, or boolean               | **Yes**  | Value returned when this rule matches.                          |

Rules are evaluated **in the order they appear**; the first rule whose `attribute` value (resolved
from the caller's `FeatureContext`) satisfies `operator` against `value`/`values` wins, and its
`result` is returned. If `attribute` was never set on the context, the rule never matches,
regardless of operator.

If both `value` and `values` are present, `values` takes precedence.

## Full JSON example

```json
{
  "flags": [
    {
      "key": "new-checkout",
      "type": "BOOLEAN",
      "defaultValue": "false",
      "enabled": true,
      "rules": [
        { "attribute": "country", "operator": "EQUALS", "value": "US", "result": "true" },
        { "attribute": "country", "operator": "IN", "values": ["CA", "MX"], "result": "true" }
      ]
    },
    {
      "key": "max-items-per-cart",
      "type": "INTEGER",
      "defaultValue": "10"
    },
    {
      "key": "checkout-discount-rate",
      "type": "DOUBLE",
      "defaultValue": "0.0",
      "rules": [
        { "attribute": "plan", "operator": "EQUALS", "value": "enterprise", "result": "0.15" }
      ]
    },
    {
      "key": "welcome-message",
      "type": "STRING",
      "defaultValue": "Welcome!",
      "rules": [
        { "attribute": "country", "operator": "EQUALS", "value": "US", "result": "Welcome to the US store!" }
      ]
    }
  ]
}
```

This exact file is available at [`examples/src/main/resources/flags.json`](../examples/src/main/resources/flags.json).

## Full YAML example

```yaml
flags:
  - key: new-checkout
    type: BOOLEAN
    defaultValue: "false"
    enabled: true
    rules:
      - attribute: country
        operator: EQUALS
        value: US
        result: "true"
      - attribute: country
        operator: IN
        values:
          - CA
          - MX
        result: "true"

  - key: max-items-per-cart
    type: INTEGER
    defaultValue: "10"

  - key: checkout-discount-rate
    type: DOUBLE
    defaultValue: "0.0"
    rules:
      - attribute: plan
        operator: EQUALS
        value: enterprise
        result: "0.15"

  - key: welcome-message
    type: STRING
    defaultValue: "Welcome!"
    rules:
      - attribute: country
        operator: EQUALS
        value: US
        result: "Welcome to the US store!"
```

This exact file is available at [`examples/src/main/resources/flags.yaml`](../examples/src/main/resources/flags.yaml).
Both files above have been verified to parse to identical `FlagDefinition`s and produce identical
evaluation results.

## Supported YAML subset

`MiniYamlParser` implements a deliberately small, block-style subset of YAML — enough to express
the flag schema above, not a general-purpose YAML engine. Supported:

- **Block mappings**: `key: value`, indentation-nested.
- **Block sequences**: lines starting with `- `, indentation-nested, either of scalars or of nested
  mappings (`- key: value` starting a mapping item).
- **Scalars**: unquoted strings, single- and double-quoted strings, `true`/`false`, `null`/`~`,
  and bare integers/decimals (matched by `-?\d+(\.\d+)?`, then parsed as `Double`).
- **Comments**: `# ...` to end of line, only when the `#` is at the start of the line or preceded
  by a space (so a `#` inside a quoted string is not mistaken for a comment).

**Not supported** — avoid these in FlaggerJ flag files:

- Flow-style collections: `values: [CA, MX]` or `{key: value}`. Use block-style sequences instead
  (see the `values:` example above).
- Anchors and aliases (`&anchor`, `*alias`).
- Multi-line scalars (`|`, `>`).
- Multiple documents in one file (`---` separators).
- Tabs for indentation (use spaces).

If you need any of the above, use the JSON format instead — `JsonParser` is a complete,
spec-conformant JSON parser (aside from not needing UTF-16 surrogate-pair edge cases beyond
standard `\uXXXX` escapes).

## Error handling

`FlaggerClient.loadFromFile` throws:

- `UncheckedIOException` if the file cannot be read.
- `IllegalArgumentException` if the content cannot be parsed as valid JSON/YAML, if the document
  doesn't match the schema above (missing `flags`, missing required fields on a flag or rule
  entry), or if `type`/`operator` contains a value outside the supported enums.

There is no partial-load behavior: a malformed file fails the whole load rather than silently
registering the flags that happened to parse correctly, so configuration errors surface
immediately at startup instead of manifesting as confusing default-value fallbacks in production.
