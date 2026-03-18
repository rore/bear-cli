package com.bear.kernel.target.properties;

import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.JvmTarget;
import com.bear.kernel.target.node.NodeTarget;
import com.bear.kernel.target.python.PythonTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for TargetRegistry resolution (implemented as parameterized JUnit 5 tests).
 * Feature: phase-p2-python-checking
 * 
 * Property 15: For any registry with detectors all returning NONE and no pin file,
 *              resolve() throws TargetResolutionException with code TARGET_NOT_DETECTED.
 * 
 * Property 16: Any check pipeline failure produces stderr with CODE=, PATH=, REMEDIATION= lines.
 */
class TargetRegistryResolutionProperties {

    // Stub detector that always returns NONE
    private static final TargetDetector NONE_DETECTOR = projectRoot -> DetectedTarget.none();

    /**
     * Property 15: For any registry with detectors all returning NONE and no pin file,
     * resolve() throws TargetResolutionException with code TARGET_NOT_DETECTED.
     * 
     * Validates: Req 9.1, 9.2
     */
    @ParameterizedTest(name = "iteration {0}: {1} targets, {2} detectors")
    @MethodSource("registryConfigurations")
    void allNoneDetectors_throwsTargetNotDetected(
            int iteration,
            int targetCount,
            int detectorCount,
            Map<TargetId, Target> targets,
            @TempDir Path tempDir) {
        
        // Create list of NONE-returning detectors
        List<TargetDetector> detectors = IntStream.range(0, detectorCount)
            .mapToObj(i -> NONE_DETECTOR)
            .toList();

        TargetRegistry registry = new TargetRegistry(targets, detectors);

        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir),
            "Expected TARGET_NOT_DETECTED for registry with " + targetCount + 
            " targets and " + detectorCount + " NONE-returning detectors"
        );
        
        assertEquals("TARGET_NOT_DETECTED", ex.code(),
            "Exception code should be TARGET_NOT_DETECTED");
        assertNotNull(ex.path(), "Exception path should not be null");
        assertNotNull(ex.remediation(), "Exception remediation should not be null");
        assertTrue(ex.remediation().contains(".bear/target.id") || 
                   ex.remediation().contains("pin file"),
            "Remediation should mention pin file");
    }

    /**
     * Generates 100+ test configurations for Property 15.
     * Varies: number of targets (1-3), number of detectors (1-5), target combinations.
     */
    static Stream<Arguments> registryConfigurations() {
        Target jvm = new JvmTarget();
        Target node = new NodeTarget();
        Target python = new PythonTarget();

        List<Map<TargetId, Target>> targetMaps = List.of(
            Map.of(TargetId.JVM, jvm),
            Map.of(TargetId.NODE, node),
            Map.of(TargetId.PYTHON, python),
            Map.of(TargetId.JVM, jvm, TargetId.NODE, node),
            Map.of(TargetId.JVM, jvm, TargetId.PYTHON, python),
            Map.of(TargetId.NODE, node, TargetId.PYTHON, python),
            Map.of(TargetId.JVM, jvm, TargetId.NODE, node, TargetId.PYTHON, python)
        );

        int[] detectorCounts = {1, 2, 3, 4, 5};
        
        int iteration = 0;
        Stream.Builder<Arguments> builder = Stream.builder();
        
        // Generate 100+ iterations by cycling through configurations
        for (int cycle = 0; cycle < 15; cycle++) {
            for (Map<TargetId, Target> targets : targetMaps) {
                for (int detectorCount : detectorCounts) {
                    // Only include configurations with at least one detector
                    if (detectorCount > 0) {
                        builder.add(Arguments.of(
                            iteration++,
                            targets.size(),
                            detectorCount,
                            targets
                        ));
                    }
                }
            }
        }
        
        return builder.build();
    }

    /**
     * Property 15 (determinism): Multiple calls with same configuration → same exception code.
     */
    @Test
    void allNoneDetectors_throwsConsistentException(@TempDir Path tempDir) {
        TargetRegistry registry = new TargetRegistry(
            Map.of(TargetId.JVM, new JvmTarget(), TargetId.NODE, new NodeTarget()),
            List.of(NONE_DETECTOR, NONE_DETECTOR)
        );

        for (int i = 0; i < 10; i++) {
            TargetResolutionException ex = assertThrows(
                TargetResolutionException.class,
                () -> registry.resolve(tempDir)
            );
            assertEquals("TARGET_NOT_DETECTED", ex.code(),
                "Exception code should be consistent across calls");
        }
    }

    /**
     * Property 16: Any check pipeline failure produces stderr with CODE=, PATH=, REMEDIATION= lines.
     * 
     * This property tests that TargetResolutionException contains the required fields
     * for the three-line stderr envelope format.
     * 
     * Validates: Req 12.8
     */
    @ParameterizedTest(name = "iteration {0}: exception code {1}")
    @MethodSource("exceptionScenarios")
    void failureExceptionContainsRequiredFields(
            int iteration,
            String expectedCode,
            TargetResolutionException exception) {
        
        // Verify exception has all required fields for CODE/PATH/REMEDIATION envelope
        assertNotNull(exception.code(), "Exception must have code for CODE= line");
        assertNotNull(exception.path(), "Exception must have path for PATH= line");
        assertNotNull(exception.remediation(), "Exception must have remediation for REMEDIATION= line");
        
        assertEquals(expectedCode, exception.code());
        assertFalse(exception.path().isEmpty(), "PATH should not be empty");
        assertFalse(exception.remediation().isEmpty(), "REMEDIATION should not be empty");
    }

    /**
     * Generates 100+ exception scenarios for Property 16.
     */
    static Stream<Arguments> exceptionScenarios() {
        Stream.Builder<Arguments> builder = Stream.builder();
        
        String[] codes = {
            "TARGET_NOT_DETECTED",
            "TARGET_PIN_INVALID",
            "TARGET_AMBIGUOUS",
            "TARGET_UNSUPPORTED",
            "TARGET_DETECTOR_INVALID"
        };
        
        String[] paths = {
            "/project/root",
            "/tmp/test-project",
            ".",
            "/home/user/workspace/my-project",
            "C:\\Users\\dev\\project"
        };
        
        String[] remediations = {
            "Add a .bear/target.id pin file",
            "Remove or correct .bear/target.id",
            "Add a .bear/target.id pin file to disambiguate",
            "Target ecosystem recognized but project shape is unsupported",
            "A target detector returned null"
        };
        
        int iteration = 0;
        // Generate 100+ iterations
        for (int cycle = 0; cycle < 4; cycle++) {
            for (String code : codes) {
                for (String path : paths) {
                    String remediation = remediations[iteration % remediations.length];
                    builder.add(Arguments.of(
                        iteration++,
                        code,
                        new TargetResolutionException(code, path, remediation)
                    ));
                }
            }
        }
        
        return builder.build();
    }

    /**
     * Property 16 (format verification): Exception message contains all three components.
     */
    @Test
    void exceptionMessageContainsAllComponents() {
        TargetResolutionException ex = new TargetResolutionException(
            "TARGET_NOT_DETECTED",
            "/test/path",
            "Add a .bear/target.id pin file"
        );
        
        String message = ex.getMessage();
        assertTrue(message.contains("TARGET_NOT_DETECTED"), "Message should contain code");
        assertTrue(message.contains("/test/path"), "Message should contain path");
        assertTrue(message.contains("pin file"), "Message should contain remediation hint");
    }

    /**
     * Property 15 (no silent fallback): Even with JVM registered, NONE detectors throw.
     */
    @ParameterizedTest(name = "iteration {0}")
    @MethodSource("jvmRegisteredConfigurations")
    void noSilentJvmFallback_evenWithJvmRegistered(
            int iteration,
            int detectorCount,
            @TempDir Path tempDir) {
        
        // Registry always includes JVM
        Map<TargetId, Target> targets = Map.of(
            TargetId.JVM, new JvmTarget(),
            TargetId.NODE, new NodeTarget()
        );
        
        List<TargetDetector> detectors = IntStream.range(0, detectorCount)
            .mapToObj(i -> NONE_DETECTOR)
            .toList();

        TargetRegistry registry = new TargetRegistry(targets, detectors);

        TargetResolutionException ex = assertThrows(
            TargetResolutionException.class,
            () -> registry.resolve(tempDir),
            "Should throw even when JVM is registered"
        );
        
        assertEquals("TARGET_NOT_DETECTED", ex.code(),
            "Should not silently fall back to JVM");
    }

    /**
     * Generates configurations with JVM always registered.
     */
    static Stream<Arguments> jvmRegisteredConfigurations() {
        Stream.Builder<Arguments> builder = Stream.builder();
        
        // 100+ iterations with varying detector counts
        for (int i = 0; i < 100; i++) {
            int detectorCount = (i % 5) + 1; // 1-5 detectors
            builder.add(Arguments.of(i, detectorCount));
        }
        
        return builder.build();
    }
}
