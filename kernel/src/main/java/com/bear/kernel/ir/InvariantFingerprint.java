package com.bear.kernel.ir;

import java.util.ArrayList;
import java.util.List;

public final class InvariantFingerprint {
    private InvariantFingerprint() {
    }

    public static String canonicalKey(BearIr.Invariant invariant) {
        String kind = invariant.kind().name().toLowerCase();
        String scope = invariant.scope().name().toLowerCase();
        String field = escapeToken(invariant.field());
        String params = canonicalParams(invariant);
        return "kind=" + kind + "|scope=" + scope + "|field=" + field + "|params=" + params;
    }

    private static String canonicalParams(BearIr.Invariant invariant) {
        BearIr.InvariantParams params = invariant.params();
        String value = params == null ? null : params.value();
        List<String> values = params == null || params.values() == null ? List.of() : params.values();
        return switch (invariant.kind()) {
            case NON_NEGATIVE, NON_EMPTY -> "none";
            case EQUALS -> "value=" + escapeToken(value);
            case ONE_OF -> {
                ArrayList<String> sorted = new ArrayList<>(values);
                sorted.sort(String::compareTo);
                yield "values=" + escapeCsv(sorted);
            }
        };
    }

    private static String escapeCsv(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append(escapeToken(values.get(i)));
        }
        return out.toString();
    }

    private static String escapeToken(String value) {
        if (value == null) {
            return "<null>";
        }
        return value
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace(",", "\\,")
            .replace("=", "\\=");
    }
}
