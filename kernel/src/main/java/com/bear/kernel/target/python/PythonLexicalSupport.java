package com.bear.kernel.target.python;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Lexical support for Python naming conventions.
 * Handles kebab-case to snake_case conversions.
 */
public class PythonLexicalSupport {

    /**
     * Converts a kebab-case string to snake_case.
     * Example: "my-block" -> "my_block"
     */
    public static String kebabToSnake(String kebab) {
        if (kebab == null || kebab.isEmpty()) {
            return kebab;
        }
        return kebab.replace('-', '_');
    }

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
     * Derives block name from block key (kebab-case).
     * For Python, this converts to snake_case.
     * Example: "user-auth" -> "user_auth"
     */
    public static String deriveBlockName(String blockKey) {
        return kebabToSnake(blockKey);
    }
}
