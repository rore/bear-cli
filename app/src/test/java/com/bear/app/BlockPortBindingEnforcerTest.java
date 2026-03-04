package com.bear.app;

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

        Path nonAppFile = projectRoot.resolve("src/main/java/org/demo/Wiring.java");
        Files.createDirectories(nonAppFile.getParent());
        Files.writeString(
            nonAppFile,
            "package org.demo;\n"
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


