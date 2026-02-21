package com.bear.kernel.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlockIdentityCanonicalizer {
    private BlockIdentityCanonicalizer() {
    }

    public static String canonicalizeBlockKey(String raw) {
        List<String> tokens = canonicalTokens(raw);
        if (tokens.isEmpty()) {
            return "block";
        }
        return String.join("-", tokens);
    }

    public static List<String> canonicalTokens(String raw) {
        String value = raw == null ? "" : raw;
        String adjusted = value
            .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
            .replaceAll("[^A-Za-z0-9]+", " ")
            .trim();
        if (adjusted.isEmpty()) {
            return List.of();
        }
        String[] parts = adjusted.split("\\s+");
        ArrayList<String> tokens = new ArrayList<>(parts.length);
        for (String part : parts) {
            tokens.add(part.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tokens);
    }
}
