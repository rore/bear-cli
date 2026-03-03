package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.target.JvmTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmTargetTest {
    @Test
    void compileGeneratesPerOperationWrappersAndSharedLogic(@TempDir Path tempDir) throws Exception {
        BearIr ir = parseNormalizedFixture();
        JvmTarget target = new JvmTarget();

        target.compile(ir, tempDir, "withdraw");

        Path base = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw");
        assertTrue(Files.exists(base.resolve("WithdrawLogic.java")));
        assertTrue(Files.exists(base.resolve("Withdraw_ExecuteWithdraw.java")));
        assertTrue(Files.exists(base.resolve("Withdraw_ExecuteWithdrawRequest.java")));
        assertTrue(Files.exists(base.resolve("Withdraw_ExecuteWithdrawResult.java")));
        assertTrue(Files.exists(base.resolve("LedgerPort.java")));
        assertTrue(Files.exists(base.resolve("IdempotencyPort.java")));

        String logic = Files.readString(base.resolve("WithdrawLogic.java"));
        assertTrue(logic.contains("Withdraw_ExecuteWithdrawResult executeExecuteWithdraw("));
        String wrapper = Files.readString(base.resolve("Withdraw_ExecuteWithdraw.java"));
        assertTrue(wrapper.contains("private static final String OPERATION_NAME = \"ExecuteWithdraw\";"));
        assertTrue(wrapper.contains("key.append(\"|op=\")"));
    }

    @Test
    void compileIsDeterministic(@TempDir Path tempDir) throws Exception {
        BearIr ir = parseNormalizedFixture();
        JvmTarget target = new JvmTarget();

        target.compile(ir, tempDir, "withdraw");
        Map<String, String> first = readTree(tempDir.resolve("build/generated/bear"));
        target.compile(ir, tempDir, "withdraw");
        Map<String, String> second = readTree(tempDir.resolve("build/generated/bear"));

        assertEquals(first, second);
    }

    @Test
    void generateWiringOnlyMatchesCompileWiring(@TempDir Path tempDir) throws Exception {
        BearIr ir = parseNormalizedFixture();
        JvmTarget target = new JvmTarget();

        Path compileRoot = tempDir.resolve("compile-root");
        target.compile(ir, compileRoot, "withdraw");
        Path compileWiring = compileRoot.resolve("build/generated/bear/wiring/withdraw.wiring.json");

        Path wiringOnlyRoot = tempDir.resolve("wiring-only");
        target.generateWiringOnly(ir, compileRoot, wiringOnlyRoot, "withdraw");
        Path wiringOnly = wiringOnlyRoot.resolve("wiring/withdraw.wiring.json");
        assertEquals(Files.readString(compileWiring), Files.readString(wiringOnly));
    }

    @Test
    void compileCreatesSingleSharedImplStubWhenMissing(@TempDir Path tempDir) throws Exception {
        BearIr ir = parseNormalizedFixture();
        JvmTarget target = new JvmTarget();

        target.compile(ir, tempDir, "withdraw");
        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        assertTrue(Files.exists(impl));
        String text = Files.readString(impl);
        assertTrue(text.contains("implements WithdrawLogic"));
        assertTrue(text.contains("executeExecuteWithdraw("));
    }

    @Test
    void compileWiringManifestStillUsesV2Contract(@TempDir Path tempDir) throws Exception {
        BearIr ir = parseNormalizedFixture();
        JvmTarget target = new JvmTarget();

        target.compile(ir, tempDir, "withdraw");
        String wiring = Files.readString(tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json"));
        assertTrue(wiring.contains("\"schemaVersion\":\"v2\""));
        assertTrue(wiring.contains("\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\""));
        assertTrue(wiring.contains("\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/_shared\"]"));
        assertTrue(wiring.contains("\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\""));
    }

    private static BearIr parseNormalizedFixture() throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("spec/fixtures/withdraw.bear.yaml");
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        return normalizer.normalize(ir);
    }

    private static Map<String, String> readTree(Path root) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .forEach(path -> {
                    String rel = root.relativize(path).toString().replace('\\', '/');
                    if (rel.startsWith(".staging/")) {
                        return;
                    }
                    try {
                        files.put(rel, Files.readString(path).replace("\r\n", "\n"));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }
        return files;
    }
}
