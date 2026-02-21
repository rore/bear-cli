package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockIndexParserTest {
    @Test
    void parsesValidIndex(@TempDir Path tempDir) throws Exception {
        Path index = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(index, ""
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/a.bear.yaml\n"
            + "    projectRoot: services/a\n", StandardCharsets.UTF_8);

        BlockIndex parsed = new BlockIndexParser().parse(tempDir, index);
        assertEquals("v0", parsed.version());
        assertEquals(1, parsed.blocks().size());
        assertTrue(parsed.blocks().get(0).enabled());
    }

    @Test
    void allowsDuplicateEnabledProjectRoot(@TempDir Path tempDir) throws Exception {
        Path index = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(index, ""
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/a.bear.yaml\n"
            + "    projectRoot: services/a\n"
            + "  - name: beta\n"
            + "    ir: spec/b.bear.yaml\n"
            + "    projectRoot: services/a\n", StandardCharsets.UTF_8);

        BlockIndex parsed = new BlockIndexParser().parse(tempDir, index);
        assertEquals(2, parsed.blocks().size());
    }

    @Test
    void rejectsInvalidName(@TempDir Path tempDir) throws Exception {
        Path index = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(index, ""
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: Alpha\n"
            + "    ir: spec/a.bear.yaml\n"
            + "    projectRoot: services/a\n", StandardCharsets.UTF_8);

        BlockIndexValidationException error = assertThrows(
            BlockIndexValidationException.class,
            () -> new BlockIndexParser().parse(tempDir, index)
        );
        assertTrue(error.getMessage().contains("name must match"));
    }

    @Test
    void allowsProjectRootDotAndCanonicalizes(@TempDir Path tempDir) throws Exception {
        Path index = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(index, ""
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/a.bear.yaml\n"
            + "    projectRoot: .\n", StandardCharsets.UTF_8);

        BlockIndex parsed = new BlockIndexParser().parse(tempDir, index);
        assertEquals(".", parsed.blocks().get(0).projectRoot());
    }

    @Test
    void rejectsIrDot(@TempDir Path tempDir) throws Exception {
        Path index = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(index, ""
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: .\n"
            + "    projectRoot: .\n", StandardCharsets.UTF_8);

        BlockIndexValidationException error = assertThrows(
            BlockIndexValidationException.class,
            () -> new BlockIndexParser().parse(tempDir, index)
        );
        assertTrue(error.path().endsWith(".ir"));
        assertTrue(error.getMessage().contains("path must be repo-relative"));
    }

    @Test
    void rejectsDuplicateTupleWhenStrictGuardEnabled(@TempDir Path tempDir) throws Exception {
        Path index = tempDir.resolve("bear.blocks.yaml");
        Files.writeString(index, ""
            + "version: v0\n"
            + "blocks:\n"
            + "  - name: alpha\n"
            + "    ir: spec/a.bear.yaml\n"
            + "    projectRoot: services/a\n"
            + "  - name: beta\n"
            + "    ir: spec/a.bear.yaml\n"
            + "    projectRoot: services/a\n", StandardCharsets.UTF_8);

        BlockIndexValidationException error = assertThrows(
            BlockIndexValidationException.class,
            () -> new BlockIndexParser().parse(tempDir, index, true)
        );
        assertTrue(error.getMessage().contains("duplicate (ir,projectRoot) tuple"));
    }
}
