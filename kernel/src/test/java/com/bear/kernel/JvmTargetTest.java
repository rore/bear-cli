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
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmTargetTest {
    @Test
    void compileIsDeterministicAndCreatesExpectedFiles(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path goldenRoot = repoRoot.resolve("spec/golden/compile/withdraw-v1");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        target.compile(normalized, tempDir, "withdraw");
        Map<String, String> first = readTree(tempDir.resolve("build/generated/bear"));
        target.compile(normalized, tempDir, "withdraw");
        Map<String, String> second = readTree(tempDir.resolve("build/generated/bear"));
        Map<String, String> expected = readTree(goldenRoot);

        assertEquals(first, second);
        assertEquals(expected, first);
        String withdrawJava = first.get("src/main/java/com/bear/generated/withdraw/Withdraw.java");
        assertTrue(withdrawJava.contains("public static Withdraw of(IdempotencyPort idempotencyPort, LedgerPort ledgerPort)"));
        assertTrue(withdrawJava.contains("return new Withdraw(idempotencyPort, ledgerPort, new blocks.withdraw.impl.WithdrawImpl());"));
        assertTrue(withdrawJava.contains("idempotency replay payload missing field: result.balance"));
        assertFalse(first.containsKey("src/test/java/com/bear/generated/withdraw/WithdrawIdempotencyTest.java"));
        assertFalse(first.containsKey("src/test/java/com/bear/generated/withdraw/WithdrawInvariantNonNegativeTest.java"));
        String directionTest = first.get("src/test/java/com/bear/generated/withdraw/WithdrawStructuralDirectionTest.java");
        assertTrue(directionTest.contains("\"BEAR_STRUCTURAL_SIGNAL|blockKey=\""));
        assertTrue(directionTest.contains("+ BLOCK_KEY"));
        assertTrue(directionTest.contains("+ \"|test=\""));
        assertTrue(directionTest.contains("+ TEST_NAME"));
        assertTrue(directionTest.contains("+ \"|kind=\""));
        assertTrue(directionTest.contains("Boolean.getBoolean(\"bear.structural.tests.strict\")"));
        assertTrue(directionTest.contains("Collections.sort(mismatches);"));
        assertTrue(directionTest.contains("fail(\"BEAR_STRUCTURAL_STRICT_FAILURE|\" + TEST_NAME + \"|\" + String.join(\"\\n\", mismatches));"));
        assertFalse(directionTest.contains("blocks.withdraw.impl.WithdrawImpl"));
        String reachTest = first.get("src/test/java/com/bear/generated/withdraw/WithdrawStructuralReachTest.java");
        assertTrue(reachTest.contains("\"BEAR_STRUCTURAL_SIGNAL|blockKey=\""));
        assertTrue(reachTest.contains("+ BLOCK_KEY"));
        assertTrue(reachTest.contains("+ \"|test=\""));
        assertTrue(reachTest.contains("+ TEST_NAME"));
        assertTrue(reachTest.contains("+ \"|kind=\""));
        assertTrue(reachTest.contains("Boolean.getBoolean(\"bear.structural.tests.strict\")"));
        assertTrue(reachTest.contains("Collections.sort(mismatches);"));
        assertTrue(reachTest.contains("fail(\"BEAR_STRUCTURAL_STRICT_FAILURE|\" + TEST_NAME + \"|\" + String.join(\"\\n\", mismatches));"));
        assertTrue(reachTest.contains("IdempotencyPort#get(BearValue)"));
        assertTrue(reachTest.contains("LedgerPort#getBalance(BearValue)"));
        String manifest = first.get("surfaces/withdraw.surface.json");
        assertTrue(manifest.contains("\"schemaVersion\":\"v1\""));
        assertTrue(manifest.contains("\"surfaceVersion\":3"));
        assertTrue(manifest.contains("\"target\":\"jvm\""));
        assertTrue(manifest.contains("\"capabilities\":[{\"name\":\"idempotency\",\"ops\":[\"get\",\"put\"]},{\"name\":\"ledger\",\"ops\":[\"getBalance\",\"setBalance\"]}]"));
        assertTrue(manifest.contains("\"allowedDeps\":[]"));
        assertTrue(manifest.contains("\"invariants\":[{\"kind\":\"non_negative\",\"field\":\"balance\"}]"));
        assertTrue(manifest.contains("\"irHash\":\"32e65c3c25e5d88bd16ddffdb8071b13cca68ef762a71154ca972d8a88a4ae9d\""));
        String wiring = first.get("wiring/withdraw.wiring.json");
        assertTrue(wiring.contains("\"schemaVersion\":\"v2\""));
        assertTrue(wiring.contains("\"blockKey\":\"withdraw\""));
        assertTrue(wiring.contains("\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\""));
        assertTrue(wiring.contains("\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\""));
        assertTrue(wiring.contains("\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\""));
        assertTrue(wiring.contains("\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\""));
        assertTrue(wiring.contains("\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\""));
        assertTrue(wiring.contains("\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/_shared\"]"));
        assertTrue(wiring.contains("\"requiredEffectPorts\":[\"idempotencyPort\",\"ledgerPort\"]"));
        assertTrue(wiring.contains("\"constructorPortParams\":[\"idempotencyPort\",\"ledgerPort\"]"));
        assertTrue(wiring.contains("\"logicRequiredPorts\":[\"ledgerPort\"]"));
        assertTrue(wiring.contains("\"wrapperOwnedSemanticPorts\":[\"idempotencyPort\"]"));
        assertTrue(wiring.contains("\"wrapperOwnedSemanticChecks\":[\"IDEMPOTENCY\",\"INVARIANTS\"]"));
        String containmentGradle = first.get("gradle/bear-containment.gradle");
        assertFalse(containmentGradle.contains("exclude('blocks/**/impl/**')"));
    }

    @Test
    void compileAlwaysIncludesSharedGovernedRoot(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        target.compile(normalized, tempDir, "withdraw");

        Path wiringPath = tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json");
        String wiring = Files.readString(wiringPath);
        assertTrue(wiring.contains(
            "\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/_shared\"]"
        ));
    }

    @Test
    void compileIncludesSharedContainmentUnitWhenPolicyExists(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path policy = tempDir.resolve("spec/_shared.policy.yaml");
        Files.createDirectories(policy.getParent());
        Files.writeString(
            policy,
            ""
                + "version: v1\n"
                + "scope: shared\n"
                + "impl:\n"
                + "  allowedDeps:\n"
                + "    - maven: com.fasterxml.jackson.core:jackson-databind\n"
                + "      version: 2.17.2\n"
        );

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        target.compile(normalizer.normalize(ir), tempDir, "withdraw");

        String index = Files.readString(tempDir.resolve("build/generated/bear/config/containment-required.json"));
        assertTrue(index.contains("\"blockKey\":\"_shared\""));
        assertTrue(Files.isRegularFile(tempDir.resolve("build/generated/bear/config/allowed-deps/_shared.json")));
    }

    @Test
    void compileIncludesSharedContainmentUnitWhenSharedSourcesExist(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path sharedSource = tempDir.resolve("src/main/java/blocks/_shared/SharedOnly.java");
        Files.createDirectories(sharedSource.getParent());
        Files.writeString(sharedSource, "package blocks._shared;\npublic final class SharedOnly {}\n");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        target.compile(normalizer.normalize(ir), tempDir, "withdraw");

        String index = Files.readString(tempDir.resolve("build/generated/bear/config/containment-required.json"));
        assertTrue(index.contains("\"blockKey\":\"_shared\""));
        String sharedConfig = Files.readString(tempDir.resolve("build/generated/bear/config/allowed-deps/_shared.json"));
        assertTrue(sharedConfig.contains("\"allowedDeps\":[]"));
    }

    @Test
    void compileOmitsSharedContainmentUnitWhenOutOfScope(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        target.compile(normalizer.normalize(ir), tempDir, "withdraw");

        String index = Files.readString(tempDir.resolve("build/generated/bear/config/containment-required.json"));
        assertFalse(index.contains("\"blockKey\":\"_shared\""));
        assertFalse(Files.exists(tempDir.resolve("build/generated/bear/config/allowed-deps/_shared.json")));
    }

    @Test
    void compileContainmentGradleEntrypointWiresSharedClasspathContracts(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        Path sharedSource = tempDir.resolve("src/main/java/blocks/_shared/SharedOnly.java");
        Files.createDirectories(sharedSource.getParent());
        Files.writeString(sharedSource, "package blocks._shared;\npublic final class SharedOnly {}\n");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        target.compile(normalizer.normalize(ir), tempDir, "withdraw");

        String entrypoint = Files.readString(tempDir.resolve("build/generated/bear/gradle/bear-containment.gradle"));
        assertTrue(entrypoint.contains("def isSharedUnit = (blockKey == '_shared')"));
        assertTrue(entrypoint.contains("file(\"$buildDir/bear/shared-classes/_shared\")"));
        assertTrue(entrypoint.contains("dependsOn(compileTaskName)"));
        assertTrue(entrypoint.contains("classpath = classpath + files(outputDir)"));
        assertTrue(entrypoint.contains("testSourceSet.compileClasspath += files(outputDir)"));
        assertTrue(entrypoint.contains("testSourceSet.runtimeClasspath += files(outputDir)"));
    }

    @Test
    void generateWiringOnlyMatchesCompileWiringBytes(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        Path compileProjectRoot = tempDir.resolve("compile-project");
        target.compile(normalized, compileProjectRoot, "withdraw");
        Path compileWiring = compileProjectRoot.resolve("build/generated/bear/wiring/withdraw.wiring.json");

        Path wiringOnlyRoot = tempDir.resolve("wiring-only");
        target.generateWiringOnly(normalized, compileProjectRoot, wiringOnlyRoot, "withdraw");
        Path wiringOnly = wiringOnlyRoot.resolve("wiring/withdraw.wiring.json");

        assertEquals(Files.readString(compileWiring), Files.readString(wiringOnly));
    }

    @Test
    void generateWiringOnlyDoesNotEmitJavaSources(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);

        target.generateWiringOnly(normalized, tempDir, tempDir.resolve("wiring-only"), "withdraw");

        Path wiringOnlyRoot = tempDir.resolve("wiring-only");
        assertTrue(Files.isRegularFile(wiringOnlyRoot.resolve("wiring/withdraw.wiring.json")));
        assertFalse(Files.exists(wiringOnlyRoot.resolve("src")));
        long javaCount;
        try (var stream = Files.walk(wiringOnlyRoot)) {
            javaCount = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .count();
        }
        assertEquals(0L, javaCount);
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

        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "package blocks.withdraw.impl;\nclass KeepMe {}\n");

        target.compile(normalized, tempDir, "withdraw");
        assertEquals("package blocks.withdraw.impl;\nclass KeepMe {}\n", Files.readString(impl));
    }

    @Test
    void compileUsesSanitizedImplPackagePathForMultiTokenBlockName(@TempDir Path tempDir) throws Exception {
        Path irFile = tempDir.resolve("create-wallet.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: CreateWallet\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: ownerId\n"
            + "        type: string\n"
            + "    outputs:\n"
            + "      - name: walletId\n"
            + "        type: string\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: walletStore\n"
            + "        ops: [put]\n");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(irFile);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);
        target.compile(normalized, tempDir, "create-wallet");

        Path impl = tempDir.resolve("src/main/java/blocks/create/wallet/impl/CreateWalletImpl.java");
        assertTrue(Files.exists(impl));
        String content = Files.readString(impl);
        assertTrue(content.contains("package blocks.create.wallet.impl;"));
        assertFalse(Files.exists(tempDir.resolve("src/main/java/blocks/create-wallet/impl/CreateWalletImpl.java")));
        Path wiring = tempDir.resolve("build/generated/bear/wiring/create-wallet.wiring.json");
        assertTrue(Files.exists(wiring));
        String wiringContent = Files.readString(wiring);
        assertTrue(wiringContent.contains("\"implFqcn\":\"blocks.create.wallet.impl.CreateWalletImpl\""));
        assertTrue(wiringContent.contains("\"implSourcePath\":\"src/main/java/blocks/create/wallet/impl/CreateWalletImpl.java\""));
        assertTrue(wiringContent.contains("\"blockRootSourceDir\":\"src/main/java/blocks/create/wallet\""));
        assertTrue(wiringContent.contains("\"constructorPortParams\":[\"walletStorePort\"]"));
        Path wrapper = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/create/wallet/CreateWallet.java");
        String wrapperContent = Files.readString(wrapper);
        assertTrue(wrapperContent.contains("public static CreateWallet of(WalletStorePort walletStorePort)"));
        assertTrue(wrapperContent.contains("new blocks.create.wallet.impl.CreateWalletImpl()"));
    }

    @Test
    void compileNoDecimalEntrypointStillImportsBigDecimalForReplayDecode(@TempDir Path tempDir) throws Exception {
        Path irFile = tempDir.resolve("status.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
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
        target.compile(normalized, tempDir, "status");

        Path entrypoint = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/status/Status.java");
        String entry = Files.readString(entrypoint);
        assertTrue(entry.contains("import java.math.BigDecimal;"));
        assertTrue(entry.contains("if (type == BigDecimal.class)"));
        assertTrue(entry.contains("if (\"true\".equals(text)) return (T) Boolean.TRUE;"));
        assertTrue(entry.contains("if (\"false\".equals(text)) return (T) Boolean.FALSE;"));
        assertTrue(entry.contains("idempotency replay payload invalid value: \" + fieldName"));
    }

    @Test
    void compileGeneratesProjectGlobalRuntimeExceptionOnceAndUsesWriteIfDiff(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        target.compile(normalizer.normalize(ir), tempDir, "withdraw");

        Path secondaryIr = tempDir.resolve("status.bear.yaml");
        Files.writeString(secondaryIr, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: Status\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs: [{name: requestId, type: string}]\n"
            + "    outputs: [{name: ok, type: bool}]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: state\n"
            + "        ops: [get]\n");
        BearIr second = parser.parse(secondaryIr);
        validator.validate(second);
        target.compile(normalizer.normalize(second), tempDir, "status");

        Path runtimeExceptionCanonical = tempDir.resolve(
            "build/generated/bear/src/main/java/com/bear/generated/runtime/BearInvariantViolationException.java"
        );
        Path runtimeExceptionLegacy = tempDir.resolve("build/generated/bear/runtime");
        assertTrue(Files.exists(runtimeExceptionCanonical));
        assertFalse(Files.exists(runtimeExceptionLegacy));
        long exceptionCount;
        try (var stream = Files.walk(tempDir.resolve("build/generated/bear"))) {
            exceptionCount = stream
                .filter(Files::isRegularFile)
                .filter(path -> "BearInvariantViolationException.java".equals(path.getFileName().toString()))
                .count();
        }
        assertEquals(1L, exceptionCount);

        FileTime forced = FileTime.fromMillis(1234L);
        Files.setLastModifiedTime(runtimeExceptionCanonical, forced);
        target.compile(normalizer.normalize(second), tempDir, "status");
        assertEquals(forced, Files.getLastModifiedTime(runtimeExceptionCanonical));
        assertFalse(Files.exists(runtimeExceptionLegacy));
    }

    @Test
    void compileInvariantRuleCanonicalizationIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path irFile = tempDir.resolve("rules.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
            + "block:\n"
            + "  name: Rules\n"
            + "  kind: logic\n"
            + "  contract:\n"
            + "    inputs:\n"
            + "      - name: requestId\n"
            + "        type: string\n"
            + "      - name: tenantId\n"
            + "        type: string\n"
            + "    outputs:\n"
            + "      - name: balance\n"
            + "        type: int\n"
            + "      - name: status\n"
            + "        type: string\n"
            + "      - name: mode\n"
            + "        type: string\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "      - port: ledger\n"
            + "        ops: [apply]\n"
            + "  idempotency:\n"
            + "    keyFromInputs: [tenantId, requestId]\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n"
            + "  invariants:\n"
            + "    - kind: non_negative\n"
            + "      scope: result\n"
            + "      field: balance\n"
            + "    - kind: non_empty\n"
            + "      scope: result\n"
            + "      field: status\n"
            + "    - kind: equals\n"
            + "      scope: result\n"
            + "      field: mode\n"
            + "      params:\n"
            + "        value: A|B\n"
            + "    - kind: one_of\n"
            + "      scope: result\n"
            + "      field: mode\n"
            + "      params:\n"
            + "        values: [A, B#C]\n");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();
        BearIr ir = parser.parse(irFile);
        validator.validate(ir);
        target.compile(normalizer.normalize(ir), tempDir, "rules");

        Path entrypoint = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/rules/Rules.java");
        String entry = Files.readString(entrypoint);
        assertTrue(entry.contains("return \"BEAR_INVARIANT_VIOLATION|\""));
        assertTrue(entry.contains("\"non_negative\""));
        assertTrue(entry.contains("\"non_empty\""));
        assertTrue(entry.contains("\"equals:A\\\\|B\""));
        assertTrue(entry.contains("\"one_of:2|1#A|4#B\\\\#C\""));
    }

    @Test
    void compilePortNameAlreadyEndingWithPortDoesNotDoubleSuffix(@TempDir Path tempDir) throws Exception {
        Path irFile = tempDir.resolve("notify.bear.yaml");
        Files.writeString(irFile, ""
            + "version: v1\n"
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
        target.compile(normalized, tempDir, "notify");

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
        target.compile(normalized, tempDir, "withdraw");

        Path generatedMain = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/withdraw");
        Path stale = generatedMain.resolve("LOCKED.tmp");
        Files.writeString(stale, "stale");

        String previous = System.getProperty("bear.compile.test.lockPathContains");
        try {
            System.setProperty("bear.compile.test.lockPathContains", "LOCKED.tmp");
            boolean threw = false;
            try {
                target.compile(normalized, tempDir, "withdraw");
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

    @Test
    void compileReplaceLockFallsBackToInPlaceRewrite(@TempDir Path tempDir) throws Exception {
        Path repoRoot = TestRepoPaths.repoRoot();
        Path fixture = repoRoot.resolve("spec/fixtures/withdraw.bear.yaml");

        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        JvmTarget target = new JvmTarget();

        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        BearIr normalized = normalizer.normalize(ir);
        target.compile(normalized, tempDir, "withdraw");

        String previousNeedle = System.getProperty("bear.compile.test.lockPathContains");
        String previousAction = System.getProperty("bear.compile.test.lockAction");
        try {
            System.setProperty("bear.compile.test.lockPathContains", "Withdraw.java");
            System.setProperty("bear.compile.test.lockAction", "replace");

            target.compile(normalized, tempDir, "withdraw");
        } finally {
            if (previousNeedle == null) {
                System.clearProperty("bear.compile.test.lockPathContains");
            } else {
                System.setProperty("bear.compile.test.lockPathContains", previousNeedle);
            }
            if (previousAction == null) {
                System.clearProperty("bear.compile.test.lockAction");
            } else {
                System.setProperty("bear.compile.test.lockAction", previousAction);
            }
        }

        Path generatedEntrypoint = tempDir.resolve(
            "build/generated/bear/src/main/java/com/bear/generated/withdraw/Withdraw.java"
        );
        assertTrue(Files.exists(generatedEntrypoint));
        String generated = Files.readString(generatedEntrypoint);
        assertTrue(generated.contains("class Withdraw"));
    }

    private static Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
                .forEach(path -> {
                    String rel = root.relativize(path).toString().replace('\\', '/');
                    if (rel.startsWith(".staging/")) {
                        return;
                    }
                    if (rel.endsWith("IdempotencyTest.java") || rel.endsWith("InvariantNonNegativeTest.java")) {
                        // Legacy golden placeholders are ignored after structural test replacement.
                        return;
                    }
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


