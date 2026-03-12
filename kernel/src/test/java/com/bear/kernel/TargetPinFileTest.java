package com.bear.kernel;

import com.bear.kernel.target.TargetId;
import com.bear.kernel.target.TargetPinFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetPinFileTest {

    @Test
    void validJvmPin(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("target.id"), "jvm");

        Optional<TargetId> result = TargetPinFile.read(tempDir);

        assertEquals(Optional.of(TargetId.JVM), result);
    }

    @Test
    void validNodePin(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("target.id"), "node");

        Optional<TargetId> result = TargetPinFile.read(tempDir);

        assertEquals(Optional.of(TargetId.NODE), result);
    }

    @Test
    void invalidPin(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("target.id"), "unknown");

        assertThrows(IllegalArgumentException.class, () -> TargetPinFile.read(tempDir));
    }

    @Test
    void emptyFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("target.id"), "");

        assertThrows(IllegalArgumentException.class, () -> TargetPinFile.read(tempDir));
    }

    @Test
    void whitespaceOnlyFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("target.id"), "  \n  ");

        assertThrows(IllegalArgumentException.class, () -> TargetPinFile.read(tempDir));
    }

    @Test
    void missingFile(@TempDir Path tempDir) {
        Optional<TargetId> result = TargetPinFile.read(tempDir);

        assertTrue(result.isEmpty());
    }
}
