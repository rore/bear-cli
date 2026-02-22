package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        Files.writeString(tempDir.resolve("src/main/java/com/example/App.java"), "class App {}");

        WiringManifest manifest = new WiringManifest(
            "v1",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            List.of("ledgerPort"),
            List.of("logic", "ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));

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
    void semanticPortIdentifierBindingUsesManifestIdentifierOnly(@TempDir Path tempDir) throws Exception {
        Path impl = tempDir.resolve("src/main/java/blocks/withdraw/impl/WithdrawImpl.java");
        Files.createDirectories(impl.getParent());
        Files.writeString(
            impl,
            "package blocks.withdraw.impl;\n"
                + "public final class WithdrawImpl {\n"
                + "  public void execute(Object request, Object ledgerPort) {\n"
                + "    ledgerPort.toString();\n"
                + "    Object idempotencyPort = null;\n"
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
            List.of("idempotencyPort", "ledgerPort"),
            List.of("idempotencyPort", "ledgerPort"),
            List.of("ledgerPort"),
            List.of("idempotencyPortParam"),
            List.of("IDEMPOTENCY")
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));
        boolean hasSemanticViolation = findings.stream().anyMatch(f ->
            "EFFECTS_BYPASS".equals(f.rule()) && f.detail().contains("semantic port usage forbidden"));
        assertTrue(!hasSemanticViolation);
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
                + "}\n"
        );

        WiringManifest manifest = new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));
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
                + "}\n"
        );

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
                + "}\n"
        );

        WiringManifest manifest = new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(
            tempDir,
            List.of(manifest),
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
            java.nio.charset.StandardCharsets.UTF_8
        );

        WiringManifest manifest = new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));
        assertTrue(findings.stream().anyMatch(f ->
            "DIRECT_IMPL_USAGE".equals(f.rule())
                && "src/main/resources/META-INF/services/com.bear.generated.withdraw.WithdrawLogic".equals(f.path())
                && f.detail().contains("KIND=IMPL_SERVICE_BINDING: com.bear.generated.withdraw.WithdrawLogic -> blocks.withdraw.impl.WithdrawImpl")
        ));
    }

    @Test
    void scanBoundaryBypassIgnoresNonGovernedServiceBinding(@TempDir Path tempDir) throws Exception {
        writeWorkingWithdrawImpl(tempDir);
        Path descriptor = tempDir.resolve("src/main/resources/META-INF/services/com.example.OtherLogic");
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, "blocks.withdraw.impl.WithdrawImpl\n", java.nio.charset.StandardCharsets.UTF_8);

        WiringManifest manifest = new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));
        assertTrue(findings.stream().noneMatch(f ->
            "DIRECT_IMPL_USAGE".equals(f.rule()) && f.detail().contains("KIND=IMPL_SERVICE_BINDING")
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
            java.nio.charset.StandardCharsets.UTF_8
        );

        WiringManifest manifest = new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));
        assertTrue(findings.stream().anyMatch(f ->
            "DIRECT_IMPL_USAGE".equals(f.rule())
                && "src/main/java/module-info.java".equals(f.path())
                && f.detail().contains("KIND=IMPL_MODULE_BINDING: com.bear.generated.withdraw.WithdrawLogic -> blocks.withdraw.impl.WithdrawImpl")
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
                + "}\n"
        );
    }
}
