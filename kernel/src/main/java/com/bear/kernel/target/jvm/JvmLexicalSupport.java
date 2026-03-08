package com.bear.kernel.target.jvm;

import com.bear.kernel.identity.BlockIdentityCanonicalizer;
import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIr.FieldType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class JvmLexicalSupport {
    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "record", "sealed", "permits", "var", "yield", "non-sealed"
    );

    private JvmLexicalSupport() {
    }

    static String sanitizePackageSegment(String raw) {
        List<String> tokens = splitTokens(raw);
        if (tokens.isEmpty()) {
            return "block";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                out.append('.');
            }
            String segment = tokens.get(i);
            if (Character.isDigit(segment.charAt(0))) {
                segment = "_" + segment;
            }
            if (JAVA_KEYWORDS.contains(segment)) {
                segment = segment + "_";
            }
            out.append(segment);
        }
        return out.toString();
    }

    static String toPascalCase(String raw) {
        List<String> tokens = splitTokens(raw);
        if (tokens.isEmpty()) {
            return "Block";
        }
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            out.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        String value = out.toString();
        if (Character.isDigit(value.charAt(0))) {
            value = "_" + value;
        }
        if (JAVA_KEYWORDS.contains(value)) {
            value = value + "_";
        }
        return value;
    }

    static String toCamelCase(String raw) {
        List<String> tokens = splitTokens(raw);
        if (tokens.isEmpty()) {
            return "value";
        }
        StringBuilder out = new StringBuilder(tokens.get(0));
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            out.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        String value = out.toString();
        if (Character.isDigit(value.charAt(0))) {
            value = "_" + value;
        }
        if (JAVA_KEYWORDS.contains(value)) {
            value = value + "_";
        }
        return value;
    }

    static String toJavaType(FieldType type) {
        return switch (type) {
            case STRING -> "String";
            case DECIMAL -> "BigDecimal";
            case INT -> "Integer";
            case BOOL -> "Boolean";
            case ENUM -> "String";
        };
    }

    static String defaultLiteral(String javaType) {
        return switch (javaType) {
            case "String" -> "\"\"";
            case "BigDecimal" -> "BigDecimal.ZERO";
            case "Integer" -> "0";
            case "Boolean" -> "Boolean.FALSE";
            default -> "null";
        };
    }

    static String invariantRuleText(BearIr.Invariant invariant) {
        return switch (invariant.kind()) {
            case NON_NEGATIVE -> "non_negative";
            case NON_EMPTY -> "non_empty";
            case EQUALS -> "equals:" + escapeDelimitedForRule(invariant.params().value());
            case ONE_OF -> {
                StringBuilder out = new StringBuilder();
                List<String> values = invariant.params().values();
                out.append("one_of:").append(values.size());
                for (String value : values) {
                    String escaped = escapeDelimitedForRule(value);
                    out.append("|").append(escaped.getBytes(StandardCharsets.UTF_8).length).append("#").append(escaped);
                }
                yield out.toString();
            }
        };
    }

    static String oneOfSetLiteral(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "java.util.Set.of()";
        }
        StringBuilder out = new StringBuilder("java.util.Set.of(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(javaString(values.get(i)));
        }
        out.append(")");
        return out.toString();
    }

    static String javaString(String value) {
        StringBuilder out = new StringBuilder();
        out.append("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    private static String escapeDelimitedForRule(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("=", "\\=")
            .replace("#", "\\#")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static List<String> splitTokens(String raw) {
        return new ArrayList<>(BlockIdentityCanonicalizer.canonicalTokens(raw));
    }
}


