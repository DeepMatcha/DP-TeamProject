package com.holub.life.plugin.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The small part of JSON the snapshot format needs: objects, arrays, strings, whole numbers,
 * booleans and null. Values are Map, List, String, Long, Boolean or null.
 */
final class Json {
    private final String text;
    private int at;

    private Json(String text) { this.text = text; }

    static Object parse(String text) throws IOException {
        Json json = new Json(text);
        Object value = json.value();
        json.skipSpace();
        if (json.at != text.length()) throw json.error("Unexpected text after the document");
        return value;
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            String separator = "";
            for (var entry : map.entrySet()) {
                out.append(separator);
                writeString((String) entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out);
                separator = ",";
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            String separator = "";
            for (Object item : list) {
                out.append(separator);
                write(item, out);
                separator = ",";
            }
            out.append(']');
        } else if (value instanceof String string) {
            writeString(string, out);
        } else {
            out.append(value);
        }
    }

    private static void writeString(String string, StringBuilder out) {
        out.append('"');
        for (char c : string.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private Object value() throws IOException {
        skipSpace();
        if (at >= text.length()) throw error("Unexpected end");
        char c = text.charAt(at);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
        if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
        if (text.startsWith("null", at)) { at += 4; return null; }
        return number();
    }

    private Map<String, Object> object() throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        at++;
        skipSpace();
        if (peek() == '}') { at++; return map; }
        while (true) {
            skipSpace();
            if (peek() != '"') throw error("Expected a member name");
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            if (peek() == ',') { at++; continue; }
            expect('}');
            return map;
        }
    }

    private List<Object> array() throws IOException {
        List<Object> list = new ArrayList<>();
        at++;
        skipSpace();
        if (peek() == ']') { at++; return list; }
        while (true) {
            list.add(value());
            skipSpace();
            if (peek() == ',') { at++; continue; }
            expect(']');
            return list;
        }
    }

    private String string() throws IOException {
        StringBuilder out = new StringBuilder();
        at++;
        while (true) {
            if (at >= text.length()) throw error("Unterminated string");
            char c = text.charAt(at++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (at >= text.length()) throw error("Unterminated escape");
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (at + 4 > text.length()) throw error("Short unicode escape");
                    try {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    } catch (NumberFormatException bad) {
                        throw error("Bad unicode escape");
                    }
                    at += 4;
                }
                default -> throw error("Unknown escape \\" + escaped);
            }
        }
    }

    private Long number() throws IOException {
        int start = at;
        if (peek() == '-') at++;
        while (at < text.length() && Character.isDigit(text.charAt(at))) at++;
        try {
            return Long.parseLong(text.substring(start, at));
        } catch (NumberFormatException bad) {
            throw error("Expected a value");
        }
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
    }

    private char peek() { return at < text.length() ? text.charAt(at) : '\0'; }

    private void expect(char c) throws IOException {
        if (peek() != c) throw error("Expected '" + c + "'");
        at++;
    }

    private IOException error(String message) { return new IOException(message + " at character " + at); }
}
