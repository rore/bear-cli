package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.target.JvmTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmTargetTest {
    @Test
    void compileIsDeterministicAndCreatesExpectedFiles(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path goldenRoot = repoRoot.resolve("spec/golden/compile/withdraw");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        target.compile(normalized, tempDir);
        Map<String, String> first = readTree(tempDir.resolve("build/generated/bear"));
        target.compile(normalized, tempDir);
        Map<String, String> second = readTree(tempDir.resolve("build/generated/bear"));
        Map<String, String> expected = readTree(goldenRoot);

        assertEquals(first, second);
        assertEquals(expected, first);
        String withdrawJava = first.get("src/main/java/com/bear/generated/withdraw/Withdraw.java");
        assertTrue(withdrawJava.contains("idempotency replay payload missing field: result.balance"));
        String manifest = first.get("surfaces/withdraw.surface.json");
        assertTrue(manifest.contains("\"schemaVersion\":\"v0\""));
        assertTrue(manifest.contains("\"surfaceVersion\":2"));
        assertTrue(manifest.contains("\"target\":\"jvm\""));
        assertTrue(manifest.contains("\"capabilities\":[{\"name\":\"idempotency\",\"ops\":[\"get\",\"put\"]},{\"name\":\"ledger\",\"ops\":[\"getBalance\",\"setBalance\"]}]"));
        assertTrue(manifest.contains("\"invariants\":[{\"kind\":\"non_negative\",\"field\":\"balance\"}]"));
        assertTrue(manifest.contains("\"irHash\":\"1b6da2086a3ee4286d2f74fd10adb70e4c2cb9d7f49f826e562a0dc312ab6e38\""));
    }

    @Test
    void compileCreatesImplOnlyWhenMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        Path impl = tempDir.resolve("src/main/java/com/bear/generated/withdraw/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "package com.bear.generated.withdraw;\nclass KeepMe {}\n");

        target.compile(normalized, tempDir);
        assertEquals("package com.bear.generated.withdraw;\nclass KeepMe {}\n", Files.readString(impl));
    }

    @Test
    void compileNoDecimalEntrypointStillImportsBigDecimalForReplayDecode(@TempDir Path tempDir) throws Exception {
        Path irFile = tempDir.resolve("status.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v0\n"
            + "block:\n"
            + "  name: Status\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: requestId\n"
            + "        type: string\n"
            + "    outputs:\n"
            + "      - name: ok\n"
            + "        type: bool\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    key: requestId\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(irFile);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);
        target.compile(normalized, tempDir);

        Path entrypoint = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/status/Status.java");
        String entry = Files.readString(entrypoint);
        assertTrue(entry.contains("import java.math.BigDecimal;"));
        assertTrue(entry.contains("if (type == BigDecimal.class)"));
    }

    @Test
    void compilePortNameAlreadyEndingWithPortDoesNotDoubleSuffix(@TempDir Path tempDir) throws Exception {
        Path irFile = tempDir.resolve("notify.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v0\n"
            + "block:\n"
            + "  name: Notify\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: requestId\n"
            + "        type: string\n"
            + "    outputs:\n"
            + "      - name: accepted\n"
            + "        type: bool\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: notificationPort\n"
            + "        ops: [emit]\n");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(irFile);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);
        target.compile(normalized, tempDir);

        Path generatedMain = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/notify");
        assertTrue(Files.exists(generatedMain.resolve("NotificationPort.java")));
        assertTrue(Files.notExists(generatedMain.resolve("NotificationPortPort.java")));
    }

    @Test
    void compileLockFailureIsDeterministicAndKeepsExistingFiles(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);
        target.compile(normalized, tempDir);

        Path generatedMain = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw");
        Path stale = generatedMain.resolve("LOCKED.tmp");
        Files.writeString(stale, "stale");

        String previous = System.getProperty("bear.compile.test.lockPathContains");
        try {
            System.setProperty("bear.compile.test.lockPathContains", "LOCKED.tmp");
            boolean threw = false;
            try {
                target.compile(normalized, tempDir);
            } catch (IOException e) {
                threw = true;
                assertTrue(e.getMessage().startsWith("WINDOWS_FILE_LOCK: delete blocked at "));
                assertTrue(e.getMessage().contains("LOCKED.tmp"));
            }
            assertTrue(threw);
        } finally {
            if (previous == null) {
                System.clearProperty("bear.compile.test.lockPathContains");
            } else {
                System.setProperty("bear.compile.test.lockPathContains", previous);
            }
        }

        assertTrue(Files.exists(generatedMain.resolve("Withdraw.java")));
        assertTrue(Files.exists(stale));
    }

    private static Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .forEach(path -> {
                    String rel = root.relativize(path).toString().replace('\\', '/');
                    try {
                        files.put(rel, normalizeLf(Files.readString(path)));
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
        return files;
    }

    private static String normalizeLf(String text) {
        return text.replace("\r\n", "\n");
    }
}
