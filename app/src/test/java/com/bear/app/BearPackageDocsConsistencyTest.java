package com.bear.app;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearPackageDocsConsistencyTest {
    @Test
    void irExamplesUseV1ForIrSchemas() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String content = Files.readString(repoRoot.resolve("doc/bear-package/IR_EXAMPLES.md"), StandardCharsets.UTF_8);

        Pattern deprecatedIrVersion = Pattern.compile("(?im)^\\s*version:\\s*v0\\s*\\R\\s*block:");
        assertFalse(deprecatedIrVersion.matcher(content).find(), "IR examples must use version: v1");
        assertFalse(content.contains("valid BEAR v0"), "IR examples must not describe BEAR v0 IR as canonical");
    }

    @Test
    void primerUsesV1InvariantLanguage() throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        String content = Files.readString(repoRoot.resolve("doc/bear-package/BEAR_PRIMER.md"), StandardCharsets.UTF_8);
        assertFalse(content.contains("v0 supports `non_negative`"));
        assertTrue(content.contains("v1 supports `non_negative`"));
    }
}
