package com.bear.kernel.target.python;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PythonImportContainmentScannerTest {

    @Test
    void cleanProjectHasNoFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("user_auth_logic.py"), "# clean file\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty());
    }

    @Test
    void boundaryBypassDetected(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));
        
        Files.writeString(tempDir.resolve("src/blocks/user-auth/__init__.py"), "");
        Files.writeString(servicesDir.resolve("__init__.py"), "");

        // Import from sibling block — boundary bypass (../../ goes up from services/ to blocks/)
        Files.writeString(servicesDir.resolve("bad_service.py"),
            "from ...payment.services.payment_service import PaymentService\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")));
    }

    @Test
    void thirdPartyImportDetected(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        // Third-party package import from governed root
        Files.writeString(blockRoot.resolve("bad_service.py"), "import requests\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("THIRD_PARTY_IMPORT")));
    }

    @Test
    void findingsIncludePathAndModuleName(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        Files.writeString(blockRoot.resolve("bad_service.py"), "import requests\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        BoundaryBypassFinding finding = findings.get(0);
        assertTrue(finding.path().contains("bad_service.py"));
        assertTrue(finding.detail().contains("requests"));
    }

    @Test
    void multipleViolationsCollected(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        Files.writeString(blockRoot.resolve("bad_service1.py"), "import requests\n");
        Files.writeString(blockRoot.resolve("bad_service2.py"), "import flask\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertEquals(2, findings.size());
    }

    @Test
    void testFilesExcludedFromScan(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        // test_*.py files should be excluded
        Files.writeString(blockRoot.resolve("test_user_auth.py"), "import requests\n");
        // *_test.py files should be excluded
        Files.writeString(blockRoot.resolve("user_auth_test.py"), "import flask\n");
        // Regular .py file with clean import
        Files.writeString(blockRoot.resolve("user_auth.py"), "# clean\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "test files should not be scanned");
    }

    @Test
    void blockWithoutInitPyExcluded(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        // No __init__.py file

        Files.writeString(blockRoot.resolve("bad_service.py"), "import requests\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "blocks without __init__.py should not be scanned");
    }

    @Test
    void sharedRootIncludedWhenPresent(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(sharedRoot.resolve("__init__.py"), "");

        // Block imports from _shared — allowed (stdlib import)
        Files.writeString(blockRoot.resolve("service.py"), "from pathlib import Path\n");
        
        // _shared has third-party import — should be detected
        Files.writeString(sharedRoot.resolve("util.py"), "import requests\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertEquals(1, findings.size());
        assertTrue(findings.get(0).path().contains("_shared"));
        assertTrue(findings.get(0).rule().equals("THIRD_PARTY_IMPORT"));
    }

    @Test
    void stdlibImportsAllowed(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        // Python stdlib imports should be allowed
        Files.writeString(blockRoot.resolve("service.py"), 
            "import os\nimport sys\nfrom pathlib import Path\nimport json\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "stdlib imports should be allowed");
    }

    @Test
    void relativeImportsWithinBlockAllowed(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path servicesDir = Files.createDirectories(blockRoot.resolve("services"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(servicesDir.resolve("__init__.py"), "");

        // Relative import within same block — allowed
        Files.writeString(servicesDir.resolve("service.py"), "from . import helper\n");
        Files.writeString(servicesDir.resolve("helper.py"), "# helper\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "relative imports within block should be allowed");
    }

    @Test
    void findingsSortedByFilePath(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        // Create files in non-alphabetical order
        Files.writeString(blockRoot.resolve("z_service.py"), "import requests\n");
        Files.writeString(blockRoot.resolve("a_service.py"), "import flask\n");
        Files.writeString(blockRoot.resolve("m_service.py"), "import django\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertEquals(3, findings.size());
        // Verify sorted by file path
        assertTrue(findings.get(0).path().contains("a_service.py"));
        assertTrue(findings.get(1).path().contains("m_service.py"));
        assertTrue(findings.get(2).path().contains("z_service.py"));
    }

    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "_impl.py",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
