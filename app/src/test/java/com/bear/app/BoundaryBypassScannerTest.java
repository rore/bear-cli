package com.bear.app;

import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryBypassScannerTest {
    @Test
    void firstDirectImplUsageTokenDetectsImport() {
        String token = BoundaryBypassScanner.firstDirectImplUsageToken(
            "import blocks.withdraw.impl.WithdrawImpl;\nclass X {}"
        );
        assertEquals("import blocks.withdraw.impl.WithdrawImpl;", token);
    }

    @Test
    void firstTopLevelNullPortWiringTokenDetectsNullConstructorArg() {
        String token = BoundaryBypassScanner.firstTopLevelNullPortWiringToken(
            "class X { void m() { new Withdraw(logic, null, other); } }",
            Set.of("com.bear.generated.withdraw.Withdraw"),
            Map.of("Withdraw", 1)
        );
        assertEquals("new Withdraw(..., null, ...)", token);
    }

    @Test
    void stripJavaCommentsStringsAndCharsRemovesFalsePositives() {
        String sanitized = BoundaryBypassScanner.stripJavaCommentsStringsAndChars(
            "// new WithdrawImpl()\nString s = \"new WithdrawImpl()\";\nchar c='x';\n"
        );
        assertTrue(!sanitized.contains("WithdrawImpl"));
    }

    @Test
    void scanBoundaryBypassReportsMissingGovernedImplSource(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "class App {}", StandardCharsets.UTF_8);

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));

        assertEquals(1, findings.size());
        BoundaryBypassFinding first = findings.get(0);
        assertEquals("EFFECTS_BYPASS", first.rule());
        assertNotNull(first.path());
        assertTrue(first.detail().contains("missing governed impl source"));
    }

    @Test
    void firstReflectiveImplUsageTokenDetectsClassForNameLiteral() {
        String token = BoundaryBypassScanner.firstReflectiveImplUsageToken(
            "class X { void m(){ Class.forName(\"blocks.withdraw.impl.WithdrawImpl\"); } }"
        );
        assertEquals("Class.forName(\"blocks.withdraw.impl.WithdrawImpl\")", token);
    }

    @Test
    void firstReflectionClassloadingTokenDetectsClassLoadingApis() {
        assertEquals("Class.forName(...)", BoundaryBypassScanner.firstReflectionClassloadingToken("Class.forName(name);"));
        assertEquals("loadClass(...)", BoundaryBypassScanner.firstReflectionClassloadingToken("loader.loadClass(name);"));
    }

    @Test
    void scanBoundaryBypassFlagsImplPlaceholderStub(@TempDir Path tempDir) throws Exception {
        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(
            impl,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request, Object ledgerPort) {\n"
                + "    // TODO: replace this entire method body with business logic.\n"
                + "    // Do not append logic below this placeholder return.\n"
                + "    // BEAR:PORT_USED ledgerPort\n"
                + "    return new WithdrawResult(0);\n"
                + "  }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f -> "IMPL_PLACEHOLDER".equals(f.rule())));
    }

    @Test
    void reflectionClassloadingAllowlistSkipsViolationForAllowlistedPath(@TempDir Path tempDir) throws Exception {
        Path app = tempDir.resolve("src/main/java/com/example/App.java");
        Files.createDirectories(app.getParent());
        Files.writeString(
            app,
            "package com.example;\n"
                + "public final class App {\n"
                + "  void run(String name) throws Exception { Class.forName(name); }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
        writeWorkingWithdrawImpl(tempDir);

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(
            tempDir,
            List.of(baseManifest()),
            Set.of("src/main/java/com/example/App.java")
        );
        assertTrue(findings.stream().noneMatch(f ->
            "DIRECT_IMPL_USAGE".equals(f.rule()) && "src/main/java/com/example/App.java".equals(f.path())));
    }

    @Test
    void scanBoundaryBypassFlagsGovernedServiceBinding(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        Path descriptor = tempDir.resolve("src/main/resources/META-INF/services/com.bear.generated.withdraw.WithdrawLogic");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(
            descriptor,
            "# comment\n"
                + "blocks.withdraw.impl.WithdrawImpl  # trailing\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "DIRECT_IMPL_USAGE".equals(f.rule())
                && "src/main/resources/META-INF/services/com.bear.generated.withdraw.WithdrawLogic".equals(f.path())
                && f.detail().contains("KIND=IMPL_SERVICE_BINDING: com.bear.generated.withdraw.WithdrawLogic -> blocks.withdraw.impl.WithdrawImpl")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsGovernedModuleInfoBinding(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        Path moduleInfo = tempDir.resolve("src/main/java/module-info.java");
        Files.createDirectories(moduleInfo.getParent());
        Files.writeString(
            moduleInfo,
            "module demo {\n"
                + "  provides com.bear.generated.withdraw.WithdrawLogic\n"
                + "      with blocks.withdraw.impl.WithdrawImpl,\n"
                + "           com.example.Other;\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "DIRECT_IMPL_USAGE".equals(f.rule())
                && "src/main/java/module-info.java".equals(f.path())
                && f.detail().contains("KIND=IMPL_MODULE_BINDING: com.bear.generated.withdraw.WithdrawLogic -> blocks.withdraw.impl.WithdrawImpl")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsExternalStaticFqcnCallFromImpl(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    return com.example.domain.WalletDomain.apply();\n"
                + "  }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/com/example/domain/WalletDomain.java",
            "package com.example.domain;\npublic final class WalletDomain { static Object apply() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().anyMatch(f ->
            "IMPL_CONTAINMENT_BYPASS".equals(f.rule())
                && f.detail().equals("KIND=IMPL_EXTERNAL_CALL: com.example.domain.WalletDomain")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsExternalImportedTypeCallFromImpl(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "import com.example.domain.WalletDomain;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    return WalletDomain.apply();\n"
                + "  }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/com/example/domain/WalletDomain.java",
            "package com.example.domain;\npublic final class WalletDomain { static Object apply() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().anyMatch(f ->
            "IMPL_CONTAINMENT_BYPASS".equals(f.rule())
                && f.detail().equals("KIND=IMPL_EXTERNAL_CALL: com.example.domain.WalletDomain")
        ));
    }

    @Test
    void scanBoundaryBypassAllowsInRootHelperCall(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "import blocks.withdraw.Helper;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    return Helper.apply();\n"
                + "  }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/withdraw/Helper.java",
            "package blocks.withdraw;\npublic final class Helper { public static Object apply() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().noneMatch(f -> "IMPL_CONTAINMENT_BYPASS".equals(f.rule())));
    }

    @Test
    void scanBoundaryBypassAllowsSharedRootHelperCallWhenConfigured(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "import blocks._shared.pure.SharedHelper;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    return SharedHelper.apply();\n"
                + "  }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/SharedHelper.java",
            "package blocks._shared.pure;\npublic final class SharedHelper { public static Object apply() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithSharedRoot()));
        assertTrue(findings.stream().noneMatch(f -> "IMPL_CONTAINMENT_BYPASS".equals(f.rule())));
    }

    @Test
    void scanBoundaryBypassDoesNotFailWhenResolvedSourceMissing(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    return com.example.missing.WalletDomain.apply();\n"
                + "  }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().noneMatch(f -> "IMPL_CONTAINMENT_BYPASS".equals(f.rule())));
    }

    @Test
    void scanBoundaryBypassAllowsJavaAndJavaxNamespaceTargets(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    java.util.Objects.requireNonNull(request);\n"
                + "    return javax.crypto.Cipher.getInstance(\"AES\");\n"
                + "  }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().noneMatch(f -> "IMPL_CONTAINMENT_BYPASS".equals(f.rule())));
    }

    @Test
    void scanBoundaryBypassOnlyScansExecuteMethodBodyForContainment(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    return request;\n"
                + "  }\n"
                + "  Object helper() {\n"
                + "    return com.example.domain.WalletDomain.apply();\n"
                + "  }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/com/example/domain/WalletDomain.java",
            "package com.example.domain;\npublic final class WalletDomain { public static Object apply() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().noneMatch(f -> "IMPL_CONTAINMENT_BYPASS".equals(f.rule())));
    }

    @Test
    void scanBoundaryBypassSortsContainmentFindingsDeterministically(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) {\n"
                + "    com.zeta.External.apply();\n"
                + "    com.alpha.External.apply();\n"
                + "    return null;\n"
                + "  }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/com/alpha/External.java",
            "package com.alpha;\npublic final class External { public static Object apply() { return null; } }\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/com/zeta/External.java",
            "package com.zeta;\npublic final class External { public static Object apply() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        List<BoundaryBypassFinding> containment = findings.stream()
            .filter(f -> "IMPL_CONTAINMENT_BYPASS".equals(f.rule()))
            .toList();
        assertEquals(2, containment.size());
        assertEquals("KIND=IMPL_EXTERNAL_CALL: com.alpha.External", containment.get(0).detail());
        assertEquals("KIND=IMPL_EXTERNAL_CALL: com.zeta.External", containment.get(1).detail());
    }

    @Test
    void scanBoundaryBypassUsesLogicRequiredPortsOnlyWithoutFallback(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request, Object ledgerPort) {\n"
                + "    return null;\n"
                + "  }\n"
                + "}\n"
        );

        WiringManifest manifest = new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "src/main/java/blocks/withdraw",
            List.of("src/main/java/blocks/withdraw"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));
        assertTrue(findings.stream().noneMatch(f ->
            "EFFECTS_BYPASS".equals(f.rule()) && f.detail().contains("missing required effect port usage: ledgerPort")
        ));
    }

    @Test
    void scanBoundaryBypassDedupesMultiBlockRuleWhenOutsideGovernedRootAlreadyFails(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/MegaAdapter.java",
            "package com.acme;\n"
                + "public final class MegaAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/deposit/impl/DepositImpl.java",
            "package blocks.deposit.impl;\npublic final class DepositImpl { Object execute() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(
            tempDir,
            List.of(withdrawManifestWithSharedRoot(), depositManifestWithSharedRoot())
        );

        long outsideCount = findings.stream()
            .filter(f -> "PORT_IMPL_OUTSIDE_GOVERNED_ROOT".equals(f.rule()))
            .count();
        assertEquals(2L, outsideCount);
        assertTrue(findings.stream().noneMatch(f -> "MULTI_BLOCK_PORT_IMPL_FORBIDDEN".equals(f.rule())));
    }

    @Test
    void scanBoundaryBypassFlagsSharedPureMutableStaticField(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/MutableHolder.java",
            "package blocks._shared.pure;\n"
                + "public final class MutableHolder {\n"
                + "  static int counter = 0;\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "SHARED_PURITY_VIOLATION".equals(f.rule()) && f.path().endsWith("MutableHolder.java")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsSharedPureSynchronizedUsage(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/SyncHelper.java",
            "package blocks._shared.pure;\n"
                + "public final class SyncHelper {\n"
                + "  static synchronized int value() { return 1; }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "SHARED_PURITY_VIOLATION".equals(f.rule())
                && f.detail().contains("synchronized usage is forbidden")
        ));
    }

    @Test
    void scanBoundaryBypassAllowsSharedPurePrimitiveConstant(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/Constants.java",
            "package blocks._shared.pure;\n"
                + "public final class Constants {\n"
                + "  static final int MAX = 5;\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().noneMatch(f ->
            "SHARED_PURITY_VIOLATION".equals(f.rule()) && f.path().endsWith("Constants.java")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsSharedPureStaticFinalNewWhenNotAllowlisted(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/Factory.java",
            "package blocks._shared.pure;\n"
                + "public final class Factory {\n"
                + "  static final Object HOLDER = new Object();\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "SHARED_PURITY_VIOLATION".equals(f.rule())
                && f.detail().contains("static final `new` initializer is forbidden")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsImplStaticMutableState(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  static int calls = 0;\n"
                + "  Object execute(Object request) { return null; }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().anyMatch(f ->
            "IMPL_PURITY_VIOLATION".equals(f.rule())
                && f.detail().contains("mutable static field is forbidden")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsImplSynchronizedUsage(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  synchronized Object execute(Object request) { return null; }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().anyMatch(f ->
            "IMPL_PURITY_VIOLATION".equals(f.rule())
                && f.detail().contains("synchronized usage is forbidden")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsImplStateDependency(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "import blocks._shared.state.SessionStore;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) { return SessionStore.fetch(); }\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/SessionStore.java",
            "package blocks._shared.state;\npublic final class SessionStore { public static Object fetch() { return null; } }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().anyMatch(f ->
            "IMPL_STATE_DEPENDENCY_BYPASS".equals(f.rule())
                && f.detail().contains("blocks._shared.state")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsScopedImportPolicyInImpl(@TempDir Path tempDir) throws Exception {
        writeContainmentImpl(
            tempDir,
            "package blocks.withdraw.impl;\n"
                + "import java.net.URL;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request) throws Exception { return new URL(\"https://example.com\"); }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(withdrawManifestWithoutRequiredPorts()));
        assertTrue(findings.stream().anyMatch(f ->
            "SCOPED_IMPORT_POLICY_BYPASS".equals(f.rule())
                && f.detail().contains("java.net.")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsScopedImportPolicyInSharedPure(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/NetworkHelper.java",
            "package blocks._shared.pure;\n"
                + "import java.net.URI;\n"
                + "public final class NetworkHelper { static final String X = URI.create(\"https://example.com\").toString(); }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "SCOPED_IMPORT_POLICY_BYPASS".equals(f.rule())
                && f.path().endsWith("NetworkHelper.java")
        ));
    }


    @Test
    void scanBoundaryBypassDoesNotEvaluateSharedStateAgainstPurityOrScopedImportRules(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/StateStore.java",
            "package blocks._shared.state;\n"
                + "import java.net.URI;\n"
                + "public final class StateStore {\n"
                + "  synchronized void updateBalance(String walletId, int balanceCents) {\n"
                + "    URI.create(\"https://example.com\");\n"
                + "  }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().noneMatch(f ->
            ("SHARED_PURITY_VIOLATION".equals(f.rule()) || "SCOPED_IMPORT_POLICY_BYPASS".equals(f.rule()))
                && f.path().endsWith("StateStore.java")
        ));
    }

    @Test
    void scanBoundaryBypassAllowsConcurrentTokenInSharedPureWhenOtherwisePure(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/pure/ConcurrentMention.java",
            "package blocks._shared.pure;\n"
                + "import java.util.concurrent.atomic.AtomicInteger;\n"
                + "public final class ConcurrentMention { static final int X = 1; }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().noneMatch(f ->
            "SCOPED_IMPORT_POLICY_BYPASS".equals(f.rule()) && f.path().endsWith("ConcurrentMention.java")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsSharedLayoutViolationOutsidePureOrState(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/LegacyHelper.java",
            "package blocks._shared;\npublic final class LegacyHelper { static final int X = 1; }\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "SHARED_LAYOUT_POLICY_VIOLATION".equals(f.rule()) && f.path().endsWith("LegacyHelper.java")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsStateStoreNoopUpdateInSharedState(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/WalletStateStore.java",
            "package blocks._shared.state;\n"
                + "public final class WalletStateStore {\n"
                + "  void updateBalance(String walletId, int balanceCents) {\n"
                + "    Object wallet = null;\n"
                + "    if (wallet == null) { return; }\n"
                + "  }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "STATE_STORE_NOOP_UPDATE".equals(f.rule())
                && f.path().endsWith("WalletStateStore.java")
                && f.detail().startsWith("KIND=STATE_STORE_NOOP_UPDATE|PATTERN=EARLY_RETURN_NOOP|method=updateBalance|")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsStateStoreNullGuardNoopPattern(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/WalletStateStore.java",
            "package blocks._shared.state;\n"
                + "public final class WalletStateStore {\n"
                + "  void updateBalance(String walletId, int balanceCents) {\n"
                + "    Wallet wallet = wallets.get(walletId);\n"
                + "    if (wallet != null) { wallet.setBalanceCents(balanceCents); }\n"
                + "  }\n"
                + "  private final Wallets wallets = new Wallets();\n"
                + "  static final class Wallet { void setBalanceCents(int value) {} }\n"
                + "  static final class Wallets { Wallet get(String walletId) { return null; } }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "STATE_STORE_NOOP_UPDATE".equals(f.rule())
                && f.path().endsWith("WalletStateStore.java")
                && f.detail().startsWith("KIND=STATE_STORE_NOOP_UPDATE|PATTERN=NULL_GUARD_NOOP|method=updateBalance|")
        ));
    }

    @Test
    void scanBoundaryBypassDoesNotFlagStateStoreWhenNullPathThrows(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/WalletStateStore.java",
            "package blocks._shared.state;\n"
                + "public final class WalletStateStore {\n"
                + "  void updateBalance(String walletId, int balanceCents) {\n"
                + "    Wallet wallet = wallets.get(walletId);\n"
                + "    if (wallet == null) { throw new IllegalStateException(\"missing\"); }\n"
                + "    wallet.setBalanceCents(balanceCents);\n"
                + "  }\n"
                + "  private final Wallets wallets = new Wallets();\n"
                + "  static final class Wallet { void setBalanceCents(int value) {} }\n"
                + "  static final class Wallets { Wallet get(String walletId) { return null; } }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().noneMatch(f ->
            "STATE_STORE_NOOP_UPDATE".equals(f.rule()) && f.path().endsWith("WalletStateStore.java")
        ));
    }

    @Test
    void scanBoundaryBypassDoesNotFlagStateStoreWhenElseSignalsNotFound(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/WalletStateStore.java",
            "package blocks._shared.state;\n"
                + "public final class WalletStateStore {\n"
                + "  void updateBalance(String walletId, int balanceCents) {\n"
                + "    Wallet wallet = wallets.get(walletId);\n"
                + "    if (wallet != null) {\n"
                + "      wallet.setBalanceCents(balanceCents);\n"
                + "    } else {\n"
                + "      throw new IllegalStateException(\"missing\");\n"
                + "    }\n"
                + "  }\n"
                + "  private final Wallets wallets = new Wallets();\n"
                + "  static final class Wallet { void setBalanceCents(int value) {} }\n"
                + "  static final class Wallets { Wallet get(String walletId) { return null; } }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().noneMatch(f ->
            "STATE_STORE_NOOP_UPDATE".equals(f.rule()) && f.path().endsWith("WalletStateStore.java")
        ));
    }

    @Test
    void scanBoundaryBypassDoesNotFlagStateStoreWhenBooleanFailureReturnSignalsNotFound(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/state/WalletStateStore.java",
            "package blocks._shared.state;\n"
                + "public final class WalletStateStore {\n"
                + "  boolean updateBalance(String walletId, int balanceCents) {\n"
                + "    Wallet wallet = wallets.get(walletId);\n"
                + "    if (wallet != null) {\n"
                + "      wallet.setBalanceCents(balanceCents);\n"
                + "      return true;\n"
                + "    }\n"
                + "    return false;\n"
                + "  }\n"
                + "  private final Wallets wallets = new Wallets();\n"
                + "  static final class Wallet { void setBalanceCents(int value) {} }\n"
                + "  static final class Wallets { Wallet get(String walletId) { return null; } }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().noneMatch(f ->
            "STATE_STORE_NOOP_UPDATE".equals(f.rule()) && f.path().endsWith("WalletStateStore.java")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsAdapterStateStoreOpMisuseForUpdateMethod(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/withdraw/adapter/WithdrawPorts.java",
            "package blocks.withdraw.adapter;\n"
                + "public final class WithdrawPorts {\n"
                + "  private final State state = new State();\n"
                + "  void updateBalance(String walletId, int balanceCents) {\n"
                + "    state.createWallet(walletId, \"owner\", \"ACTIVE\", balanceCents);\n"
                + "  }\n"
                + "  static final class State {\n"
                + "    void createWallet(String walletId, String ownerId, String status, int balanceCents) {}\n"
                + "  }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "STATE_STORE_OP_MISUSE".equals(f.rule())
                && f.path().endsWith("WithdrawPorts.java")
                && f.detail().contains("update-path signals")
        ));
    }

    @Test
    void scanBoundaryBypassFlagsAdapterStateStoreOpMisuseForRenameDodge(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/withdraw/adapter/WalletPorts.java",
            "package blocks.withdraw.adapter;\n"
                + "public final class WalletPorts {\n"
                + "  private final State state = new State();\n"
                + "  void put(String walletId, String ownerId, String status, String balanceCents) {\n"
                + "    int parsed = Integer.parseInt(balanceCents);\n"
                + "    state.createWallet(walletId, ownerId, status, parsed);\n"
                + "  }\n"
                + "  static final class State {\n"
                + "    void createWallet(String walletId, String ownerId, String status, int balanceCents) {}\n"
                + "  }\n"
                + "}\n"
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(baseManifest()));
        assertTrue(findings.stream().anyMatch(f ->
            "STATE_STORE_OP_MISUSE".equals(f.rule())
                && f.path().endsWith("WalletPorts.java")
        ));
    }

    private static void writeWorkingWithdrawImpl(Path tempDir) throws Exception {
        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(
            impl,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  Object execute(Object request, Object ledgerPort) {\n"
                + "    return helper(ledgerPort);\n"
                + "  }\n"
                + "  Object helper(Object value) { return null; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
    }

    private static void writeContainmentImpl(Path tempDir, String source) throws Exception {
        writeJavaFile(tempDir, "src/main/java/blocks/withdraw/impl/WithdrawImpl.java", source);
    }

    private static void writeJavaFile(Path tempDir, String relPath, String source) throws Exception {
        Path file = tempDir.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static WiringManifest baseManifest() {
        return new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "src/main/java/blocks/withdraw",
            List.of("src/main/java/blocks/withdraw"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );
    }

    private static WiringManifest withdrawManifestWithoutRequiredPorts() {
        return new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "src/main/java/blocks/withdraw",
            List.of("src/main/java/blocks/withdraw"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private static WiringManifest withdrawManifestWithSharedRoot() {
        return new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "src/main/java/blocks/withdraw",
            List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private static WiringManifest depositManifestWithSharedRoot() {
        return new WiringManifest(
            "v2",
            "deposit",
            "com.bear.generated.deposit.Deposit",
            "com.bear.generated.deposit.DepositLogic",
            "blocks.deposit.impl.DepositImpl",
            "src/main/java/blocks/deposit/impl/DepositImpl.java",
            "src/main/java/blocks/deposit",
            List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of(),
            List.of()
        );
    }
}

