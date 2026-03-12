package com.bear.kernel.target;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class TargetPinFile {

    public static Optional<TargetId> read(Path bearDir) {
        Path pinFile = bearDir.resolve("target.id");
        if (!Files.exists(pinFile)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(pinFile);
            String trimmed = content.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("target.id file is empty or contains only whitespace");
            }
            TargetId targetId = TargetId.fromValue(trimmed);
            return Optional.of(targetId);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read target.id file", e);
        }
    }
}
