package com.bear.kernel.target.node;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodeDynamicImportDetector {

    // Pattern for dynamic import: import(...)
    private static final Pattern DYNAMIC_IMPORT = Pattern.compile(
        "import\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)",
        Pattern.MULTILINE
    );

    /**
     * Detects all dynamic import expressions in TypeScript source.
     * Returns a list of DynamicImport with line/column numbers.
     */
    public List<DynamicImport> detectDynamicImports(String content) {
        List<DynamicImport> dynamicImports = new ArrayList<>();

        Matcher matcher = DYNAMIC_IMPORT.matcher(content);
        while (matcher.find()) {
            int lineNumber = getLineNumber(content, matcher.start());
            int columnNumber = getColumnNumber(content, matcher.start());
            String specifier = matcher.group(1);
            dynamicImports.add(new DynamicImport(specifier, lineNumber, columnNumber));
        }

        return dynamicImports;
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
     * Record representing a dynamic import with location information.
     */
    public record DynamicImport(String specifier, int lineNumber, int columnNumber) {
    }
}
