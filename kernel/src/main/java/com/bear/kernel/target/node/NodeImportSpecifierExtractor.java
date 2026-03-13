package com.bear.kernel.target.node;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodeImportSpecifierExtractor {

    // Patterns for different import/export statements
    private static final Pattern IMPORT_NAMED = Pattern.compile(
        "import\\s*\\{([^}]+)\\}\\s*from\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT_NAMESPACE = Pattern.compile(
        "import\\s*\\*\\s*as\\s+(\\w+)\\s+from\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT_DEFAULT = Pattern.compile(
        "import\\s+(\\w+)\\s+from\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern IMPORT_SIDE_EFFECT = Pattern.compile(
        "import\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern EXPORT_NAMED = Pattern.compile(
        "export\\s*\\{([^}]+)\\}\\s*from\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    private static final Pattern EXPORT_ALL = Pattern.compile(
        "export\\s*\\*\\s*from\\s*['\"]([^'\"]+)['\"]",
        Pattern.MULTILINE
    );

    /**
     * Extracts all import and export specifiers from TypeScript source.
     * Returns a list of ImportSpecifier with line/column numbers.
     */
    public List<ImportSpecifier> extractImports(String source, String content) {
        List<ImportSpecifier> specifiers = new ArrayList<>();

        // Extract named imports: import { x } from "path"
        extractPattern(content, IMPORT_NAMED, specifiers, "named");

        // Extract namespace imports: import * as x from "path"
        extractPattern(content, IMPORT_NAMESPACE, specifiers, "namespace");

        // Extract default imports: import x from "path"
        extractPattern(content, IMPORT_DEFAULT, specifiers, "default");

        // Extract side-effect imports: import "path"
        extractPattern(content, IMPORT_SIDE_EFFECT, specifiers, "side-effect");

        // Extract named exports: export { x } from "path"
        extractPattern(content, EXPORT_NAMED, specifiers, "export-named");

        // Extract export * from "path"
        extractPattern(content, EXPORT_ALL, specifiers, "export-all");

        return specifiers;
    }

    private void extractPattern(String content, Pattern pattern, List<ImportSpecifier> specifiers, String kind) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int lineNumber = getLineNumber(content, matcher.start());
            int columnNumber = getColumnNumber(content, matcher.start());
            String specifier = matcher.group(matcher.groupCount());
            specifiers.add(new ImportSpecifier(specifier, lineNumber, columnNumber, kind));
        }
    }

    private int getLineNumber(String content, int position) {
        int line = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int getColumnNumber(String content, int position) {
        int column = 1;
        for (int i = position - 1; i >= 0 && i < content.length(); i--) {
            if (content.charAt(i) == '\n') {
                break;
            }
            column++;
        }
        return column;
    }

    /**
     * Record representing an import/export specifier with location information.
     */
    public record ImportSpecifier(String specifier, int lineNumber, int columnNumber, String kind) {
    }
}
