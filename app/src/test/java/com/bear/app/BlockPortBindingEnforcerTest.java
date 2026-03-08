package com.bear.app;

import com.bear.kernel.target.*;
import com.bear.kernel.target.jvm.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPortBindingEnforcerTest {
    @Test
    void userRootImplementationViolationUsesExactDetail(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path userImpl = projectRoot.resolve("src/main/java/blocks/account/impl/LocalTxPortImpl.java");
        Files.createDirectories(userImpl.getParent());
        Files.writeString(
            userImpl,
            "package blocks.account.impl;\n"
                + "import com.bear.generated.account.TransactionLogPort;\n"
                + "import com.bear.generated.account.BearValue;\n"
                + "public final class LocalTxPortImpl implements TransactionLogPort {\n"
                + "  @Override public BearValue call(BearValue input) { return input; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        BoundaryBypassFinding finding = findings.stream()
            .filter(f -> BlockPortBindingEnforcer.RULE_BLOCK_PORT_IMPL_INVALID.equals(f.rule()))
            .filter(f -> f.path().endsWith("src/main/java/blocks/account/impl/LocalTxPortImpl.java"))
            .findFirst()
            .orElseThrow();
        assertEquals(BlockPortBindingEnforcer.USER_MAIN_IMPL_DETAIL, finding.detail());
    }

    @Test
    void generatedScopeIgnoresStagingButCopiedGeneratedInUserRootStillViolates(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path stagingImpl = projectRoot.resolve("build/generated/bear/.staging/x/src/main/java/com/bear/generated/account/ExtraClient.java");
        Files.createDirectories(stagingImpl.getParent());
        Files.writeString(
            stagingImpl,
            "package com.bear.generated.account;\n"
                + "public final class ExtraClient implements TransactionLogPort {\n"
                + "  @Override public BearValue call(BearValue input) { return input; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        Path copiedGeneratedImpl = projectRoot.resolve("src/main/java/com/bear/generated/account/CopiedClient.java");
        Files.createDirectories(copiedGeneratedImpl.getParent());
        Files.writeString(
            copiedGeneratedImpl,
            "package com.bear.generated.account;\n"
                + "public final class CopiedClient implements TransactionLogPort {\n"
                + "  @Override public BearValue call(BearValue input) { return input; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertFalse(findings.stream().anyMatch(f -> f.path().contains(".staging")));
        assertTrue(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/com/bear/generated/account/CopiedClient.java")));
    }

    @Test
    void appLaneExecuteIsDeniedForInboundTargetWrappers(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path appWiring = projectRoot.resolve("src/main/java/com/demo/Wiring.java");
        Files.createDirectories(appWiring.getParent());
        Files.writeString(
            appWiring,
            "package com.demo;\n"
                + "import com.bear.generated.transaction.log.TransactionLog_AppendTransaction;\n"
                + "public final class Wiring {\n"
                + "  private final TransactionLog_AppendTransaction wrapper;\n"
                + "  public Wiring(TransactionLog_AppendTransaction wrapper) { this.wrapper = wrapper; }\n"
                + "  public void run(Object req) { wrapper.execute((com.bear.generated.transaction.log.TransactionLog_AppendTransactionRequest) req); }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of("com.bear.generated.transaction.log.TransactionLog_AppendTransaction")
        );

        assertTrue(findings.stream().anyMatch(f -> BlockPortBindingEnforcer.RULE_BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN.equals(f.rule())));
    }


    @Test
    void appLaneExecuteIsAllowedWhenWrapperIsNotInInboundSet(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path appWiring = projectRoot.resolve("src/main/java/com/demo/Wiring.java");
        Files.createDirectories(appWiring.getParent());
        Files.writeString(
            appWiring,
            "package com.demo;\n"
                + "import com.bear.generated.transaction.log.TransactionLog_AppendTransaction;\n"
                + "public final class Wiring {\n"
                + "  private final TransactionLog_AppendTransaction wrapper;\n"
                + "  public Wiring(TransactionLog_AppendTransaction wrapper) { this.wrapper = wrapper; }\n"
                + "  public void run(Object req) { wrapper.execute((com.bear.generated.transaction.log.TransactionLog_AppendTransactionRequest) req); }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertFalse(findings.stream().anyMatch(f -> BlockPortBindingEnforcer.RULE_BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN.equals(f.rule())));
    }
    @Test
    void nonAppLaneWrapperExecuteIsForbidden(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path nonAppFile = projectRoot.resolve("src/main/java/blocks/account/impl/Wiring.java");
        Files.createDirectories(nonAppFile.getParent());
        Files.writeString(
            nonAppFile,
            "package blocks.account.impl;\n"
                + "import com.bear.generated.transaction.log.TransactionLog_AppendTransaction;\n"
                + "public final class Wiring {\n"
                + "  private final TransactionLog_AppendTransaction wrapper;\n"
                + "  public Wiring(TransactionLog_AppendTransaction wrapper) { this.wrapper = wrapper; }\n"
                + "  public void run(Object req) { wrapper.execute((com.bear.generated.transaction.log.TransactionLog_AppendTransactionRequest) req); }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertTrue(findings.stream().anyMatch(f -> BlockPortBindingEnforcer.RULE_BLOCK_PORT_REFERENCE_FORBIDDEN.equals(f.rule())));
    }

    @Test
    void targetOwnedFilesAreIgnoredInSourceBlockContext(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path targetOwned = projectRoot.resolve("src/main/java/blocks/transaction/log/impl/Wiring.java");
        Files.createDirectories(targetOwned.getParent());
        Files.writeString(
            targetOwned,
            "package blocks.transaction.log.impl;\n"
                + "import blocks.transaction.log.adapter.DirectAdapter;\n"
                + "public final class Wiring {}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/transaction/log/impl/Wiring.java")));
    }

    @Test
    void unownedFilesOutsideGovernedRootsAreIgnored(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path unowned = projectRoot.resolve("src/main/java/blocks/other/impl/Wiring.java");
        Files.createDirectories(unowned.getParent());
        Files.writeString(
            unowned,
            "package blocks.other.impl;\n"
                + "import blocks.transaction.log.adapter.DirectAdapter;\n"
                + "public final class Wiring {}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/other/impl/Wiring.java")));
    }

    @Test
    void segmentSafeOwnershipDoesNotTreatAccountingAsAccount(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path accountingFile = projectRoot.resolve("src/main/java/blocks/accounting/impl/Wiring.java");
        Files.createDirectories(accountingFile.getParent());
        Files.writeString(
            accountingFile,
            "package blocks.accounting.impl;\n"
                + "import blocks.transaction.log.adapter.DirectAdapter;\n"
                + "public final class Wiring {}\n",
            StandardCharsets.UTF_8
        );

        WiringManifest accountingManifest = new WiringManifest(
            "v3",
            "accounting",
            "com.bear.generated.accounting.Accounting",
            "com.bear.generated.accounting.AccountingLogic",
            "blocks.accounting.impl.AccountingImpl",
            "src/main/java/blocks/accounting/impl/AccountingImpl.java",
            "src/main/java/blocks/accounting",
            List.of("src/main/java/blocks/accounting", "src/main/java/blocks/_shared"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest(), accountingManifest),
            Set.of()
        );

        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/accounting/impl/Wiring.java")));
    }

    @Test
    void equalLengthOwnershipTieBreakIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path sharedLane = projectRoot.resolve("src/main/java/blocks/sharedlane/impl/Wiring.java");
        Files.createDirectories(sharedLane.getParent());
        Files.writeString(
            sharedLane,
            "package blocks.sharedlane.impl;\n"
                + "import blocks.transaction.log.adapter.DirectAdapter;\n"
                + "public final class Wiring {}\n",
            StandardCharsets.UTF_8
        );

        WiringManifest alpha = customBindingManifest("alpha", "transaction-log", "src/main/java/blocks/sharedlane");
        WiringManifest beta = customBindingManifest("beta", "ledger-log", "src/main/java/blocks/sharedlane");

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(alpha, beta),
            Set.of()
        );

        assertTrue(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/sharedlane/impl/Wiring.java")));
    }

    @Test
    void sharedConcreteGeneratedPortImplementorForbiddenReferenceIsFlagged(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path sharedImpl = projectRoot.resolve("src/main/java/blocks/_shared/state/SharedTxPort.java");
        Files.createDirectories(sharedImpl.getParent());
        Files.writeString(
            sharedImpl,
            "package blocks._shared.state;\n"
                + "import blocks.transaction.log.impl.TransactionLogImpl;\n"
                + "import com.bear.generated.account.TransactionLogPort;\n"
                + "import com.bear.generated.account.BearValue;\n"
                + "public final class SharedTxPort implements TransactionLogPort {\n"
                + "  @Override public BearValue call(BearValue input) { return input; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertTrue(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/_shared/state/SharedTxPort.java")
            && BlockPortBindingEnforcer.RULE_BLOCK_PORT_REFERENCE_FORBIDDEN.equals(f.rule())));
    }

    @Test
    void sharedNonPortOrAbstractTypesAreNotFlaggedByNarrowGuard(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        createGeneratedPortAndClient(projectRoot);

        Path iface = projectRoot.resolve("src/main/java/blocks/_shared/state/Helper.java");
        Files.createDirectories(iface.getParent());
        Files.writeString(
            iface,
            "package blocks._shared.state;\n"
                + "import blocks.transaction.log.impl.TransactionLogImpl;\n"
                + "public interface Helper {}\n",
            StandardCharsets.UTF_8
        );

        Path abstractType = projectRoot.resolve("src/main/java/blocks/_shared/state/AbstractHelper.java");
        Files.createDirectories(abstractType.getParent());
        Files.writeString(
            abstractType,
            "package blocks._shared.state;\n"
                + "import blocks.transaction.log.impl.TransactionLogImpl;\n"
                + "public abstract class AbstractHelper {}\n",
            StandardCharsets.UTF_8
        );

        List<BoundaryBypassFinding> findings = BlockPortBindingEnforcer.scan(
            projectRoot,
            List.of(wiringManifest()),
            Set.of()
        );

        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/_shared/state/Helper.java")
            && BlockPortBindingEnforcer.RULE_BLOCK_PORT_REFERENCE_FORBIDDEN.equals(f.rule())));
        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("src/main/java/blocks/_shared/state/AbstractHelper.java")
            && BlockPortBindingEnforcer.RULE_BLOCK_PORT_REFERENCE_FORBIDDEN.equals(f.rule())));
    }
    private static WiringManifest wiringManifest() {
        return new WiringManifest(
            "v3",
            "account",
            "com.bear.generated.account.Account",
            "com.bear.generated.account.AccountLogic",
            "blocks.account.impl.AccountImpl",
            "src/main/java/blocks/account/impl/AccountImpl.java",
            "src/main/java/blocks/account",
            List.of("src/main/java/blocks/account", "src/main/java/blocks/_shared"),
            List.of("transactionLogPort"),
            List.of("transactionLogPort"),
            List.of("transactionLogPort"),
            List.of(),
            List.of(),
            List.of(new BlockPortBinding(
                "transactionLog",
                "transaction-log",
                List.of("AppendTransaction"),
                "com.bear.generated.account.TransactionLogPort",
                "com.bear.generated.account.Account_TransactionLogBlockClient"
            ))
        );
    }


    private static WiringManifest customBindingManifest(String sourceBlock, String targetBlock, String blockRoot) {
        return new WiringManifest(
            "v3",
            sourceBlock,
            "com.bear.generated." + sourceBlock + ".Entrypoint",
            "com.bear.generated." + sourceBlock + ".Logic",
            "blocks." + sourceBlock + ".impl." + sourceBlock + "Impl",
            blockRoot + "/impl/Impl.java",
            blockRoot,
            List.of(blockRoot, "src/main/java/blocks/_shared"),
            List.of("transactionLogPort"),
            List.of("transactionLogPort"),
            List.of("transactionLogPort"),
            List.of(),
            List.of(),
            List.of(new BlockPortBinding(
                "transactionLog",
                targetBlock,
                List.of("AppendTransaction"),
                "com.bear.generated.account.TransactionLogPort",
                "com.bear.generated.account.Account_TransactionLogBlockClient"
            ))
        );
    }
    private static void createGeneratedPortAndClient(Path projectRoot) throws Exception {
        Path generatedPort = projectRoot.resolve("build/generated/bear/src/main/java/com/bear/generated/account/TransactionLogPort.java");
        Files.createDirectories(generatedPort.getParent());
        Files.writeString(
            generatedPort,
            "package com.bear.generated.account;\n"
                + "public interface TransactionLogPort {\n"
                + "  BearValue call(BearValue input);\n"
                + "}\n",
            StandardCharsets.UTF_8
        );

        Path generatedValue = projectRoot.resolve("build/generated/bear/src/main/java/com/bear/generated/account/BearValue.java");
        Files.createDirectories(generatedValue.getParent());
        Files.writeString(
            generatedValue,
            "package com.bear.generated.account;\n"
                + "public final class BearValue {}\n",
            StandardCharsets.UTF_8
        );

        Path generatedClient = projectRoot.resolve("build/generated/bear/src/main/java/com/bear/generated/account/Account_TransactionLogBlockClient.java");
        Files.createDirectories(generatedClient.getParent());
        Files.writeString(
            generatedClient,
            "package com.bear.generated.account;\n"
                + "public final class Account_TransactionLogBlockClient implements TransactionLogPort {\n"
                + "  @Override public BearValue call(BearValue input) { return input; }\n"
                + "}\n",
            StandardCharsets.UTF_8
        );
    }
}






