package com.bear.kernel.target.python;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects dynamic import patterns in Python source files using Python's AST module.
 * 
 * Phase P: Detection only, no enforcement. Dynamic imports are identified but do not
 * cause boundary bypass failures.
 * 
 * Detected patterns:
 * - importlib.import_module(...)
 * - __import__(...)
 * - importlib.util.spec_from_file_location(...)
 */
public class PythonDynamicImportDetector {

    private static final String PYTHON_SCRIPT = """
        import ast
        import sys
        import json
        
        def detect_dynamic_imports(source_code):
            dynamic_imports = []
            try:
                tree = ast.parse(source_code)
                for node in ast.walk(tree):
                    if isinstance(node, ast.Call):
                        # Check for importlib.import_module(...)
                        if isinstance(node.func, ast.Attribute):
                            if (isinstance(node.func.value, ast.Name) and 
                                node.func.value.id == 'importlib' and 
                                node.func.attr == 'import_module'):
                                dynamic_imports.append({
                                    'pattern': 'importlib.import_module',
                                    'line': node.lineno,
                                    'col': node.col_offset
                                })
                            # Check for importlib.util.spec_from_file_location(...)
                            elif (isinstance(node.func.value, ast.Attribute) and
                                  isinstance(node.func.value.value, ast.Name) and
                                  node.func.value.value.id == 'importlib' and
                                  node.func.value.attr == 'util' and
                                  node.func.attr == 'spec_from_file_location'):
                                dynamic_imports.append({
                                    'pattern': 'importlib.util.spec_from_file_location',
                                    'line': node.lineno,
                                    'col': node.col_offset
                                })
                        # Check for __import__(...)
                        elif isinstance(node.func, ast.Name) and node.func.id == '__import__':
                            dynamic_imports.append({
                                'pattern': '__import__',
                                'line': node.lineno,
                                'col': node.col_offset
                            })
            except SyntaxError:
                # If parsing fails, return empty list
                pass
            
            return dynamic_imports
        
        if __name__ == '__main__':
            source = sys.stdin.read()
            dynamic_imports = detect_dynamic_imports(source)
            print(json.dumps(dynamic_imports))
        """;

    /**
     * Detects all dynamic import patterns in the given Python source file.
     * 
     * Phase P: Detection only, no enforcement. Returns list for advisory purposes.
     * 
     * @param filePath Path to the Python source file (used for error reporting)
     * @param content The Python source code content
     * @return List of DynamicImport objects with line/column information
     * @throws IOException if Python execution fails or output cannot be parsed
     */
    public List<DynamicImport> detectDynamicImports(Path filePath, String content) throws IOException {
        // Handle empty files
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }

        try {
            // Execute Python script with source as stdin
            ProcessBuilder pb = new ProcessBuilder("python3", "-c", PYTHON_SCRIPT);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Write source to stdin
            process.getOutputStream().write(content.getBytes());
            process.getOutputStream().close();

            // Read JSON output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Python AST dynamic import detection failed for " + filePath + ": exit code " + exitCode);
            }

            // Parse JSON output
            return parseDynamicImportsJson(output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python AST dynamic import detection interrupted for " + filePath, e);
        }
    }

    /**
     * Parses the JSON output from the Python script into DynamicImport objects.
     */
    private List<DynamicImport> parseDynamicImportsJson(String json) throws IOException {
        List<DynamicImport> imports = new ArrayList<>();
        
        // Simple JSON parsing (avoiding external dependencies)
        // Expected format: [{"pattern":"importlib.import_module","line":1,"col":0}, ...]
        json = json.trim();
        if (json.equals("[]")) {
            return imports;
        }

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IOException("Invalid JSON output from Python AST dynamic import detector: " + json);
        }

        // Remove outer brackets
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return imports;
        }

        // Split by objects (simple approach for our controlled output)
        int depth = 0;
        int start = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
                if (depth == 1) {
                    start = i;
                }
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String obj = json.substring(start, i + 1);
                    imports.add(parseDynamicImportObject(obj));
                }
            }
        }

        return imports;
    }

    /**
     * Parses a single JSON object into a DynamicImport.
     */
    private DynamicImport parseDynamicImportObject(String obj) throws IOException {
        // Extract fields from JSON object
        String pattern = extractJsonString(obj, "pattern");
        int line = extractJsonInt(obj, "line");
        int col = extractJsonInt(obj, "col");

        return new DynamicImport(pattern, line, col);
    }

    private String extractJsonString(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IOException("Missing key: " + key);
        }
        start += pattern.length();
        
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        // Check if it's a quoted string
        if (start >= json.length() || json.charAt(start) != '"') {
            throw new IOException("Expected string value for key: " + key);
        }
        
        start++; // Skip opening quote
        int end = json.indexOf("\"", start);
        if (end == -1) {
            throw new IOException("Malformed JSON string for key: " + key);
        }
        return json.substring(start, end);
    }

    private int extractJsonInt(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IOException("Missing key: " + key);
        }
        start += pattern.length();
        
        // Find the next comma or closing brace
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        if (end == -1) {
            throw new IOException("Malformed JSON int for key: " + key);
        }
        
        String value = json.substring(start, end).trim();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid integer for key: " + key + ", value: " + value, e);
        }
    }

    /**
     * Record representing a dynamic import pattern with location information.
     * 
     * @param pattern The dynamic import pattern detected (e.g., "importlib.import_module", "__import__")
     * @param lineNumber Line number in source (1-indexed)
     * @param columnNumber Column number in source (0-indexed, as per Python AST)
     */
    public record DynamicImport(String pattern, int lineNumber, int columnNumber) {
    }
}
