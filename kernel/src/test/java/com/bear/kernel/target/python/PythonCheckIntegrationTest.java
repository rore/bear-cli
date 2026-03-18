package com.bear.kernel.target.python;

import com.bear.kernel.target.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Python check pipeline scanners.
 * Tests undeclared reach, dynamic execution, and dynamic import enforcement.
 */
class PythonCheckIntegrationTest {

    private final PythonTarget target = new PythonTarget();

    // ========== Undeclared Reach Tests (Exit Code 6) ==========

    @Test
    void checkUndeclaredReach_socketImport_producesFindings() throws Exception {
        Path fixture = getFixturePath("check-undeclared-reach");
        
        List<UndeclaredReachFinding> findings = target.scanUndeclaredReach(fixture);
        
        assertFalse(findings.isEmpty(), "Should detect socket import as undeclared reach");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("socket")),
            "Should have finding with surface 'socket'");
    }

    @Test
    void checkOsSystem_producesFindings() throws Exception {
        Path fixture = getFixturePath("check-os-system");
        
        List<UndeclaredReachFinding> findings = target.scanUndeclaredReach(fixture);
        
        assertFalse(findings.isEmpty(), "Should detect os.system call as undeclared reach");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os.system")),
            "Should have finding with surface 'os.system'");
    }

    @Test
    void checkFromOsImport_producesFindings() throws Exception {
        Path fixture = getFixturePath("check-from-os-import");
        
        List<UndeclaredReachFinding> findings = target.scanUndeclaredReach(fixture);
        
        assertFalse(findings.isEmpty(), "Should detect 'from os import system' as undeclared reach");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("os.system")),
            "Should have finding with surface 'os.system'");
    }

    // ========== Dynamic Execution Tests (Exit Code 7, CODE=BOUNDARY_BYPASS) ==========

    @Test
    void checkDynamicExec_evalCall_producesFindings() throws Exception {
        Path fixture = getFixturePath("check-dynamic-exec");
        List<WiringManifest> manifests = loadWiringManifests(fixture);
        
        List<UndeclaredReachFinding> findings = target.scanForbiddenReflectionDispatch(fixture, manifests);
        
        assertFalse(findings.isEmpty(), "Should detect eval() call as boundary bypass");
        assertTrue(findings.stream().anyMatch(f -> f.surface().equals("eval")),
            "Should have finding with surface 'eval'");
    }

    // ========== Dynamic Import Tests (Exit Code 7, CODE=BOUNDARY_BYPASS) ==========

    @Test
    void checkDynamicImport_importlibImportModule_producesFindings() throws Exception {
        Path fixture = getFixturePath("check-dynamic-import");
        List<WiringManifest> manifests = loadWiringManifests(fixture);
        
        List<UndeclaredReachFinding> findings = target.scanForbiddenReflectionDispatch(fixture, manifests);
        
        assertFalse(findings.isEmpty(), "Should detect importlib.import_module as boundary bypass");
        assertTrue(findings.stream().anyMatch(f -> f.surface().contains("importlib")),
            "Should have finding with surface containing 'importlib'");
    }

    @Test
    void checkSysPathMutation_producesFindings() throws Exception {
        Path fixture = getFixturePath("check-sys-path-mutation");
        List<WiringManifest> manifests = loadWiringManifests(fixture);
        
        List<UndeclaredReachFinding> findings = target.scanForbiddenReflectionDispatch(fixture, manifests);
        
        assertFalse(findings.isEmpty(), "Should detect sys.path mutation as boundary bypass");
        assertTrue(findings.stream().anyMatch(f -> f.surface().contains("sys.path")),
            "Should have finding with surface containing 'sys.path'");
    }

    // ========== TYPE_CHECKING Exclusion Tests (Exit Code 0) ==========

    @Test
    void checkTypeCheckingExcluded_noFindings() throws Exception {
        Path fixture = getFixturePath("check-type-checking-excluded");
        
        List<UndeclaredReachFinding> undeclaredReach = target.scanUndeclaredReach(fixture);
        
        assertTrue(undeclaredReach.isEmpty(), 
            "TYPE_CHECKING imports should be excluded from undeclared reach findings");
    }

    @Test
    void checkTypeCheckingExcluded_noForbiddenReflectionFindings() throws Exception {
        Path fixture = getFixturePath("check-type-checking-excluded");
        List<WiringManifest> manifests = loadWiringManifests(fixture);
        
        List<UndeclaredReachFinding> findings = target.scanForbiddenReflectionDispatch(fixture, manifests);
        
        assertTrue(findings.isEmpty(), 
            "TYPE_CHECKING blocks should be excluded from forbidden reflection findings");
    }

    // ========== Fixture Structure Validation ==========

    @Test
    void checkUndeclaredReachFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-undeclared-reach");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("uv.lock")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/__init__.py")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/service.py")));
        assertTrue(Files.exists(fixture.resolve("build/generated/bear/wiring/my-block.wiring.json")));
    }

    @Test
    void checkDynamicExecFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-dynamic-exec");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/service.py")));
        String content = Files.readString(fixture.resolve("src/blocks/my-block/impl/service.py"));
        assertTrue(content.contains("eval("), "Service should contain eval() call");
    }

    @Test
    void checkDynamicImportFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-dynamic-import");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/service.py")));
        String content = Files.readString(fixture.resolve("src/blocks/my-block/impl/service.py"));
        assertTrue(content.contains("importlib.import_module"), "Service should contain importlib.import_module");
    }

    @Test
    void checkSysPathMutationFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-sys-path-mutation");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/service.py")));
        String content = Files.readString(fixture.resolve("src/blocks/my-block/impl/service.py"));
        assertTrue(content.contains("sys.path.append"), "Service should contain sys.path.append");
    }

    @Test
    void checkTypeCheckingExcludedFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-type-checking-excluded");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/service.py")));
        String content = Files.readString(fixture.resolve("src/blocks/my-block/impl/service.py"));
        assertTrue(content.contains("TYPE_CHECKING"), "Service should contain TYPE_CHECKING");
        assertTrue(content.contains("import socket"), "Service should contain socket import");
    }

    // ========== Clean Fixture Tests (Exit Code 0) ==========

    @Test
    void checkClean_noUndeclaredReachFindings() throws Exception {
        Path fixture = getFixturePath("check-clean");
        
        List<UndeclaredReachFinding> findings = target.scanUndeclaredReach(fixture);
        
        assertTrue(findings.isEmpty(), 
            "Clean fixture should have no undeclared reach findings");
    }

    @Test
    void checkClean_noForbiddenReflectionFindings() throws Exception {
        Path fixture = getFixturePath("check-clean");
        List<WiringManifest> manifests = loadWiringManifests(fixture);
        
        List<UndeclaredReachFinding> findings = target.scanForbiddenReflectionDispatch(fixture, manifests);
        
        assertTrue(findings.isEmpty(), 
            "Clean fixture should have no forbidden reflection findings");
    }

    @Test
    void checkClean_noBoundaryBypassFindings() throws Exception {
        Path fixture = getFixturePath("check-clean");
        List<WiringManifest> manifests = loadWiringManifests(fixture);
        
        List<BoundaryBypassFinding> findings = target.scanBoundaryBypass(fixture, manifests, java.util.Set.of());
        
        assertTrue(findings.isEmpty(), 
            "Clean fixture should have no boundary bypass findings");
    }

    @Test
    void checkCleanFixture_hasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("check-clean");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("uv.lock")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/__init__.py")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/__init__.py")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/my-block/impl/service.py")));
        assertTrue(Files.exists(fixture.resolve("build/generated/bear/wiring/my-block.wiring.json")));
    }

    @Test
    void checkClean_serviceHasNoViolations() throws Exception {
        Path fixture = getFixturePath("check-clean");
        String content = Files.readString(fixture.resolve("src/blocks/my-block/impl/service.py"));
        
        // Verify no violation patterns
        assertFalse(content.contains("import socket"), "Service should not import socket");
        assertFalse(content.contains("import subprocess"), "Service should not import subprocess");
        assertFalse(content.contains("eval("), "Service should not contain eval()");
        assertFalse(content.contains("exec("), "Service should not contain exec()");
        assertFalse(content.contains("compile("), "Service should not contain compile()");
        assertFalse(content.contains("importlib.import_module"), "Service should not use importlib.import_module");
        assertFalse(content.contains("__import__"), "Service should not use __import__");
        assertFalse(content.contains("sys.path"), "Service should not mutate sys.path");
        assertFalse(content.contains("os.system"), "Service should not use os.system");
        assertFalse(content.contains("os.popen"), "Service should not use os.popen");
    }

    @Test
    void checkClean_projectVerification_returnsResult() throws Exception {
        Path fixture = getFixturePath("check-clean");
        
        // Run project verification - may return BOOTSTRAP_IO if uv/poetry not available
        ProjectTestResult result = target.runProjectVerification(fixture, null);
        
        assertNotNull(result, "Project verification should return a result");
        assertNotNull(result.status(), "Result should have a status");
        
        // In CI without uv/poetry, expect BOOTSTRAP_IO (exit 74)
        // With uv/poetry available, expect PASSED or FAILED based on mypy
        assertTrue(
            result.status() == ProjectTestStatus.PASSED ||
            result.status() == ProjectTestStatus.FAILED ||
            result.status() == ProjectTestStatus.BOOTSTRAP_IO,
            "Status should be PASSED, FAILED, or BOOTSTRAP_IO, got: " + result.status()
        );
    }

    // ========== Helper Methods ==========

    private Path getFixturePath(String fixtureName) throws URISyntaxException {
        return Paths.get(
            Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/python/" + fixtureName)
            ).toURI()
        );
    }

    private List<WiringManifest> loadWiringManifests(Path projectRoot) throws IOException, ManifestParseException {
        Path wiringDir = projectRoot.resolve("build/generated/bear/wiring");
        if (!Files.isDirectory(wiringDir)) {
            return List.of();
        }
        
        java.util.ArrayList<WiringManifest> manifests = new java.util.ArrayList<>();
        try (var stream = Files.list(wiringDir)) {
            for (Path p : stream.filter(path -> path.toString().endsWith(".wiring.json")).toList()) {
                manifests.add(target.parseWiringManifest(p));
            }
        }
        return manifests;
    }
}
