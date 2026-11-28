package com.holub.life.plugin.codec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed read access to one parsed JSON object. A missing or mistyped member is reported as
 * an IOException that names it, so the codec reads a document without unchecked casts.
 */
record JsonObject(Map<?, ?> members) {

    static JsonObject of(Object value, String what) throws IOException {
        return new JsonObject(as(Map.class, value, what));
    }

    /** The value as the expected JSON type, or an IOException saying what was expected where. */
    static <T> T as(Class<T> type, Object value, String what) throws IOException {
        if (!type.isInstance(value)) {
            throw new IOException("Expected " + type.getSimpleName() + " for " + what);
        }
        return type.cast(value);
    }

    String string(String key) throws IOException { return as(String.class, members.get(key), key); }

    long number(String key) throws IOException { return as(Long.class, members.get(key), key); }

    int integer(String key) throws IOException { return Math.toIntExact(number(key)); }

    boolean bool(String key) throws IOException { return as(Boolean.class, members.get(key), key); }

    List<?> array(String key) throws IOException { return as(List.class, members.get(key), key); }

    JsonObject object(String key) throws IOException { return of(members.get(key), key); }

    /** Every member as a string-to-string entry, in document order. */
    Map<String, String> strings() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : members.entrySet()) {
            String key = as(String.class, entry.getKey(), "a member name");
            result.put(key, as(String.class, entry.getValue(), key));
        }
        return result;
    }
}
