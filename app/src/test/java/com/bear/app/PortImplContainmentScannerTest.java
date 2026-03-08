package com.bear.app;

import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortImplContainmentScannerTest {
    @Test
    void detectsFqcnGeneratedPortImplementationOutsideGovernedRoots(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AppPortAdapter.java",
            "package com.acme;\n"
                + "public final class AppPortAdapter implements com.bear.generated.withdraw.LedgerPort {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertEquals(1, findings.size());
        assertEquals("com.bear.generated.withdraw.LedgerPort", findings.get(0).interfaceFqcn());
        assertEquals("com.acme.AppPortAdapter", findings.get(0).implClassFqcn());
        assertEquals("src/main/java/com/acme/AppPortAdapter.java", findings.get(0).path());
    }

    @Test
    void detectsImportedGeneratedPortImplementationOutsideGovernedRoots(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AppPortAdapter.java",
            "package com.acme;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertEquals(1, findings.size());
        assertEquals("com.bear.generated.withdraw.LedgerPort", findings.get(0).interfaceFqcn());
    }

    @Test
    void detectsSamePackageGeneratedPortImplementationOutsideGovernedRoots(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/bear/generated/withdraw/AppPortAdapter.java",
            "package com.bear.generated.withdraw;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertEquals(1, findings.size());
        assertEquals("com.bear.generated.withdraw.LedgerPort", findings.get(0).interfaceFqcn());
    }

    @Test
    void ignoresNonGeneratedOrNonPortInterfaces(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AppPortAdapter.java",
            "package com.acme;\n"
                + "import java.io.Closeable;\n"
                + "public final class AppPortAdapter implements Closeable, com.example.SomeInterface {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertTrue(findings.isEmpty());
    }

    @Test
    void handlesMultipleInterfacesAndSortsDeterministically(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AAdapter.java",
            "package com.acme;\n"
                + "public final class AAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.withdraw.IdempotencyPort {\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/BAdapter.java",
            "package com.acme;\n"
                + "public final class BAdapter implements com.bear.generated.withdraw.LedgerPort {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertEquals(3, findings.size());
        assertEquals("com.bear.generated.withdraw.IdempotencyPort", findings.get(0).interfaceFqcn());
        assertEquals("com.bear.generated.withdraw.LedgerPort", findings.get(1).interfaceFqcn());
        assertEquals("com.bear.generated.withdraw.LedgerPort", findings.get(2).interfaceFqcn());
    }

    @Test
    void allowsImplementationsInsideGovernedRoots(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/AppPortAdapter.java",
            "package blocks._shared;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertTrue(findings.isEmpty());
    }

    @Test
    void ignoresGeneratedPortWhenOwnerManifestMissingFromScope(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AppPortAdapter.java",
            "package com.acme;\n"
                + "import com.bear.generated.missing.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n"
        );

        List<PortImplContainmentFinding> findings = PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertTrue(findings.isEmpty());
    }

    @Test
    void throwsManifestInvalidWhenGeneratedPortOwnerIsAmbiguous(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AppPortAdapter.java",
            "package com.acme;\n"
                + "import com.bear.generated.withdraw.LedgerPort;\n"
                + "public final class AppPortAdapter implements LedgerPort {\n"
                + "}\n"
        );

        ManifestParseException ex = assertThrows(
            ManifestParseException.class,
            () -> PortImplContainmentScanner.scanPortImplOutsideGovernedRoots(
                tempDir,
                List.of(
                    withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                    new WiringManifest(
                        "v2",
                        "withdraw-2",
                        "com.bear.generated.withdraw.Withdraw2",
                        "com.bear.generated.withdraw.Withdraw2Logic",
                        "blocks.withdraw.two.impl.Withdraw2Impl",
                        "src/main/java/blocks/withdraw/two/impl/Withdraw2Impl.java",
                        "src/main/java/blocks/withdraw/two",
                        List.of("src/main/java/blocks/withdraw/two", "src/main/java/blocks/_shared"),
                        List.of("ledgerPort"),
                        List.of("ledgerPort"),
                        List.of("ledgerPort"),
                        List.of(),
                        List.of()
                    )
                )
            )
        );
        assertEquals(PortImplContainmentScanner.AMBIGUOUS_PORT_OWNER_REASON_CODE, ex.reasonCode());
    }

    @Test
    void detectsMultiBlockPortImplWithoutMarker(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/MegaAdapter.java",
            "package blocks._shared;\n"
                + "public final class MegaAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplFinding> findings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertEquals(1, findings.size());
        assertEquals(PortImplContainmentScanner.MULTI_BLOCK_PORT_IMPL_FORBIDDEN_KIND, findings.get(0).kind());
        assertEquals("blocks._shared.MegaAdapter", findings.get(0).implClassFqcn());
        assertEquals("com.bear.generated.deposit,com.bear.generated.withdraw", findings.get(0).generatedPackageCsv());
        assertEquals("src/main/java/blocks/_shared/MegaAdapter.java", findings.get(0).path());
    }

    @Test
    void allowsMultiBlockPortImplWhenMarkerIsWithinWindowInSharedRoot(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/MegaAdapter.java",
            "package blocks._shared;\n"
                + "\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class MegaAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplFinding> findings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertTrue(findings.isEmpty());
    }

    @Test
    void emitsAllowedSignalWhenMarkerIsValidInSharedRoot(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/MegaAdapter.java",
            "package blocks._shared;\n"
                + "\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class MegaAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplAllowedSignal> signals = PortImplContainmentScanner.scanMultiBlockPortImplAllowedSignals(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertEquals(1, signals.size());
        assertEquals("blocks._shared.MegaAdapter", signals.get(0).implClassFqcn());
        assertEquals("com.bear.generated.deposit,com.bear.generated.withdraw", signals.get(0).generatedPackageCsv());
        assertEquals("src/main/java/blocks/_shared/MegaAdapter.java", signals.get(0).path());
    }

    @Test
    void failsMultiBlockPortImplWhenMarkerIsOutsideFiveNonEmptyLines(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/MegaAdapter.java",
            "package blocks._shared;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "// gap-1\n"
                + "// gap-2\n"
                + "// gap-3\n"
                + "// gap-4\n"
                + "// gap-5\n"
                + "// gap-6\n"
                + "public final class MegaAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplFinding> findings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertEquals(1, findings.size());
        assertEquals(PortImplContainmentScanner.MULTI_BLOCK_PORT_IMPL_FORBIDDEN_KIND, findings.get(0).kind());

        List<MultiBlockPortImplAllowedSignal> signals = PortImplContainmentScanner.scanMultiBlockPortImplAllowedSignals(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertTrue(signals.isEmpty());
    }

    @Test
    void failsWhenMarkerIsUsedOutsideSharedRoot(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/com/acme/AppPortAdapter.java",
            "package com.acme;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class AppPortAdapter implements com.bear.generated.withdraw.LedgerPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplFinding> findings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertEquals(1, findings.size());
        assertEquals(PortImplContainmentScanner.MARKER_MISUSED_OUTSIDE_SHARED_KIND, findings.get(0).kind());
        assertEquals("com.acme.AppPortAdapter", findings.get(0).implClassFqcn());
        assertEquals("", findings.get(0).generatedPackageCsv());

        List<MultiBlockPortImplAllowedSignal> signals = PortImplContainmentScanner.scanMultiBlockPortImplAllowedSignals(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertTrue(signals.isEmpty());
    }

    @Test
    void passesSingleBlockPortImplementer(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/SingleAdapter.java",
            "package blocks._shared;\n"
                + "public final class SingleAdapter implements com.bear.generated.withdraw.LedgerPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplFinding> findings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            tempDir,
            List.of(withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")))
        );
        assertTrue(findings.isEmpty());
    }

    @Test
    void multiBlockFindingsAreSortedDeterministically(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/BAdapter.java",
            "package blocks._shared;\n"
                + "public final class BAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/AAdapter.java",
            "package blocks._shared;\n"
                + "public final class AAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplFinding> findings = PortImplContainmentScanner.scanMultiBlockPortImplFindings(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertEquals(2, findings.size());
        assertTrue(findings.get(0).path().endsWith("AAdapter.java"));
        assertTrue(findings.get(1).path().endsWith("BAdapter.java"));
        assertFalse(findings.get(0).implClassFqcn().isBlank());
    }

    @Test
    void allowedSignalsAreSortedDeterministically(@TempDir Path tempDir) throws Exception {
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/BAdapter.java",
            "package blocks._shared;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class BAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );
        writeJavaFile(
            tempDir,
            "src/main/java/blocks/_shared/AAdapter.java",
            "package blocks._shared;\n"
                + "// BEAR:ALLOW_MULTI_BLOCK_PORT_IMPL\n"
                + "public final class AAdapter implements com.bear.generated.withdraw.LedgerPort, com.bear.generated.deposit.DepositPort {\n"
                + "}\n"
        );

        List<MultiBlockPortImplAllowedSignal> signals = PortImplContainmentScanner.scanMultiBlockPortImplAllowedSignals(
            tempDir,
            List.of(
                withdrawManifest(List.of("src/main/java/blocks/withdraw", "src/main/java/blocks/_shared")),
                depositManifest(List.of("src/main/java/blocks/deposit", "src/main/java/blocks/_shared"))
            )
        );
        assertEquals(2, signals.size());
        assertTrue(signals.get(0).path().endsWith("AAdapter.java"));
        assertTrue(signals.get(1).path().endsWith("BAdapter.java"));
    }

    private static void writeJavaFile(Path root, String relPath, String content) throws Exception {
        Path file = root.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static WiringManifest withdrawManifest(List<String> governedRoots) {
        return new WiringManifest(
            "v2",
            "withdraw",
            "com.bear.generated.withdraw.Withdraw",
            "com.bear.generated.withdraw.WithdrawLogic",
            "blocks.withdraw.impl.WithdrawImpl",
            "src/main/java/blocks/withdraw/impl/WithdrawImpl.java",
            "src/main/java/blocks/withdraw",
            governedRoots,
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );
    }

    private static WiringManifest depositManifest(List<String> governedRoots) {
        return new WiringManifest(
            "v2",
            "deposit",
            "com.bear.generated.deposit.Deposit",
            "com.bear.generated.deposit.DepositLogic",
            "blocks.deposit.impl.DepositImpl",
            "src/main/java/blocks/deposit/impl/DepositImpl.java",
            "src/main/java/blocks/deposit",
            governedRoots,
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of("ledgerPort"),
            List.of(),
            List.of()
        );
    }
}

