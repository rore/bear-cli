package com.bear.kernel.target;

import java.io.IOException;
import java.io.UncheckedIOException;
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
            String content = Files.readString(pinFile, java.nio.charset.StandardCharsets.UTF_8);
            if (content.isEmpty()) {
                throw new IllegalArgumentException("target.id file is empty");
            }
            // Allow an optional single trailing newline (LF or CRLF) for editors and tools
            // that append a trailing newline, but otherwise require the content to be exactly the target token.
            String value;
            if (content.endsWith("\r\n")) {
                value = content.substring(0, content.length() - 2);
            } else if (content.endsWith("\n")) {
                value = content.substring(0, content.length() - 1);
            } else {
                value = content;
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("target.id file does not contain a target id");
            }
            // After removing at most one trailing newline, no whitespace is allowed
            if (value.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException(
                    "target.id must contain exactly one of: jvm/node/python/react with no whitespace");
            }
            TargetId targetId = TargetId.fromValue(value);
            return Optional.of(targetId);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + pinFile, e);
        }
    }
}
