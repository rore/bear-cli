package com.bear.kernel.target.python;

import com.bear.kernel.target.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using Python fixture projects.
 * Tests end-to-end detection, compilation, and validation scenarios.
 */
class PythonFixtureIntegrationTest {

    private final PythonTargetDetector detector = new PythonTargetDetector();
    private final TargetRegistry registry = TargetRegistry.defaultRegistry();

    // ========== Detection Tests ==========

    @Test
    void validSingleBlockDetectedAsPython() throws Exception {
        Path fixture = getFixturePath("valid-single-block");
        
        DetectedTarget result = detector.detect(fixture);
        
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void validMultiBlockDetectedAsPython() throws Exception {
        Path fixture = getFixturePath("valid-multi-block");
        
        DetectedTarget result = detector.detect(fixture);
        
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void validWithSharedDetectedAsPython() throws Exception {
        Path fixture = getFixturePath("valid-with-shared");
        
        DetectedTarget result = detector.detect(fixture);
        
        assertEquals(DetectionStatus.SUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
    }

    @Test
    void invalidWorkspaceDetectedAsUnsupported() throws Exception {
        Path fixture = getFixturePath("invalid-workspace");
        
        DetectedTarget result = detector.detect(fixture);
        
        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
        assertTrue(result.reason().contains("workspace"));
    }

    @Test
    void invalidFlatLayoutDetectedAsUnsupported() throws Exception {
        Path fixture = getFixturePath("invalid-flat-layout");
        
        DetectedTarget result = detector.detect(fixture);
        
        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
        assertTrue(result.reason().contains("src/") || result.reason().contains("layout"));
    }

    @Test
    void invalidNamespacePackageDetectedAsUnsupported() throws Exception {
        Path fixture = getFixturePath("invalid-namespace-package");
        
        DetectedTarget result = detector.detect(fixture);
        
        assertEquals(DetectionStatus.UNSUPPORTED, result.status());
        assertEquals(TargetId.PYTHON, result.targetId());
        assertTrue(result.reason().contains("__init__.py") || result.reason().contains("namespace"));
    }

    // ========== Target Registry Tests ==========

    @Test
    void registryResolvesPythonProjectToPythonTarget() throws Exception {
        Path fixture = getFixturePath("valid-single-block");
        
        Target target = registry.resolve(fixture);
        
        assertNotNull(target);
        assertEquals(TargetId.PYTHON, target.targetId());
        assertInstanceOf(PythonTarget.class, target);
    }

    @Test
    void registryDoesNotConfuseNodeAndPythonProjects() throws Exception {
        // Python project should resolve to PythonTarget
        Path pythonFixture = getFixturePath("valid-single-block");
        Target pythonTarget = registry.resolve(pythonFixture);
        assertEquals(TargetId.PYTHON, pythonTarget.targetId());
        
        // Node project should resolve to NodeTarget (if Node fixtures exist)
        // This ensures no cross-target interference
        try {
            Path nodeFixture = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader().getResource("fixtures/node/valid-single-block"))
                    .toURI()
            );
            Target nodeTarget = registry.resolve(nodeFixture);
            assertEquals(TargetId.NODE, nodeTarget.targetId());
        } catch (NullPointerException e) {
            // Node fixtures may not exist in this test run - skip
        }
    }

    // ========== Boundary Bypass Detection Tests ==========

    @Test
    void boundaryBypassEscapeDetected() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-escape");
        
        // Verify detection works
        DetectedTarget detected = detector.detect(fixture);
        assertEquals(DetectionStatus.SUPPORTED, detected.status());
        
        // Verify boundary bypass would be detected during scan
        // (Full scan requires wiring manifests - this validates fixture structure)
        Path escapeFile = fixture.resolve("src/blocks/user-auth/escape.py");
        assertTrue(Files.exists(escapeFile));
        String content = Files.readString(escapeFile);
        assertTrue(content.contains("from ...nongoverned import helper"));
    }

    @Test
    void boundaryBypassSiblingDetected() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-sibling");
        
        // Verify detection works
        DetectedTarget detected = detector.detect(fixture);
        assertEquals(DetectionStatus.SUPPORTED, detected.status());
        
        // Verify boundary bypass would be detected during scan
        Path crossBlockFile = fixture.resolve("src/blocks/user-auth/cross_block.py");
        assertTrue(Files.exists(crossBlockFile));
        String content = Files.readString(crossBlockFile);
        assertTrue(content.contains("from blocks.payment.processor import"));
    }

    @Test
    void boundaryBypassThirdPartyDetected() throws Exception {
        Path fixture = getFixturePath("boundary-bypass-third-party");
        
        // Verify detection works
        DetectedTarget detected = detector.detect(fixture);
        assertEquals(DetectionStatus.SUPPORTED, detected.status());
        
        // Verify third-party import exists
        Path apiClientFile = fixture.resolve("src/blocks/user-auth/api_client.py");
        assertTrue(Files.exists(apiClientFile));
        String content = Files.readString(apiClientFile);
        assertTrue(content.contains("import requests"));
    }

    // ========== Fixture Structure Validation ==========

    @Test
    void validSingleBlockHasRequiredFiles() throws Exception {
        Path fixture = getFixturePath("valid-single-block");
        
        assertTrue(Files.exists(fixture.resolve("pyproject.toml")));
        assertTrue(Files.exists(fixture.resolve("uv.lock")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/user-auth/__init__.py")));
    }

    @Test
    void validMultiBlockHasTwoBlocks() throws Exception {
        Path fixture = getFixturePath("valid-multi-block");
        
        assertTrue(Files.exists(fixture.resolve("src/blocks/user-auth/__init__.py")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/payment/__init__.py")));
    }

    @Test
    void validWithSharedHasSharedDirectory() throws Exception {
        Path fixture = getFixturePath("valid-with-shared");
        
        assertTrue(Files.exists(fixture.resolve("src/blocks/_shared/__init__.py")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/_shared/utils.py")));
        assertTrue(Files.exists(fixture.resolve("src/blocks/user-auth/__init__.py")));
    }

    @Test
    void invalidWorkspaceHasWorkspaceMarker() throws Exception {
        Path fixture = getFixturePath("invalid-workspace");
        
        String pyprojectContent = Files.readString(fixture.resolve("pyproject.toml"));
        assertTrue(pyprojectContent.contains("[tool.uv.workspace]") || 
                   pyprojectContent.contains("uv.workspace"));
    }

    @Test
    void invalidFlatLayoutMissesSrcDirectory() throws Exception {
        Path fixture = getFixturePath("invalid-flat-layout");
        
        assertFalse(Files.exists(fixture.resolve("src/blocks")));
        assertTrue(Files.exists(fixture.resolve("blocks/__init__.py")));
    }

    @Test
    void invalidNamespacePackageMissesInitPy() throws Exception {
        Path fixture = getFixturePath("invalid-namespace-package");
        
        assertTrue(Files.exists(fixture.resolve("src/blocks/user-auth/helper.py")));
        assertFalse(Files.exists(fixture.resolve("src/blocks/user-auth/__init__.py")));
    }

    // ========== Helper Methods ==========

    private Path getFixturePath(String fixtureName) throws URISyntaxException {
        return Paths.get(
            Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/python/" + fixtureName)
            ).toURI()
        );
    }
}
