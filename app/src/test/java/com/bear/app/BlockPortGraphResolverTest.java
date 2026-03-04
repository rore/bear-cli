package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockPortGraphResolverTest {
    @Test
    void inboundTargetWrapperSetIsDeterministic(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("spec"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "version: v0\n"
                + "blocks:\n"
                + "  - name: account\n"
                + "    ir: spec/account.bear.yaml\n"
                + "    projectRoot: .\n"
                + "  - name: transaction-log\n"
                + "    ir: spec/transaction-log.bear.yaml\n"
                + "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );

        Files.writeString(repoRoot.resolve("spec/account.bear.yaml"), accountIr(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("spec/transaction-log.bear.yaml"), txIr(), StandardCharsets.UTF_8);

        BlockPortGraph graph = BlockPortGraphResolver.resolveAndValidate(
            repoRoot,
            repoRoot.resolve("bear.blocks.yaml")
        );
        TreeSet<String> wrappers = BlockPortGraphResolver.inboundTargetWrapperFqcns(graph);

        assertEquals(
            List.of(
                "com.bear.generated.transaction.log.TransactionLog_AppendTransaction",
                "com.bear.generated.transaction.log.TransactionLog_GetTransactions"
            ),
            wrappers.stream().toList()
        );
    }

    @Test
    void cycleDetectionUsesCanonicalLeastRotation(@TempDir Path tempDir) throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("spec"));
        Files.writeString(
            repoRoot.resolve("bear.blocks.yaml"),
            "version: v0\n"
                + "blocks:\n"
                + "  - name: account\n"
                + "    ir: spec/account.bear.yaml\n"
                + "    projectRoot: .\n"
                + "  - name: transaction-log\n"
                + "    ir: spec/transaction-log.bear.yaml\n"
                + "    projectRoot: .\n",
            StandardCharsets.UTF_8
        );

        Files.writeString(repoRoot.resolve("spec/account.bear.yaml"), accountIrWithCycle(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve("spec/transaction-log.bear.yaml"), txIrWithCycle(), StandardCharsets.UTF_8);

        BlockIndexValidationException ex = org.junit.jupiter.api.Assertions.assertThrows(
            BlockIndexValidationException.class,
            () -> BlockPortGraphResolver.resolveAndValidate(repoRoot, repoRoot.resolve("bear.blocks.yaml"))
        );

        assertEquals("bear.blocks.yaml", ex.path());
        assertEquals("BLOCK_PORT_CYCLE_DETECTED: cycle=account->transaction-log->account", ex.getMessage());
    }
    private static String accountIr() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                      - name: amountCents
                        type: int
                      - name: requestId
                        type: string
                    outputs:
                      - name: balanceCents
                        type: int
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
                - name: GetBalance
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: balanceCents
                        type: int
                  uses:
                    allow: []
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [GetTransactions, AppendTransaction]
            """;
    }

    private static String accountIrWithCycle() {
        return """
            version: v1
            block:
              name: Account
              kind: logic
              operations:
                - name: Deposit
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: balanceCents
                        type: int
                  uses:
                    allow:
                      - port: transactionLog
                        kind: block
                        targetOps: [AppendTransaction]
              effects:
                allow:
                  - port: transactionLog
                    kind: block
                    targetBlock: transaction-log
                    targetOps: [AppendTransaction]
            """;
    }

    private static String txIrWithCycle() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: seq
                        type: int
                  uses:
                    allow:
                      - port: accountBoundary
                        kind: block
                        targetOps: [Deposit]
              effects:
                allow:
                  - port: accountBoundary
                    kind: block
                    targetBlock: account
                    targetOps: [Deposit]
            """;
    }
    private static String txIr() {
        return """
            version: v1
            block:
              name: TransactionLog
              kind: logic
              operations:
                - name: AppendTransaction
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: seq
                        type: int
                  uses:
                    allow:
                      - port: txStore
                        ops: [append]
                - name: GetTransactions
                  contract:
                    inputs:
                      - name: accountId
                        type: string
                    outputs:
                      - name: transactionsJson
                        type: string
                  uses:
                    allow:
                      - port: txStore
                        ops: [listSince]
              effects:
                allow:
                  - port: txStore
                    ops: [append, listSince]
            """;
    }
}



