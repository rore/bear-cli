package com.bear.kernel.target.jvm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class JvmFileSyncSupport {
    private JvmFileSyncSupport() {
    }

    static void deleteDirectoryIfExists(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    deletePath(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    static void write(Path file, String content) throws IOException {
        runWithFileLockRetry("write", file, () -> {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        });
    }

    static void writeIfDifferent(Path file, String content) throws IOException {
        byte[] next = content.getBytes(StandardCharsets.UTF_8);
        if (Files.isRegularFile(file)) {
            byte[] current = Files.readAllBytes(file);
            if (MessageDigest.isEqual(current, next)) {
                return;
            }
        }
        write(file, content);
    }

    static void syncDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        TreeMap<String, Path> sourceFiles = new TreeMap<>();
        if (Files.isDirectory(sourceDir)) {
            try (var stream = Files.walk(sourceDir)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String rel = sourceDir.relativize(path).toString().replace('\\', '/');
                    sourceFiles.put(rel, path);
                });
            }
        }

        for (Map.Entry<String, Path> entry : sourceFiles.entrySet()) {
            Path target = targetDir.resolve(entry.getKey().replace('/', java.io.File.separatorChar));
            copyFile(entry.getValue(), target);
        }

        List<Path> staleFiles = new ArrayList<>();
        if (Files.isDirectory(targetDir)) {
            try (var stream = Files.walk(targetDir)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String rel = targetDir.relativize(path).toString().replace('\\', '/');
                    if (!sourceFiles.containsKey(rel)) {
                        staleFiles.add(path);
                    }
                });
            }
        }
        staleFiles.sort(Comparator.reverseOrder());
        for (Path stale : staleFiles) {
            deletePath(stale);
        }
        deleteEmptyDirectories(targetDir);
    }

    static void syncFile(Path sourceFile, Path targetFile) throws IOException {
        copyFile(sourceFile, targetFile);
    }

    static void deletePath(Path path) throws IOException {
        runWithFileLockRetry("delete", path, () -> Files.deleteIfExists(path));
    }

    private static void copyFile(Path sourceFile, Path targetFile) throws IOException {
        byte[] content = Files.readAllBytes(sourceFile);
        try {
            runWithFileLockRetry("replace", targetFile, () -> {
                Files.createDirectories(targetFile.getParent());
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            });
        } catch (IOException e) {
            if (!canFallbackToInPlaceRewrite(targetFile, e)) {
                throw e;
            }
            runWithFileLockRetry("rewrite", targetFile, () -> {
                Files.write(
                    targetFile,
                    content,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
                );
            });
        }
    }

    private static void deleteEmptyDirectories(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> directories = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory).forEach(directories::add);
        }
        directories.sort(Comparator.reverseOrder());
        for (Path directory : directories) {
            if (directory.equals(root)) {
                continue;
            }
            try (var listing = Files.list(directory)) {
                if (!listing.findAny().isPresent()) {
                    deletePath(directory);
                }
            }
        }
    }

    private static void runWithFileLockRetry(String action, Path path, IoOperation op) throws IOException {
        int maxAttempts = 6;
        int attempt = 0;
        while (true) {
            try {
                maybeInjectLockForTest(action, path);
                op.run();
                return;
            } catch (IOException e) {
                if (!isRetryableFileLockError(e) || attempt >= maxAttempts - 1) {
                    throw toDeterministicLockError(action, path, e);
                }
                attempt++;
                sleepBackoff(attempt);
            }
        }
    }

    private static void maybeInjectLockForTest(String action, Path path) throws IOException {
        String needle = System.getProperty("bear.compile.test.lockPathContains");
        if (needle == null || needle.isBlank()) {
            return;
        }
        String configuredAction = System.getProperty("bear.compile.test.lockAction");
        if (configuredAction != null && !configuredAction.isBlank()
            && !configuredAction.equalsIgnoreCase(action)) {
            return;
        }
        String normalizedPath = path.toString().replace('\\', '/');
        String normalizedNeedle = needle.replace('\\', '/');
        if (normalizedPath.contains(normalizedNeedle)) {
            throw new AccessDeniedException(path.toString(), null, "PROJECT_TEST_WINDOWS_FILE_LOCK_SIMULATED");
        }
    }

    private static IOException toDeterministicLockError(String action, Path path, IOException cause) {
        if (!isRetryableFileLockError(cause)) {
            return cause;
        }
        String normalized = path.toString().replace('\\', '/');
        return new IOException(
            "WINDOWS_FILE_LOCK: " + action + " blocked at " + normalized
                + " (close file handles and rerun bear compile)",
            cause
        );
    }

    private static boolean isRetryableFileLockError(IOException e) {
        if (e instanceof AccessDeniedException) {
            return true;
        }
        if (e instanceof FileSystemException) {
            String reason = ((FileSystemException) e).getReason();
            if (reason != null && looksLikeLockMessage(reason)) {
                return true;
            }
        }
        String message = e.getMessage();
        return message != null && looksLikeLockMessage(message);
    }

    private static boolean looksLikeLockMessage(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("access is denied")
            || lower.contains("being used by another process")
            || lower.contains("used by another process")
            || lower.contains("file is locked");
    }

    private static boolean canFallbackToInPlaceRewrite(Path targetFile, IOException e) {
        return isLockFailureForFallback(e) && Files.isRegularFile(targetFile) && Files.isWritable(targetFile);
    }

    private static boolean isLockFailureForFallback(IOException e) {
        if (isRetryableFileLockError(e)) {
            return true;
        }
        String message = e.getMessage();
        return message != null && message.contains("WINDOWS_FILE_LOCK:");
    }

    private static void sleepBackoff(int attempt) throws IOException {
        long millis = Math.min(800L, 50L * (1L << Math.min(4, attempt)));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("INTERRUPTED_DURING_RETRY", e);
        }
    }

    @FunctionalInterface
    private interface IoOperation {
        void run() throws IOException;
    }
}


