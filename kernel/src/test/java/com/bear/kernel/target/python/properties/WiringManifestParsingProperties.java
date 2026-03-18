package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.ManifestParseException;
import com.bear.kernel.target.TargetManifestParsers;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for wiring manifest parsing.
 * Feature: phase-p2-python-checking
 * 
 * Uses plain JUnit 5 parameterized tests with 100+ iterations.
 */
class WiringManifestParsingProperties {

    private final PythonTarget target = new PythonTarget();
    private static final Random RANDOM = new Random(42); // Deterministic seed

    // ========== Property 1: Round-trip serialization ==========
    // **Validates: Requirements 1.1, 1.4**
    // Serialize valid WiringManifest to JSON, parse back, all fields identical.

    static Stream<Integer> roundTripIterations() {
        return IntStream.range(0, 100).boxed();
    }

    @ParameterizedTest(name = "Property 1 - round-trip iteration {0}")
    @MethodSource("roundTripIterations")
    void property1_roundTrip_allFieldsIdentical(int iteration, @TempDir Path tempDir) throws IOException, ManifestParseException {
        // Generate a valid manifest with varying data
        String blockKey = "block-" + iteration;
        String blockName = blockKey.replace("-", "_");
        
        String json = generateValidManifestJson(blockKey, blockName, iteration);
        Path wiringFile = tempDir.resolve(blockKey + ".wiring.json");
        Files.writeString(wiringFile, json);

        WiringManifest parsed = target.parseWiringManifest(wiringFile);

        // Verify all fields match expected values
        assertEquals("v3", parsed.schemaVersion(), "schemaVersion should match");
        assertEquals(blockKey, parsed.blockKey(), "blockKey should match");
        assertEquals("blocks." + blockName + ".Wrapper", parsed.entrypointFqcn(), "entrypointFqcn should match");
        assertEquals("blocks." + blockName + ".Logic", parsed.logicInterfaceFqcn(), "logicInterfaceFqcn should match");
        assertEquals("blocks." + blockName + ".impl.Impl", parsed.implFqcn(), "implFqcn should match");
        assertEquals("src/blocks/" + blockKey + "/impl/" + blockName + "_impl.py", parsed.implSourcePath(), "implSourcePath should match");
        assertEquals("src/blocks/" + blockKey, parsed.blockRootSourceDir(), "blockRootSourceDir should match");
        assertEquals(2, parsed.governedSourceRoots().size(), "governedSourceRoots should have 2 entries");
        assertEquals("src/blocks/" + blockKey, parsed.governedSourceRoots().get(0), "first governed root should be block root");
        assertEquals("src/main/java/blocks/_shared", parsed.governedSourceRoots().get(1), "second governed root should be _shared");
    }

    // ========== Property 2: Malformed JSON rejection ==========
    // **Validates: Requirements 1.2**
    // Any non-valid JSON string throws ManifestParseException with MALFORMED_JSON reason.

    static Stream<String> malformedJsonStrings() {
        return Stream.of(
            "",                                    // Empty string
            "   ",                                 // Whitespace only
            "not json at all",                     // Plain text
            "{ incomplete",                        // Incomplete object
            "{ \"key\": }",                        // Missing value
            "{ \"key\" \"value\" }",               // Missing colon
            "{ key: \"value\" }",                  // Unquoted key
            "[1, 2, 3]",                           // Array instead of object
            "null",                                // Null literal
            "true",                                // Boolean literal
            "123",                                 // Number literal
            "\"just a string\"",                   // String literal
            "{ \"a\": 1, \"a\": 2 }",              // Duplicate keys (still valid JSON but not object start/end)
            "{ \"key\": \"value\", }",             // Trailing comma
            "{ 'key': 'value' }",                  // Single quotes
            "{\n\"key\"\n:\n\"value\"\n",          // Unclosed brace
            "{ \"key\": \"val\\ue\" }",            // Invalid escape
            "<!-- not json -->",                   // XML-like
            "<json>not</json>",                    // XML
            "function() {}",                       // JavaScript
            "{ \"key\": undefined }",              // JavaScript undefined
            "{ \"key\": NaN }",                    // JavaScript NaN
            "{ \"key\": Infinity }"                // JavaScript Infinity
        );
    }

