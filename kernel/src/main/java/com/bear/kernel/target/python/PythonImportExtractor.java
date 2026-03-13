package com.bear.kernel.target.python;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts import statements from Python source files using Python's AST module.
 * 
 * This extractor uses Python's built-in ast module via ProcessBuilder to parse
 * Python source and extract all import statements with their locations.
 * 
 * Supported patterns:
 * - import x
 * - import x as y
 * - import x.y.z
 * - from x import y
 * - from x import y as z
 * - from x import *
 * - from . import x (relative)
 * - from .. import x (relative)
 * - from .submodule import x (relative)
 */
public class PythonImportExtractor {

    private static final String PYTHON_SCRIPT = """
        import ast
        import sys
        import json
        
        def extract_imports(source_code):
            imports = []
            try:
                tree = ast.parse(source_code)
                for node in ast.walk(tree):
                    if isinstance(node, ast.Import):
                        for alias in node.names:
                            imports.append({
                                'module': alias.name,
                                'is_relative': False,
                                'line': node.lineno,
                                'col': node.col_offset
                            })
                    elif isinstance(node, ast.ImportFrom):
                        # Handle relative imports
                        level = node.level if node.level else 0
                        module = node.module if node.module else ''
                        
                        # Construct the full module name for relative imports
                        if level > 0:
                            prefix = '.' * level
                            full_module = prefix + module if module else prefix
                            is_relative = True
                        else:
                            full_module = module
                            is_relative = False
                        
                        # For "from x import y", we track the module x, not the imported names
                        imports.append({
                            'module': full_module,
                            'is_relative': is_relative,
                            'line': node.lineno,
                            'col': node.col_offset
                        })
            except SyntaxError:
                # If parsing fails, return empty list
                pass
            
            return imports
        
        if __name__ == '__main__':
            source = sys.stdin.read()
            imports = extract_imports(source)
            print(json.dumps(imports))
        """;

    /**
     * Extracts all import statements from the given Python source file.
     * 
     * @param filePath Path to the Python source file (used for error reporting)
     * @param content The Python source code content
     * @return List of ImportStatement objects with line/column information
     * @throws IOException if Python execution fails or output cannot be parsed
     */
    public List<ImportStatement> extractImports(Path filePath, String content) throws IOException {
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
                throw new IOException("Python AST extraction failed for " + filePath + ": exit code " + exitCode);
            }

            // Parse JSON output
            return parseImportsJson(output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python AST extraction interrupted for " + filePath, e);
        }
    }

    /**
     * Parses the JSON output from the Python script into ImportStatement objects.
     */
    private List<ImportStatement> parseImportsJson(String json) throws IOException {
        List<ImportStatement> imports = new ArrayList<>();
        
        // Simple JSON parsing (avoiding external dependencies)
        // Expected format: [{"module":"os","is_relative":false,"line":1,"col":0}, ...]
        json = json.trim();
        if (json.equals("[]")) {
            return imports;
        }

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IOException("Invalid JSON output from Python AST extractor: " + json);
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
                    imports.add(parseImportObject(obj));
                }
            }
        }

        return imports;
    }

    /**
     * Parses a single JSON object into an ImportStatement.
     */
    private ImportStatement parseImportObject(String obj) throws IOException {
        // Extract fields from JSON object
        String module = extractJsonString(obj, "module");
        boolean isRelative = extractJsonBoolean(obj, "is_relative");
        int line = extractJsonInt(obj, "line");
        int col = extractJsonInt(obj, "col");

        return new ImportStatement(module, isRelative, line, col);
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

    private boolean extractJsonBoolean(String json, String key) throws IOException {
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
            throw new IOException("Malformed JSON boolean for key: " + key);
        }
        
        String value = json.substring(start, end).trim();
        return Boolean.parseBoolean(value);
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
}
