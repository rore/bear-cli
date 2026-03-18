package com.bear.kernel.target.python;

import com.bear.kernel.target.UndeclaredReachFinding;
import com.bear.kernel.target.WiringManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Enforces dynamic import facility detection that Phase P only detected as advisory.
 * 
 * Wraps PythonDynamicImportDetector and promotes advisory findings to hard failures.
 * Detects:
 * - importlib.import_module(...)
 * - __import__(...)
 * - sys.path.append(...)
 * - sys.path.insert(...)
 * - sys.path = [...]
 * 
 * Excludes patterns inside `if TYPE_CHECKING:` blocks and test files.
 * Findings map to CODE=BOUNDARY_BYPASS, exit code 7.
 */
public class PythonDynamicImportEnforcer {

    private static final PythonDynamicImportDetector DETECTOR = new PythonDynamicImportDetector();

    /**
     * Scans governed Python source files for dynamic import facility usage.
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
            List<PythonDynamicImportDetector.DynamicImport> dynamicImports = 
                DETECTOR.detectDynamicImports(file, content);
            
            String relativePath = projectRoot.relativize(file).toString();
            for (PythonDynamicImportDetector.DynamicImport imp : dynamicImports) {
                findings.add(new UndeclaredReachFinding(relativePath, imp.pattern()));
            }
        }

        // Sort by path then surface
        findings.sort(Comparator.comparing(UndeclaredReachFinding::path)
            .thenComparing(UndeclaredReachFinding::surface));

        return findings;
    }
}
