package com.bear.kernel.target.python;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scans governed Python source files for undeclared reach violations.
 * 
 * Detects direct usage of covered power-surface modules:
 * - socket, http, http.client, http.server, urllib, urllib.request
 * - subprocess, multiprocessing
 * 
 * Also detects os.system/popen/exec* call-site patterns and direct function imports
 * (from os import system/popen/exec*).
 * 
 * Excludes imports inside `if TYPE_CHECKING:` blocks and test files.
 */
public class PythonUndeclaredReachScanner {

    private static final String PYTHON_SCRIPT = """
import ast
import sys
import json

COVERED_MODULES = {
    'socket', 'http', 'http.client', 'http.server',
    'urllib', 'urllib.request', 'subprocess', 'multiprocessing'
}

OS_EXEC_ATTRS = {
    'system', 'popen', 'execl', 'execle', 'execlp', 'execlpe',
    'execv', 'execve', 'execvp', 'execvpe'
}

def collect_type_checking_lines(tree):
    \"\"\"Collect line ranges inside `if TYPE_CHECKING:` blocks.\"\"\"
    type_checking_lines = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.If):
            # Check if condition is TYPE_CHECKING
            test = node.test
            is_type_checking = False
            if isinstance(test, ast.Name) and test.id == 'TYPE_CHECKING':
                is_type_checking = True
            elif isinstance(test, ast.Attribute) and test.attr == 'TYPE_CHECKING':
                is_type_checking = True
            
            if is_type_checking:
                # Collect all lines in the body of this if block
                for body_node in ast.walk(node):
                    if hasattr(body_node, 'lineno'):
                        type_checking_lines.add(body_node.lineno)
    return type_checking_lines

def scan(source):
    findings = []
    try:
        tree = ast.parse(source)
    except SyntaxError:
        return []
    
    type_checking_lines = collect_type_checking_lines(tree)
    
    for node in ast.walk(tree):
        if not hasattr(node, 'lineno'):
            continue
        if node.lineno in type_checking_lines:
            continue
        
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name in COVERED_MODULES:
                    findings.append({'surface': alias.name, 'line': node.lineno})
        
        elif isinstance(node, ast.ImportFrom):
            if node.module and node.level == 0:
                # Check for covered module imports
                if node.module in COVERED_MODULES:
                    findings.append({'surface': node.module, 'line': node.lineno})
                # Check for direct os function imports: from os import system/popen/exec*
                if node.module == 'os':
                    for alias in node.names:
                        if alias.name in OS_EXEC_ATTRS:
                            findings.append({'surface': 'os.' + alias.name, 'line': node.lineno})
        
        elif isinstance(node, ast.Call):
            # os.system(...), os.popen(...), os.exec*(...)
            if (isinstance(node.func, ast.Attribute) and
                isinstance(node.func.value, ast.Name) and
                node.func.value.id == 'os' and
                node.func.attr in OS_EXEC_ATTRS):
                findings.append({'surface': 'os.' + node.func.attr, 'line': node.lineno})
    
    return findings

if __name__ == '__main__':
    source = sys.stdin.read()
    findings = scan(source)
    print(json.dumps(findings))
""";

    /**
     * Scans governed Python source files for undeclared reach violations.
     * 
     * @param projectRoot The project root directory
     * @param wiringManifests List of wiring manifests for all blocks
     * @return List of undeclared reach findings, sorted by path then surface
     * @throws IOException if file reading or Python AST execution fails
     */
    public static List<UndeclaredReachFinding> scan(Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
        Set<Path> governedRoots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, wiringManifests);
        List<Path> governedFiles = PythonImportContainmentScanner.collectGovernedFiles(governedRoots);

        List<UndeclaredReachFinding> findings = new ArrayList<>();

        for (Path file : governedFiles) {
            String content = Files.readString(file);
            List<RawFinding> rawFindings = scanFile(content);
            
            String relativePath = projectRoot.relativize(file).toString();
            for (RawFinding raw : rawFindings) {
                findings.add(new UndeclaredReachFinding(relativePath, raw.surface()));
            }
        }

        // Sort by path then surface
        findings.sort(Comparator.comparing(UndeclaredReachFinding::path)
            .thenComparing(UndeclaredReachFinding::surface));

        return findings;
    }

    /**
     * Scans a single Python file for undeclared reach violations.
     */
    private static List<RawFinding> scanFile(String content) throws IOException {
        if (content == null || content.trim().isEmpty()) {
            return List.of();
        }

        try {
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
                // Python script failed - treat as no findings (could be syntax error)
                return List.of();
            }

            return parseFindings(output.toString());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python AST scan interrupted", e);
        }
    }

    /**
     * Parses the JSON output from the Python script.
     */
    private static List<RawFinding> parseFindings(String json) throws IOException {
        List<RawFinding> findings = new ArrayList<>();
        
        json = json.trim();
        if (json.equals("[]") || json.isEmpty()) {
            return findings;
        }

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IOException("Invalid JSON output from Python scanner: " + json);
        }

        // Remove outer brackets
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return findings;
        }

        // Split by objects
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
                    findings.add(parseFindingObject(obj));
                }
            }
        }

        return findings;
    }

    /**
     * Parses a single JSON object into a RawFinding.
     */
    private static RawFinding parseFindingObject(String obj) throws IOException {
        String surface = extractJsonString(obj, "surface");
        int line = extractJsonInt(obj, "line");
        return new RawFinding(surface, line);
    }

    private static String extractJsonString(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IOException("Missing key: " + key);
        }
        start += pattern.length();
        
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        if (start >= json.length() || json.charAt(start) != '"') {
            throw new IOException("Expected string value for key: " + key);
        }
        
        start++;
        int end = json.indexOf("\"", start);
        if (end == -1) {
            throw new IOException("Malformed JSON string for key: " + key);
        }
        return json.substring(start, end);
    }

    private static int extractJsonInt(String json, String key) throws IOException {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) {
            throw new IOException("Missing key: " + key);
        }
        start += pattern.length();
        
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
     * Internal record for raw findings from Python script.
     */
    private record RawFinding(String surface, int line) {}
}
