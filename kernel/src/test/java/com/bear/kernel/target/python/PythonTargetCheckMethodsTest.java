package com.bear.kernel.target.python;

import com.bear.kernel.target.ManifestParseException;
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
 * Unit tests for PythonTarget check pipeline methods.
 * Feature: phase-p2-python-checking
 */
class PythonTargetCheckMethodsTest {

    private final PythonTarget target = new PythonTarget();

    // ========== parseWiringManifest tests ==========

    @Test
    void parseWiringManifest_validJson_returnsPopulatedManifest(@TempDir Path tempDir) throws IOException, ManifestParseException {
        Path wiringFile = tempDir.resolve("my-block.wiring.json");
        // Use compact JSON (no spaces after colons) to match parser expectations
        String validJson = "{" +
            "\"schemaVersion\":\"v3\"," +
            "\"blockKey\":\"my-block\"," +
            "\"entrypointFqcn\":\"blocks.my_block.MyBlockWrapper\"," +
            "\"logicInterfaceFqcn\":\"blocks.my_block.MyBlockLogic\"," +
            "\"implFqcn\":\"blocks.my_block.impl.MyBlockImpl\"," +
            "\"implSourcePath\":\"src/blocks/my-block/impl/my_block_impl.py\"," +
            "\"blockRootSourceDir\":\"src/blocks/my-block\"," +
            "\"governedSourceRoots\":[\"src/blocks/my-block\",\"src/main/java/blocks/_shared\"]," +
            "\"requiredEffectPorts\":[]," +
            "\"constructorPortParams\":[]," +
            "\"logicRequiredPorts\":[]," +
            "\"wrapperOwnedSemanticPorts\":[]," +
            "\"wrapperOwnedSemanticChecks\":[]," +
            "\"blockPortBindings\":[]" +
            "}";
        Files.writeString(wiringFile, validJson);

        WiringManifest manifest = target.parseWiringManifest(wiringFile);

        assertEquals("v3", manifest.schemaVersion());
        assertEquals("my-block", manifest.blockKey());
        assertEquals("blocks.my_block.MyBlockWrapper", manifest.entrypointFqcn());
        assertEquals("blocks.my_block.MyBlockLogic", manifest.logicInterfaceFqcn());
        assertEquals("blocks.my_block.impl.MyBlockImpl", manifest.implFqcn());
        assertEquals("src/blocks/my-block/impl/my_block_impl.py", manifest.implSourcePath());
        assertEquals("src/blocks/my-block", manifest.blockRootSourceDir());
        assertEquals(2, manifest.governedSourceRoots().size());
        assertTrue(manifest.governedSourceRoots().contains("src/blocks/my-block"));
        assertTrue(manifest.governedSourceRoots().contains("src/main/java/blocks/_shared"));
    }

