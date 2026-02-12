package com.bear.kernel;

import java.nio.file.Files;
import java.nio.file.Path;

final class TestRepoPaths {
    private TestRepoPaths() {
    }

    static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path p = current; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("settings.gradle"))) {
                return p;
            }
        }
        throw new IllegalStateException("Could not locate repo root (settings.gradle not found)");
    }
}

