package com.bear.kernel;

import com.bear.kernel.policy.SharedAllowedDepsPolicy;
import com.bear.kernel.policy.SharedAllowedDepsPolicyException;
import com.bear.kernel.policy.SharedAllowedDepsPolicyParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedAllowedDepsPolicyParserTest {
    @Test
    void parseValidPolicySortsDependencies(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, ""
            + "version: v1\n"
            + "scope: shared\n"
            + "impl:\n"
            + "  allowedDeps:\n"
            + "    - maven: com.zeta:beta\n"
            + "      version: 2.0.0\n"
            + "    - maven: com.alpha:core\n"
            + "      version: 1.0.0\n");

        SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
        SharedAllowedDepsPolicy parsed = parser.parse(policy);

        assertEquals(2, parsed.allowedDeps().size());
        assertEquals("com.alpha:core", parsed.allowedDeps().get(0).maven());
        assertEquals("1.0.0", parsed.allowedDeps().get(0).version());
        assertEquals("com.zeta:beta", parsed.allowedDeps().get(1).maven());
        assertEquals("2.0.0", parsed.allowedDeps().get(1).version());
    }

    @Test
    void parseMissingPolicyReturnsEmpty(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
        SharedAllowedDepsPolicy parsed = parser.parse(policy);
        assertEquals(0, parsed.allowedDeps().size());
    }

    @Test
    void parseRejectsUnknownKeys(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, ""
            + "version: v1\n"
            + "scope: shared\n"
            + "unexpected: true\n"
            + "impl:\n"
            + "  allowedDeps: []\n");

        SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
        SharedAllowedDepsPolicyException ex = assertThrows(SharedAllowedDepsPolicyException.class, () -> parser.parse(policy));
        assertEquals("UNKNOWN_KEY", ex.reasonCode());
    }

    @Test
    void parseRejectsInvalidVersionOrScope(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, ""
            + "version: v2\n"
            + "scope: shared\n"
            + "impl:\n"
            + "  allowedDeps: []\n");
        SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
        SharedAllowedDepsPolicyException versionEx = assertThrows(SharedAllowedDepsPolicyException.class, () -> parser.parse(policy));
        assertEquals("INVALID_VERSION", versionEx.reasonCode());

        Files.writeString(policy, ""
            + "version: v1\n"
            + "scope: block\n"
            + "impl:\n"
            + "  allowedDeps: []\n");
        SharedAllowedDepsPolicyException scopeEx = assertThrows(SharedAllowedDepsPolicyException.class, () -> parser.parse(policy));
        assertEquals("INVALID_SCOPE", scopeEx.reasonCode());
    }

    @Test
    void parseRejectsDuplicateMavenEntries(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(policy, ""
            + "version: v1\n"
            + "scope: shared\n"
            + "impl:\n"
            + "  allowedDeps:\n"
            + "    - maven: com.alpha:core\n"
            + "      version: 1.0.0\n"
            + "    - maven: com.alpha:core\n"
            + "      version: 1.0.1\n");

        SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();
        SharedAllowedDepsPolicyException ex = assertThrows(SharedAllowedDepsPolicyException.class, () -> parser.parse(policy));
        assertEquals("DUPLICATE_ALLOWED_DEP", ex.reasonCode());
    }

    @Test
    void parseRejectsMalformedCoordinatesAndUnpinnedVersions(@TempDir Path tempDir) throws Exception {
        Path policy = tempDir.resolve("bear-policy/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        SharedAllowedDepsPolicyParser parser = new SharedAllowedDepsPolicyParser();

        Files.writeString(policy, ""
            + "version: v1\n"
            + "scope: shared\n"
            + "impl:\n"
            + "  allowedDeps:\n"
            + "    - maven: com.alpha\n"
            + "      version: 1.0.0\n");
        SharedAllowedDepsPolicyException coordEx = assertThrows(SharedAllowedDepsPolicyException.class, () -> parser.parse(policy));
        assertEquals("INVALID_MAVEN_COORDINATE", coordEx.reasonCode());

        Files.writeString(policy, ""
            + "version: v1\n"
            + "scope: shared\n"
            + "impl:\n"
            + "  allowedDeps:\n"
            + "    - maven: com.alpha:core\n"
            + "      version: \"[1.0,2.0)\"\n");
        SharedAllowedDepsPolicyException versionEx = assertThrows(SharedAllowedDepsPolicyException.class, () -> parser.parse(policy));
        assertEquals("INVALID_PINNED_VERSION", versionEx.reasonCode());
    }
}
