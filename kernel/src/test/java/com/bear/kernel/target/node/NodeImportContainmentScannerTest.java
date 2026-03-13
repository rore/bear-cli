package com.bear.kernel.target.node;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeImportContainmentScannerTest {

    @Test
    void cleanProjectHasNoFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("user-authLogic.ts"), "// clean file\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty());
    }

    @Test
    void boundaryBypassDetected(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        // Import from sibling block — boundary bypass (../../ goes up from services/ to blocks/)
        Files.writeString(servicesDir.resolve("bad-service.ts"),
            "import { PaymentService } from '../../payment/services/payment-service';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BOUNDARY_BYPASS")));
    }

    @Test
    void bareImportDetected(@TempDir Path tempDir) throws IOException {
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        // Bare package import from governed root
        Files.writeString(servicesDir.resolve("bad-service.ts"), "import _ from 'lodash';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.rule().equals("BARE_PACKAGE_IMPORT")));
    }

    @Test
    void findingsIncludePathAndSpecifier(@TempDir Path tempDir) throws IOException {
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        Files.writeString(servicesDir.resolve("bad-service.ts"),
            "import { PaymentService } from '../../payment/services/payment-service';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        BoundaryBypassFinding finding = findings.get(0);
        assertTrue(finding.path().contains("bad-service.ts"));
        assertTrue(finding.detail().contains("../payment/services/payment-service"));
    }

    @Test
    void multipleViolationsCollected(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        Path servicesDir = Files.createDirectories(tempDir.resolve("src/blocks/user-auth/services"));

        Files.writeString(servicesDir.resolve("bad-service1.ts"),
            "import { A } from '../../payment/services/a';\n");
        Files.writeString(servicesDir.resolve("bad-service2.ts"),
            "import { B } from '../../payment/services/b';\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertEquals(2, findings.size());
    }

    @Test
    void testFilesExcludedFromScan(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));

        // .test.ts files should be excluded
        Files.writeString(blockRoot.resolve("user-auth.test.ts"),
            "import _ from 'lodash';\n");
        // Regular .ts file with clean import
        Files.writeString(blockRoot.resolve("user-auth.ts"), "// clean\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = NodeImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "test files should not be scanned");
    }

    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "Impl.ts",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
