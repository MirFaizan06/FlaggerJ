package com.flaggerj.core.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free, indentation-based parser for the subset of YAML needed to express
 * flag configuration files: nested mappings, sequences of scalars, and sequences of mappings
 * (block style only; no flow style, anchors, multi-document streams, or multi-line scalars).
 *
 * <p>Produces the same object graph shape as {@link JsonParser}: {@link Map}, {@link List},
 * {@link String}, {@link Double}, {@link Boolean}, and {@code null}.
 */
final class MiniYamlParser {

    private static final class Line {
        final int indent;
        final String content;

        Line(int indent, String content) {
            this.indent = indent;
            this.content = content;
        }
    }

    private final List<Line> lines;

    private MiniYamlParser(List<Line> lines) {
        this.lines = lines;
    }

    static Object parse(String input) {
        List<Line> lines = new ArrayList<>();
        for (String rawLine : input.split("\\r?\\n")) {
            String stripped = stripComment(rawLine);
            if (stripped.trim().isEmpty()) {
                continue;
            }
            int indent = 0;
            while (indent < stripped.length() && stripped.charAt(indent) == ' ') {
                indent++;
            }
            lines.add(new Line(indent, stripped.substring(indent)));
        }
        if (lines.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        MiniYamlParser parser = new MiniYamlParser(lines);
        int[] index = {0};
        return parser.parseBlock(index, lines.get(0).indent);
    }

    private static String stripComment(String line) {
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (c == '#' && (i == 0 || line.charAt(i - 1) == ' ')) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private Object parseBlock(int[] index, int indent) {
        if (index[0] >= lines.size()) {
            return new LinkedHashMap<String, Object>();
        }
        Line first = lines.get(index[0]);
        if (first.content.equals("-") || first.content.startsWith("- ")) {
            return parseList(index, indent);
        }
        return parseMap(index, indent);
    }

    private List<Object> parseList(int[] index, int indent) {
        List<Object> list = new ArrayList<>();
        while (index[0] < lines.size()) {
            Line line = lines.get(index[0]);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                throw new IllegalArgumentException("Unexpected indentation at line: " + line.content);
            }
            if (!(line.content.equals("-") || line.content.startsWith("- "))) {
                break;
            }
            String remainder = line.content.equals("-") ? "" : line.content.substring(2);
            if (remainder.isEmpty()) {
                index[0]++;
                Object value = (index[0] < lines.size() && lines.get(index[0]).indent > indent)
                        ? parseBlock(index, lines.get(index[0]).indent)
                        : null;
                list.add(value);
            } else if (findKeyColon(remainder) >= 0) {
                int itemIndent = line.indent + 2;
                List<Line> syntheticLines = new ArrayList<>();
                syntheticLines.add(new Line(itemIndent, remainder));
                index[0]++;
                while (index[0] < lines.size() && lines.get(index[0]).indent > indent) {
                    syntheticLines.add(lines.get(index[0]));
                    index[0]++;
                }
                MiniYamlParser subParser = new MiniYamlParser(syntheticLines);
                int[] subIndex = {0};
                list.add(subParser.parseMap(subIndex, itemIndent));
            } else {
                list.add(parseScalar(remainder));
                index[0]++;
            }
        }
        return list;
    }

    private Map<String, Object> parseMap(int[] index, int indent) {
        Map<String, Object> map = new LinkedHashMap<>();
        while (index[0] < lines.size()) {
            Line line = lines.get(index[0]);
            if (line.indent < indent) {
                break;
            }
            if (line.indent > indent) {
                throw new IllegalArgumentException("Unexpected indentation at line: " + line.content);
            }
            if (line.content.equals("-") || line.content.startsWith("- ")) {
                break;
            }
            int colonPos = findKeyColon(line.content);
            if (colonPos < 0) {
                throw new IllegalArgumentException("Expected 'key: value' at line: " + line.content);
            }
            String key = unquote(line.content.substring(0, colonPos).trim());
            String valuePart = line.content.substring(colonPos + 1).trim();
            index[0]++;
            if (valuePart.isEmpty()) {
                if (index[0] < lines.size() && lines.get(index[0]).indent > indent) {
                    Object nested = parseBlock(index, lines.get(index[0]).indent);
                    map.put(key, nested);
                } else {
                    map.put(key, null);
                }
            } else {
                map.put(key, parseScalar(valuePart));
            }
        }
        return map;
    }

    private int findKeyColon(String content) {
        boolean inQuotes = false;
        char quoteChar = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (c == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }

    private Object parseScalar(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return unquote(value);
        }
        if (value.equals("true") || value.equals("false")) {
            return Boolean.parseBoolean(value);
        }
        if (value.equals("null") || value.equals("~")) {
            return null;
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return Double.parseDouble(value);
        }
        return value;
    }

    private String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
