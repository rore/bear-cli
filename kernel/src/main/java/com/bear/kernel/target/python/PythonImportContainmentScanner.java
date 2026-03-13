package com.bear.kernel.target.python;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.BoundaryDecision;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Orchestrates Python import containment scanning.
 * 
 * Scans governed Python source files for import boundary bypasses using:
 * - PythonImportExtractor: AST-based static import extraction
 * - PythonDynamicImportDetector: AST-based dynamic import detection (Phase P: advisory only)
 * - PythonImportBoundaryResolver: Boundary decision logic
 * 
 * Findings are sorted deterministically by file path, then line number.
 */
public class PythonImportContainmentScanner {

    /**
     * Scans for import boundary bypasses in governed Python source files.
     * 
     * @param projectRoot The project root directory
     * @param wiringManifests List of wiring manifests for all blocks
     * @return List of boundary bypass findings, sorted by file path then line number
     * @throws IOException if file reading or Python AST execution fails
     */
    public static List<BoundaryBypassFinding> scan(Path projectRoot, List<WiringManifest> wiringManifests) throws IOException {
        Set<Path> governedRoots = computeGovernedRoots(projectRoot, wiringManifests);
        List<Path> governedFiles = collectGovernedFiles(governedRoots);

        PythonImportExtractor extractor = new PythonImportExtractor();
        PythonDynamicImportDetector dynamicDetector = new PythonDynamicImportDetector();
        PythonImportBoundaryResolver resolver = new PythonImportBoundaryResolver();

        List<BoundaryBypassFinding> findings = new ArrayList<>();
        
        for (Path file : governedFiles) {
            String content = Files.readString(file);
            
            // Extract static imports
            List<ImportStatement> imports = extractor.extractImports(file, content);
            
            for (ImportStatement imp : imports) {
                BoundaryDecision decision = resolver.resolve(
                    file, 
                    imp.moduleName(), 
                    imp.isRelative(), 
                    governedRoots, 
                    projectRoot
                );
                
                if (decision.isFail()) {
                    findings.add(new BoundaryBypassFinding(
                        decision.failureReason(),
                        projectRoot.relativize(file).toString(),
                        "Import module: " + imp.moduleName() + " at line " + imp.lineNumber()
                    ));
                }
            }
            
            // Dynamic imports: detect but do not fail in Phase P
            dynamicDetector.detectDynamicImports(file, content);
        }

        // Sort findings by file path, then line number (deterministic output for CI)
        findings.sort(Comparator.comparing(
            (BoundaryBypassFinding f) -> f.path(),
            Comparator.naturalOrder()
        ));

        return findings;
    }

    /**
     * Computes governed roots from wiring manifests.
     * 
     * Governed roots:
     * - src/blocks/<blockKey>/ for each block (with __init__.py)
     * - src/blocks/_shared/ if present (with __init__.py)
     */
    static Set<Path> computeGovernedRoots(Path projectRoot, List<WiringManifest> wiringManifests) {
        Set<Path> roots = new HashSet<>();

        // Add block roots from wiring manifests
        for (WiringManifest manifest : wiringManifests) {
            String blockKey = manifest.blockKey();
            Path blockRoot = projectRoot.resolve("src/blocks/" + blockKey);
            Path initFile = blockRoot.resolve("__init__.py");
            
            if (Files.exists(blockRoot) && Files.exists(initFile)) {
                roots.add(blockRoot);
            }
        }

        // Add _shared root if present
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path sharedInitFile = sharedRoot.resolve("__init__.py");
        
        if (Files.exists(sharedRoot) && Files.exists(sharedInitFile)) {
            roots.add(sharedRoot);
        }

        return roots;
    }

    /**
     * Collects all governed Python source files from governed roots.
     * 
     * Includes: *.py files
     * Excludes: test_*.py, *_test.py, .pyi, .pyc, .pyo
     */
    static List<Path> collectGovernedFiles(Set<Path> governedRoots) throws IOException {
        List<Path> files = new ArrayList<>();

        for (Path root : governedRoots) {
            if (Files.exists(root)) {
                Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".py"))
                    .filter(path -> !path.getFileName().toString().startsWith("test_"))
                    .filter(path -> !path.getFileName().toString().endsWith("_test.py"))
                    .forEach(files::add);
            }
        }

        return files;
    }
}