    @Test
    void parseWiringManifest_malformedJson_throwsManifestParseException(@TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("bad.wiring.json");
        String malformedJson = "{ this is not valid json }";
        Files.writeString(wiringFile, malformedJson);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile));
        
        assertTrue(ex.reasonCode().contains("MALFORMED") || ex.reasonCode().contains("MISSING"),
            "Reason code should indicate malformed JSON or missing key: " + ex.reasonCode());
    }

    @Test
    void parseWiringManifest_missingSchemaVersion_throwsManifestParseException(@TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("missing-schema.wiring.json");
        // Use compact JSON (no spaces after colons) to match parser expectations
        String jsonMissingSchemaVersion = "{" +
            "\"blockKey\":\"my-block\"," +
            "\"entrypointFqcn\":\"blocks.my_block.MyBlockWrapper\"," +
            "\"logicInterfaceFqcn\":\"blocks.my_block.MyBlockLogic\"," +
            "\"implFqcn\":\"blocks.my_block.impl.MyBlockImpl\"," +
            "\"implSourcePath\":\"src/blocks/my-block/impl/my_block_impl.py\"," +
            "\"blockRootSourceDir\":\"src/blocks/my-block\"," +
            "\"governedSourceRoots\":[\"src/blocks/my-block\",\"src/main/java/blocks/_shared\"]," +
            "\"requiredEffectPorts\":[]," +
            "\"constructorPortParams\":[]," +
            "\"logicRequiredPorts\":[]," +
            "\"wrapperOwnedSemanticPorts\":[]," +
            "\"wrapperOwnedSemanticChecks\":[]," +
            "\"blockPortBindings\":[]" +
            "}";
        Files.writeString(wiringFile, jsonMissingSchemaVersion);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile));
        
        assertTrue(ex.reasonCode().contains("schemaVersion"),
            "Reason code should contain missing field name 'schemaVersion': " + ex.reasonCode());
    }

    @Test
    void parseWiringManifest_missingBlockKey_throwsManifestParseException(@TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("missing-blockkey.wiring.json");
        // Use compact JSON (no spaces after colons) to match parser expectations
        String jsonMissingBlockKey = "{" +
            "\"schemaVersion\":\"v3\"," +
            "\"entrypointFqcn\":\"blocks.my_block.MyBlockWrapper\"," +
            "\"logicInterfaceFqcn\":\"blocks.my_block.MyBlockLogic\"," +
            "\"implFqcn\":\"blocks.my_block.impl.MyBlockImpl\"," +
            "\"implSourcePath\":\"src/blocks/my-block/impl/my_block_impl.py\"," +
            "\"blockRootSourceDir\":\"src/blocks/my-block\"," +
            "\"governedSourceRoots\":[\"src/blocks/my-block\",\"src/main/java/blocks/_shared\"]," +
            "\"requiredEffectPorts\":[]," +
            "\"constructorPortParams\":[]," +
            "\"logicRequiredPorts\":[]," +
            "\"wrapperOwnedSemanticPorts\":[]," +
            "\"wrapperOwnedSemanticChecks\":[]," +
            "\"blockPortBindings\":[]" +
            "}";
        Files.writeString(wiringFile, jsonMissingBlockKey);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile));
        
        assertTrue(ex.reasonCode().contains("blockKey"),
            "Reason code should contain missing field name 'blockKey': " + ex.reasonCode());
    }

    @Test
    void parseWiringManifest_missingGovernedSourceRoots_throwsManifestParseException(@TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("missing-governed.wiring.json");
        // Use compact JSON (no spaces after colons) to match parser expectations
        String jsonMissingGovernedRoots = "{" +
            "\"schemaVersion\":\"v3\"," +
            "\"blockKey\":\"my-block\"," +
            "\"entrypointFqcn\":\"blocks.my_block.MyBlockWrapper\"," +
            "\"logicInterfaceFqcn\":\"blocks.my_block.MyBlockLogic\"," +
            "\"implFqcn\":\"blocks.my_block.impl.MyBlockImpl\"," +
            "\"implSourcePath\":\"src/blocks/my-block/impl/my_block_impl.py\"," +
            "\"blockRootSourceDir\":\"src/blocks/my-block\"," +
            "\"requiredEffectPorts\":[]," +
            "\"constructorPortParams\":[]," +
            "\"logicRequiredPorts\":[]," +
            "\"wrapperOwnedSemanticPorts\":[]," +
            "\"wrapperOwnedSemanticChecks\":[]," +
            "\"blockPortBindings\":[]" +
            "}";
        Files.writeString(wiringFile, jsonMissingGovernedRoots);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile));
        
        assertTrue(ex.reasonCode().contains("governedSourceRoots"),
            "Reason code should contain missing field name 'governedSourceRoots': " + ex.reasonCode());
    }

    @Test
    void parseWiringManifest_emptyFile_throwsManifestParseException(@TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("empty.wiring.json");
        Files.writeString(wiringFile, "");

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile));
        
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    @Test
    void parseWiringManifest_notJsonObject_throwsManifestParseException(@TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("array.wiring.json");
        Files.writeString(wiringFile, "[1, 2, 3]");

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile));
        
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    // ========== prepareCheckWorkspace tests ==========

    @Test
    void prepareCheckWorkspace_sharedDirPresent_createdInTempRoot(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        // Setup: create _shared directory in project root
        Path sharedDir = projectRoot.resolve("src/blocks/_shared");
        Files.createDirectories(sharedDir);

        // Execute
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify: _shared directory created in temp root
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        assertTrue(Files.isDirectory(tempSharedDir),
            "src/blocks/_shared should be created in temp root when present in project root");
    }

    @Test
    void prepareCheckWorkspace_sharedDirAbsent_noErrorNoDirCreated(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        // Setup: no _shared directory in project root (just ensure src/blocks exists but not _shared)
        Path blocksDir = projectRoot.resolve("src/blocks");
        Files.createDirectories(blocksDir);
        // Explicitly do NOT create _shared

        // Execute - should not throw
        assertDoesNotThrow(() -> target.prepareCheckWorkspace(projectRoot, tempRoot));

        // Verify: _shared directory NOT created in temp root
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        assertFalse(Files.exists(tempSharedDir),
            "src/blocks/_shared should NOT be created in temp root when absent in project root");
    }

    @Test
    void prepareCheckWorkspace_tempRootCreatedIfNeeded(@TempDir Path projectRoot) throws IOException {
        // Setup: create _shared directory in project root
        Path sharedDir = projectRoot.resolve("src/blocks/_shared");
        Files.createDirectories(sharedDir);

        // Create a temp root path that doesn't exist yet
        Path tempRoot = projectRoot.resolve("temp-workspace");
        assertFalse(Files.exists(tempRoot), "Temp root should not exist before test");

        // Execute
        target.prepareCheckWorkspace(projectRoot, tempRoot);

        // Verify: temp root and _shared directory created
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        assertTrue(Files.isDirectory(tempSharedDir),
            "src/blocks/_shared should be created even when temp root didn't exist");
    }

    @Test
    void prepareCheckWorkspace_sharedIsFile_notCreatedInTempRoot(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        // Setup: create _shared as a FILE (not directory) in project root
        Path sharedPath = projectRoot.resolve("src/blocks/_shared");
        Files.createDirectories(sharedPath.getParent());
        Files.writeString(sharedPath, "this is a file, not a directory");

        // Execute - should not throw
        assertDoesNotThrow(() -> target.prepareCheckWorkspace(projectRoot, tempRoot));

        // Verify: _shared directory NOT created in temp root (because source is a file, not directory)
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        assertFalse(Files.exists(tempSharedDir),
            "src/blocks/_shared should NOT be created when source is a file, not a directory");
    }

    @Test
    void prepareCheckWorkspace_emptyProjectRoot_noError(@TempDir Path projectRoot, @TempDir Path tempRoot) throws IOException {
        // Setup: completely empty project root (no src/blocks at all)

        // Execute - should not throw
        assertDoesNotThrow(() -> target.prepareCheckWorkspace(projectRoot, tempRoot));

        // Verify: nothing created in temp root
        Path tempSharedDir = tempRoot.resolve("src/blocks/_shared");
        assertFalse(Files.exists(tempSharedDir),
            "src/blocks/_shared should NOT be created when project root has no src/blocks");
    }

    // ========== containment pipeline stub tests ==========

    @Test
    void containmentStubs_containmentSkipInfoLine_returnsNull(@TempDir Path projectRoot) {
        // JVM-style containment markers are not applicable to Python
        String result = target.containmentSkipInfoLine("test-project", projectRoot, true);
        assertNull(result, "containmentSkipInfoLine should return null for Python targets");
    }

    @Test
    void containmentStubs_containmentSkipInfoLine_returnsNullWhenContainmentDisabled(@TempDir Path projectRoot) {
        String result = target.containmentSkipInfoLine("test-project", projectRoot, false);
        assertNull(result, "containmentSkipInfoLine should return null regardless of considerContainmentSurfaces flag");
    }

    @Test
    void containmentStubs_preflightContainmentIfRequired_returnsNull(@TempDir Path projectRoot) throws IOException {
        // JVM-style containment markers are not applicable to Python
        var result = target.preflightContainmentIfRequired(projectRoot, true);
        assertNull(result, "preflightContainmentIfRequired should return null for Python targets");
    }

    @Test
    void containmentStubs_preflightContainmentIfRequired_returnsNullWhenContainmentDisabled(@TempDir Path projectRoot) throws IOException {
        var result = target.preflightContainmentIfRequired(projectRoot, false);
        assertNull(result, "preflightContainmentIfRequired should return null regardless of considerContainmentSurfaces flag");
    }

    @Test
    void containmentStubs_verifyContainmentMarkersIfRequired_returnsNull(@TempDir Path projectRoot) throws IOException {
        // JVM-style containment markers are not applicable to Python
        var result = target.verifyContainmentMarkersIfRequired(projectRoot, true);
        assertNull(result, "verifyContainmentMarkersIfRequired should return null for Python targets");
    }

    @Test
    void containmentStubs_verifyContainmentMarkersIfRequired_returnsNullWhenContainmentDisabled(@TempDir Path projectRoot) throws IOException {
        var result = target.verifyContainmentMarkersIfRequired(projectRoot, false);
        assertNull(result, "verifyContainmentMarkersIfRequired should return null regardless of considerContainmentSurfaces flag");
    }

    @Test
    void containmentStubs_considerContainmentSurfaces_returnsFalse(@TempDir Path projectRoot) {
        // Python targets do not support JVM-style containment surfaces
        boolean result = target.considerContainmentSurfaces(null, projectRoot);
        assertFalse(result, "considerContainmentSurfaces should return false for Python targets");
    }

    // ========== port and binding check stub tests ==========

    @Test
    void portStubs_scanPortImplContainmentBypass_returnsEmptyList(@TempDir Path projectRoot) throws IOException, ManifestParseException {
        // JVM-specific port binding checks are not applicable to Python
        var result = target.scanPortImplContainmentBypass(projectRoot, List.of());
        assertNotNull(result, "scanPortImplContainmentBypass should return non-null list");
        assertTrue(result.isEmpty(), "scanPortImplContainmentBypass should return empty list for Python targets");
    }

    @Test
    void portStubs_scanPortImplContainmentBypass_returnsEmptyListWithManifests(@TempDir Path projectRoot) throws IOException, ManifestParseException {
        // Even with manifests provided, should return empty list
        var result = target.scanPortImplContainmentBypass(projectRoot, List.of());
        assertNotNull(result, "scanPortImplContainmentBypass should return non-null list");
        assertTrue(result.isEmpty(), "scanPortImplContainmentBypass should return empty list regardless of manifests");
    }

    @Test
    void portStubs_scanBlockPortBindings_returnsEmptyList(@TempDir Path projectRoot) throws IOException {
        // JVM-specific port binding checks are not applicable to Python
        var result = target.scanBlockPortBindings(projectRoot, List.of(), Set.of());
        assertNotNull(result, "scanBlockPortBindings should return non-null list");
        assertTrue(result.isEmpty(), "scanBlockPortBindings should return empty list for Python targets");
    }

    @Test
    void portStubs_scanBlockPortBindings_returnsEmptyListWithInputs(@TempDir Path projectRoot) throws IOException {
        // Even with manifests and wrapper FQCNs provided, should return empty list
        var result = target.scanBlockPortBindings(projectRoot, List.of(), Set.of("com.example.Wrapper"));
        assertNotNull(result, "scanBlockPortBindings should return non-null list");
        assertTrue(result.isEmpty(), "scanBlockPortBindings should return empty list regardless of inputs");
    }

    @Test
    void portStubs_scanMultiBlockPortImplAllowedSignals_returnsEmptyList(@TempDir Path projectRoot) throws IOException, ManifestParseException {
        // JVM-specific port binding checks are not applicable to Python
        var result = target.scanMultiBlockPortImplAllowedSignals(projectRoot, List.of());
        assertNotNull(result, "scanMultiBlockPortImplAllowedSignals should return non-null list");
        assertTrue(result.isEmpty(), "scanMultiBlockPortImplAllowedSignals should return empty list for Python targets");
    }

    @Test
    void portStubs_scanMultiBlockPortImplAllowedSignals_returnsEmptyListWithManifests(@TempDir Path projectRoot) throws IOException, ManifestParseException {
        // Even with manifests provided, should return empty list
        var result = target.scanMultiBlockPortImplAllowedSignals(projectRoot, List.of());
        assertNotNull(result, "scanMultiBlockPortImplAllowedSignals should return non-null list");
        assertTrue(result.isEmpty(), "scanMultiBlockPortImplAllowedSignals should return empty list regardless of manifests");
    }
}
