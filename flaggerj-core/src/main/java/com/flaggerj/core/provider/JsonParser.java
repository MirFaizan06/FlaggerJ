package com.flaggerj.core.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free recursive-descent JSON parser. Produces a plain object graph made up
 * of {@link Map} (JSON object, insertion-ordered via {@link LinkedHashMap}), {@link List} (JSON
 * array), {@link String}, {@link Double} (all JSON numbers), {@link Boolean}, and {@code null}.
 *
 * <p>Only what {@link FileFlagProvider} needs to load flag configuration files is implemented;
 * this is not a general-purpose, fully spec-compliant JSON library.
 */
final class JsonParser {

    private final String input;
    private int pos;

    private JsonParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos != input.length()) {
            throw new IllegalArgumentException("Unexpected trailing content at position " + parser.pos);
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
                return parseString();
            case 't':
            case 'f':
                return parseBoolean();
            case 'n':
                return parseNull();
            default:
                return parseNumber();
        }
    }

    private Map<String, Object> parseObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == '}') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }
        return map;
    }

    private List<Object> parseArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == ']') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unterminated string literal");
            }
            char c = input.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                if (pos >= input.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence");
                }
                char escape = input.charAt(pos++);
                switch (escape) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > input.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape at position " + pos);
                        }
                        String hex = input.substring(pos, pos + 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid escape character '" + escape + "'");
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Boolean parseBoolean() {
        if (input.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (input.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("Invalid literal at position " + pos);
    }

    private Object parseNull() {
        if (input.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new IllegalArgumentException("Invalid literal at position " + pos);
    }

    private Double parseNumber() {
        int start = pos;
        if (pos < input.length() && input.charAt(pos) == '-') {
            pos++;
        }
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        if (pos == start) {
            throw new IllegalArgumentException("Invalid number at position " + pos);
        }
        return Double.parseDouble(input.substring(start, pos));
    }

    private void expect(char c) {
        if (pos >= input.length() || input.charAt(pos) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private char peek() {
        if (pos >= input.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        return input.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }
}
