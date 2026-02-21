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
            List.of()
        );

        List<BoundaryBypassFinding> findings = BoundaryBypassScanner.scanBoundaryBypass(tempDir, List.of(manifest));

        assertEquals(1, findings.size());
        BoundaryBypassFinding first = findings.get(0);
        assertEquals("EFFECTS_BYPASS", first.rule());
        assertNotNull(first.path());
        assertTrue(first.detail().contains("missing governed impl source"));
    }
}