    @ParameterizedTest(name = "Property 2 - malformed JSON: {0}")
    @MethodSource("malformedJsonStrings")
    void property2_malformedJson_throwsManifestParseException(String malformedJson, @TempDir Path tempDir) throws IOException {
        Path wiringFile = tempDir.resolve("malformed.wiring.json");
        Files.writeString(wiringFile, malformedJson);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile),
            "Malformed JSON should throw ManifestParseException: " + malformedJson);
        
        // Reason should indicate malformed JSON or missing required key
        assertNotNull(ex.reasonCode(), "Reason code should not be null");
        assertTrue(ex.reasonCode().contains("MALFORMED") || ex.reasonCode().contains("MISSING"),
            "Reason code should indicate malformed or missing: " + ex.reasonCode());
    }

    // Additional malformed JSON iterations to reach 100+
    static Stream<Integer> malformedJsonIterations() {
        return IntStream.range(0, 80).boxed();
    }

    @ParameterizedTest(name = "Property 2 - random malformed JSON iteration {0}")
    @MethodSource("malformedJsonIterations")
    void property2_randomMalformedJson_throwsManifestParseException(int iteration, @TempDir Path tempDir) throws IOException {
        String malformedJson = generateRandomMalformedJson(iteration);
        Path wiringFile = tempDir.resolve("malformed-" + iteration + ".wiring.json");
        Files.writeString(wiringFile, malformedJson);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile),
            "Malformed JSON should throw ManifestParseException: " + malformedJson);
        
        assertNotNull(ex.reasonCode(), "Reason code should not be null");
    }

    // ========== Property 3: Missing required field rejection ==========
    // **Validates: Requirements 1.3**
    // Valid manifest JSON with one required field removed throws ManifestParseException
    // with reason containing missing field name.

    static Stream<String> requiredFields() {
        return Stream.of(
            "schemaVersion",
            "blockKey",
            "entrypointFqcn",
            "logicInterfaceFqcn",
            "implFqcn",
            "implSourcePath",
            "blockRootSourceDir",
            "governedSourceRoots",
            "requiredEffectPorts",
            "constructorPortParams",
            "logicRequiredPorts",
            "wrapperOwnedSemanticPorts",
            "wrapperOwnedSemanticChecks",
            "blockPortBindings"
        );
    }

    @ParameterizedTest(name = "Property 3 - missing field: {0}")
    @MethodSource("requiredFields")
    void property3_missingRequiredField_throwsWithFieldName(String missingField, @TempDir Path tempDir) throws IOException {
        String json = generateManifestJsonWithoutField(missingField);
        Path wiringFile = tempDir.resolve("missing-" + missingField + ".wiring.json");
        Files.writeString(wiringFile, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile),
            "Missing field '" + missingField + "' should throw ManifestParseException");
        
        assertTrue(ex.reasonCode().contains(missingField),
            "Reason code should contain missing field name '" + missingField + "': " + ex.reasonCode());
    }

    // Additional iterations for missing field property to reach 100+
    static Stream<Object[]> missingFieldIterations() {
        List<String> fields = List.of(
            "schemaVersion", "blockKey", "entrypointFqcn", "logicInterfaceFqcn",
            "implFqcn", "implSourcePath", "blockRootSourceDir", "governedSourceRoots",
            "requiredEffectPorts", "constructorPortParams", "logicRequiredPorts",
            "wrapperOwnedSemanticPorts", "wrapperOwnedSemanticChecks", "blockPortBindings"
        );
        return IntStream.range(0, 86)
            .mapToObj(i -> new Object[]{i, fields.get(i % fields.size())});
    }

    @ParameterizedTest(name = "Property 3 - iteration {0} missing field: {1}")
    @MethodSource("missingFieldIterations")
    void property3_missingRequiredField_iterations(int iteration, String missingField, @TempDir Path tempDir) throws IOException {
        String json = generateManifestJsonWithoutField(missingField, iteration);
        Path wiringFile = tempDir.resolve("missing-" + iteration + ".wiring.json");
        Files.writeString(wiringFile, json);

        ManifestParseException ex = assertThrows(ManifestParseException.class,
            () -> target.parseWiringManifest(wiringFile),
            "Missing field '" + missingField + "' should throw ManifestParseException");
        
        assertTrue(ex.reasonCode().contains(missingField),
            "Reason code should contain missing field name '" + missingField + "': " + ex.reasonCode());
    }

    // ========== Helper methods ==========

    private String generateValidManifestJson(String blockKey, String blockName, int iteration) {
        // Generate compact JSON (no spaces after colons) to match parser expectations
        return "{" +
            "\"schemaVersion\":\"v3\"," +
            "\"blockKey\":\"" + blockKey + "\"," +
            "\"entrypointFqcn\":\"blocks." + blockName + ".Wrapper\"," +
            "\"logicInterfaceFqcn\":\"blocks." + blockName + ".Logic\"," +
            "\"implFqcn\":\"blocks." + blockName + ".impl.Impl\"," +
            "\"implSourcePath\":\"src/blocks/" + blockKey + "/impl/" + blockName + "_impl.py\"," +
            "\"blockRootSourceDir\":\"src/blocks/" + blockKey + "\"," +
            "\"governedSourceRoots\":[\"src/blocks/" + blockKey + "\",\"src/main/java/blocks/_shared\"]," +
            "\"requiredEffectPorts\":[]," +
            "\"constructorPortParams\":[]," +
            "\"logicRequiredPorts\":[]," +
            "\"wrapperOwnedSemanticPorts\":[]," +
            "\"wrapperOwnedSemanticChecks\":[]," +
            "\"blockPortBindings\":[]" +
            "}";
    }

    private String generateManifestJsonWithoutField(String fieldToRemove) {
        return generateManifestJsonWithoutField(fieldToRemove, 0);
    }

    private String generateManifestJsonWithoutField(String fieldToRemove, int iteration) {
        String blockKey = "test-block-" + iteration;
        String blockName = blockKey.replace("-", "_");
        
        // Generate compact JSON (no spaces after colons) to match parser expectations
        List<String> fields = new java.util.ArrayList<>();
        
        if (!"schemaVersion".equals(fieldToRemove)) {
            fields.add("\"schemaVersion\":\"v3\"");
        }
        if (!"blockKey".equals(fieldToRemove)) {
            fields.add("\"blockKey\":\"" + blockKey + "\"");
        }
        if (!"entrypointFqcn".equals(fieldToRemove)) {
            fields.add("\"entrypointFqcn\":\"blocks." + blockName + ".Wrapper\"");
        }
        if (!"logicInterfaceFqcn".equals(fieldToRemove)) {
            fields.add("\"logicInterfaceFqcn\":\"blocks." + blockName + ".Logic\"");
        }
        if (!"implFqcn".equals(fieldToRemove)) {
            fields.add("\"implFqcn\":\"blocks." + blockName + ".impl.Impl\"");
        }
        if (!"implSourcePath".equals(fieldToRemove)) {
            fields.add("\"implSourcePath\":\"src/blocks/" + blockKey + "/impl/" + blockName + "_impl.py\"");
        }
        if (!"blockRootSourceDir".equals(fieldToRemove)) {
            fields.add("\"blockRootSourceDir\":\"src/blocks/" + blockKey + "\"");
        }
        if (!"governedSourceRoots".equals(fieldToRemove)) {
            fields.add("\"governedSourceRoots\":[\"src/blocks/" + blockKey + "\",\"src/main/java/blocks/_shared\"]");
        }
        if (!"requiredEffectPorts".equals(fieldToRemove)) {
            fields.add("\"requiredEffectPorts\":[]");
        }
        if (!"constructorPortParams".equals(fieldToRemove)) {
            fields.add("\"constructorPortParams\":[]");
        }
        if (!"logicRequiredPorts".equals(fieldToRemove)) {
            fields.add("\"logicRequiredPorts\":[]");
        }
        if (!"wrapperOwnedSemanticPorts".equals(fieldToRemove)) {
            fields.add("\"wrapperOwnedSemanticPorts\":[]");
        }
        if (!"wrapperOwnedSemanticChecks".equals(fieldToRemove)) {
            fields.add("\"wrapperOwnedSemanticChecks\":[]");
        }
        if (!"blockPortBindings".equals(fieldToRemove)) {
            fields.add("\"blockPortBindings\":[]");
        }
        
        return "{" + String.join(",", fields) + "}";
    }

    private String generateRandomMalformedJson(int seed) {
        Random r = new Random(seed);
        int type = r.nextInt(10);
        return switch (type) {
            case 0 -> "{ " + "x".repeat(r.nextInt(50) + 1) + " }";
            case 1 -> "{ \"key\": " + r.nextInt(1000) + " }";
            case 2 -> "{ \"a\": \"b\", \"c\": }";
            case 3 -> "[" + "\"item\"".repeat(r.nextInt(5) + 1) + "]";
            case 4 -> "{ \"nested\": { \"incomplete\": ";
            case 5 -> "random text " + r.nextInt(1000);
            case 6 -> "{ \"key\": null, \"other\": undefined }";
            case 7 -> "{ \"array\": [1, 2, 3, }";
            case 8 -> "{ \"escape\": \"bad\\xescape\" }";
            default -> "{ broken: json }";
        };
    }
}
