package com.bear.kernel.target.node;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TypeScriptLexicalSupport {

    /**
     * Converts a kebab-case string to PascalCase.
     * Example: "my-block" -> "MyBlock"
     */
    public static String kebabToPascal(String kebab) {
        if (kebab == null || kebab.isEmpty()) {
            return kebab;
        }
        return Arrays.stream(kebab.split("-"))
                .map(part -> part.isEmpty() ? "" : Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }

    /**
     * Converts a kebab-case string to camelCase.
     * Example: "my-block" -> "myBlock"
     */
    public static String kebabToCamel(String kebab) {
        if (kebab == null || kebab.isEmpty()) {
            return kebab;
        }
        String pascal = kebabToPascal(kebab);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /**
     * Derives block name from block key (kebab-case).
     * Example: "user-auth" -> "UserAuth"
     */
    public static String deriveBlockName(String blockKey) {
        return kebabToPascal(blockKey);
    }
}
