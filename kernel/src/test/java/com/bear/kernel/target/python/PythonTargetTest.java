package com.bear.kernel.target.python;

import com.bear.kernel.target.TargetId;
import com.bear.kernel.target.WiringManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PythonTarget.
 * Feature: phase-p-python-scan-only
 */
class PythonTargetTest {

    @Test
    void targetIdReturnsPython() {
        PythonTarget target = new PythonTarget();
        assertEquals(TargetId.PYTHON, target.targetId());
    }

    @Test
    void defaultProfileReturnsServiceProfile() {
        PythonTarget target = new PythonTarget();
        var profile = target.defaultProfile();
        assertEquals(TargetId.PYTHON, profile.target());
        assertEquals("service", profile.profileId());
    }

    // Governed Roots Tests

    @Test
    void governedRoots_singleBlock(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "# code\n");

        var manifests = List.of(makeManifest("user-auth"));
        Set<Path> roots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, manifests);

        assertEquals(1, roots.size());
        assertTrue(roots.contains(blockRoot));
    }

    @Test
    void governedRoots_multiBlock(@TempDir Path projectRoot) throws IOException {
        Path userAuthRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(userAuthRoot.resolve("__init__.py"), "");
        
        Path paymentRoot = Files.createDirectories(projectRoot.resolve("src/blocks/payment"));
        Files.writeString(paymentRoot.resolve("__init__.py"), "");

        var manifests = List.of(makeManifest("user-auth"), makeManifest("payment"));
        Set<Path> roots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, manifests);

        assertEquals(2, roots.size());
        assertTrue(roots.contains(userAuthRoot));
        assertTrue(roots.contains(paymentRoot));
    }

    @Test
    void governedRoots_withShared(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        
        Path sharedRoot = Files.createDirectories(projectRoot.resolve("src/blocks/_shared"));
        Files.writeString(sharedRoot.resolve("__init__.py"), "");

        var manifests = List.of(makeManifest("user-auth"));
        Set<Path> roots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, manifests);

        assertEquals(2, roots.size());
        assertTrue(roots.contains(blockRoot));
        assertTrue(roots.contains(sharedRoot));
    }

    @Test
    void governedRoots_withoutShared(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");

        var manifests = List.of(makeManifest("user-auth"));
        Set<Path> roots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, manifests);

        assertEquals(1, roots.size());
        assertTrue(roots.contains(blockRoot));
    }

    @Test
    void governedRoots_requiresInitPy(@TempDir Path projectRoot) throws IOException {
        // Block directory exists but no __init__.py
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("service.py"), "# code\n");

        var manifests = List.of(makeManifest("user-auth"));
        Set<Path> roots = PythonImportContainmentScanner.computeGovernedRoots(projectRoot, manifests);

        assertTrue(roots.isEmpty(), "Block without __init__.py should not be included");
    }

    @Test
    void governedFiles_excludesTestFiles(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "# code\n");
        Files.writeString(blockRoot.resolve("test_service.py"), "# test\n");
        Files.writeString(blockRoot.resolve("service_test.py"), "# test\n");

        var roots = Set.of(blockRoot);
        List<Path> files = PythonImportContainmentScanner.collectGovernedFiles(roots);

        assertEquals(2, files.size(), "Should include service.py and __init__.py only");
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("service.py")));
        assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("__init__.py")));
        assertFalse(files.stream().anyMatch(p -> p.getFileName().toString().equals("test_service.py")));
        assertFalse(files.stream().anyMatch(p -> p.getFileName().toString().equals("service_test.py")));
    }

    @Test
    void governedFiles_extensionFiltering(@TempDir Path projectRoot) throws IOException {
        Path blockRoot = Files.createDirectories(projectRoot.resolve("src/blocks/user-auth"));
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "# code\n");
        Files.writeString(blockRoot.resolve("types.pyi"), "# stub\n");
        Files.writeString(blockRoot.resolve("compiled.pyc"), "# compiled\n");
        Files.writeString(blockRoot.resolve("optimized.pyo"), "# optimized\n");
        Files.writeString(blockRoot.resolve("readme.md"), "# docs\n");

        var roots = Set.of(blockRoot);
        List<Path> files = PythonImportContainmentScanner.collectGovernedFiles(roots);

        assertEquals(2, files.size(), "Should include only .py files");
        assertTrue(files.stream().allMatch(p -> p.toString().endsWith(".py")));
        assertFalse(files.stream().anyMatch(p -> p.toString().endsWith(".pyi")));
        assertFalse(files.stream().anyMatch(p -> p.toString().endsWith(".pyc")));
        assertFalse(files.stream().anyMatch(p -> p.toString().endsWith(".pyo")));
    }

    // Drift Gate Tests

    @Test
    void driftGate_cleanState_noFindings(@TempDir Path projectRoot) throws IOException {
        var ir = makeBearIr("UserAuth", "login");
        String blockKey = "user-auth";
        
        PythonTarget target = new PythonTarget();
        target.compile(ir, projectRoot, blockKey);

        // Generate to temp directory for comparison
        Path tempRoot = Files.createTempDirectory("bear-drift-clean-");
        try {
            target.compile(ir, tempRoot, blockKey);
            
            // Compare generated artifacts
            Path wiringA = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            Path wiringB = tempRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            assertEquals(Files.readString(wiringA), Files.readString(wiringB),
                "Clean state should produce identical wiring");
            
            Path portsA = projectRoot.resolve("build/generated/bear/" + blockKey + "/user_auth_ports.py");
            Path portsB = tempRoot.resolve("build/generated/bear/" + blockKey + "/user_auth_ports.py");
            assertEquals(Files.readString(portsA), Files.readString(portsB),
                "Clean state should produce identical ports");
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    @Test
    void driftGate_modifiedGeneratedFile_detected(@TempDir Path projectRoot) throws IOException {
        var ir = makeBearIr("UserAuth", "login");
        String blockKey = "user-auth";
        
        PythonTarget target = new PythonTarget();
        target.compile(ir, projectRoot, blockKey);

        // Modify generated file
        Path wiringFile = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
        Files.writeString(wiringFile, "{ \"modified\": true }");

        // Generate fresh to temp directory
        Path tempRoot = Files.createTempDirectory("bear-drift-modified-");
        try {
            target.compile(ir, tempRoot, blockKey);
            Path freshWiring = tempRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            
            assertNotEquals(Files.readString(wiringFile), Files.readString(freshWiring),
                "Modified wiring should differ from fresh compile");
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    @Test
    void driftGate_missingGeneratedFile_detected(@TempDir Path projectRoot) throws IOException {
        var ir = makeBearIr("UserAuth", "login");
        String blockKey = "user-auth";
        
        PythonTarget target = new PythonTarget();
        target.compile(ir, projectRoot, blockKey);

        // Delete generated file
        Path wiringFile = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
        Files.delete(wiringFile);

        assertFalse(Files.exists(wiringFile), "Wiring file should be missing");
    }

    @Test
    void driftGate_userImplModified_noFindings(@TempDir Path projectRoot) throws IOException {
        var ir = makeBearIr("UserAuth", "login");
        String blockKey = "user-auth";
        
        PythonTarget target = new PythonTarget();
        target.compile(ir, projectRoot, blockKey);

        // Modify user impl file
        Path implFile = projectRoot.resolve("src/blocks/" + blockKey + "/impl/user_auth_impl.py");
        Files.writeString(implFile, "# user modified\n");

        // Generate fresh to temp directory
        Path tempRoot = Files.createTempDirectory("bear-drift-impl-");
        try {
            target.compile(ir, tempRoot, blockKey);
            
            // Generated artifacts should still match
            Path wiringA = projectRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            Path wiringB = tempRoot.resolve("build/generated/bear/wiring/" + blockKey + ".wiring.json");
            assertEquals(Files.readString(wiringA), Files.readString(wiringB),
                "User impl modification should not affect generated wiring");
        } finally {
            deleteQuietly(tempRoot);
        }
    }

    // Helper methods

    private com.bear.kernel.ir.BearIr makeBearIr(String blockName, String opName) {
        return new com.bear.kernel.ir.BearIr("1", new com.bear.kernel.ir.BearIr.Block(
            blockName, com.bear.kernel.ir.BearIr.BlockKind.LOGIC,
            List.of(new com.bear.kernel.ir.BearIr.Operation(
                opName,
                new com.bear.kernel.ir.BearIr.Contract(
                    List.of(new com.bear.kernel.ir.BearIr.Field("input", com.bear.kernel.ir.BearIr.FieldType.STRING)),
                    List.of(new com.bear.kernel.ir.BearIr.Field("result", com.bear.kernel.ir.BearIr.FieldType.STRING))
                ),
                new com.bear.kernel.ir.BearIr.Effects(List.of()), null, List.of()
            )),
            new com.bear.kernel.ir.BearIr.Effects(List.of()), null, null, List.of()
        ));
    }

    private void deleteQuietly(Path dir) {
        try {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
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
