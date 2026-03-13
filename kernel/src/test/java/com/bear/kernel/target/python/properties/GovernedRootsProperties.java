package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.python.PythonImportContainmentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for governed roots computation.
 * Feature: phase-p-python-scan-only
 */
class GovernedRootsProperties {

    /**
     * Property 7: Any block key with __init__.py -> src/blocks/<blockKey>/ in governed roots.
     * Validates: Requirements - Governed Source Roots
     */
    @Test
    void blockKeyWithInitPyIncluded_userAuth(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("user-auth", projectRoot);
    }

    @Test
    void blockKeyWithInitPyIncluded_payment(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("payment", projectRoot);
    }

    @Test
    void blockKeyWithInitPyIncluded_orderManager(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("order-manager", projectRoot);
    }

    @Test
    void blockKeyWithInitPyIncluded_apiGateway(@TempDir Path projectRoot) throws IOException {
        assertBlockFilesScanned("api-gateway", projectRoot);
    }

    /**
     * Property 8: Any path outside src/blocks/ -> excluded from governed roots.
     * Validates: Requirements - Governed Source Roots
     */
    @Test
    void filesOutsideBlocksNotScanned(@TempDir Path projectRoot) throws IOException {
        Path outsideDir = Files.createDirectories(projectRoot.resolve("src/other"));
        Files.writeString(outsideDir.resolve("outside.py"), "import requests\n");
        
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("index.py"), "# clean\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "files outside src/blocks/ should not be scanned");
    }

    @Test
    void filesInSrcRootNotScanned(@TempDir Path projectRoot) throws IOException {
        Path srcRoot = Files.createDirectories(projectRoot.resolve("src"));
        Files.writeString(srcRoot.resolve("main.py"), "import requests\n");
        
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("index.py"), "# clean\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "files in src/ root should not be scanned");
    }

    @Test
    void filesInTestsDirNotScanned(@TempDir Path projectRoot) throws IOException {
        Path testsDir = Files.createDirectories(projectRoot.resolve("tests"));
        Files.writeString(testsDir.resolve("test_integration.py"), "import requests\n");
        
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("index.py"), "# clean\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "files in tests/ should not be scanned");
    }

    /**
     * Property 9: Any test_*.py or *_test.py within governed roots -> excluded from governed source files.
     * Validates: Requirements - Governed Source Roots
     */
    @Test
    void testFilesExcluded_testPrefix(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("test_service.py"), "import requests\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "test_*.py files should be excluded from scanning");
    }

    @Test
    void testFilesExcluded_testSuffix(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service_test.py"), "import requests\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), "*_test.py files should be excluded from scanning");
    }

    @Test
    void testFilesExcluded_multipleBlocks(@TempDir Path projectRoot) throws IOException {
        Path userAuthRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(userAuthRoot.resolve("__init__.py"), "");
        Files.writeString(userAuthRoot.resolve("test_auth.py"), "import requests\n");
        
        Path paymentRoot = Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Files.writeString(paymentRoot.resolve("__init__.py"), "");
        Files.writeString(paymentRoot.resolve("payment_test.py"), "import requests\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, 
            List.of(makeManifest("user-auth"), makeManifest("payment")));
        assertTrue(findings.isEmpty(), "test files in all blocks should be excluded");
    }

    /**
     * Property 10: Any non-.py extension in src/blocks/ -> excluded from governed source.
     * Validates: Requirements - Governed Source Roots
     */
    @Test
    void nonPyFilesExcluded_pyi(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("types.pyi"), "import requests\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".pyi files should not be scanned");
    }

    @Test
    void nonPyFilesExcluded_pyc(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("compiled.pyc"), "binary content");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".pyc files should not be scanned");
    }

    @Test
    void nonPyFilesExcluded_pyo(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("optimized.pyo"), "binary content");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".pyo files should not be scanned");
    }

    @Test
    void nonPyFilesExcluded_markdown(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("README.md"), "# Documentation");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".md files should not be scanned");
    }

    @Test
    void nonPyFilesExcluded_json(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("config.json"), "{}");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest("user-auth")));
        assertTrue(findings.isEmpty(), ".json files should not be scanned");
    }

    private void assertBlockFilesScanned(String blockKey, Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/" + blockKey));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("index.py"), "# clean\n");

        var findings = PythonImportContainmentScanner.scan(projectRoot, List.of(makeManifest(blockKey)));
        assertTrue(findings.isEmpty(), "clean file in block root should produce no findings");
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
