package com.bear.kernel;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrNormalizer;
import com.bear.kernel.ir.BearIrParser;
import com.bear.kernel.ir.BearIrValidator;
import com.bear.kernel.target.jvm.JvmTarget;
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
    void compileWiringManifestUsesV3Contract(@TempDir Path tempDir) throws Exception {
        BearIr ir = parseNormalizedFixture();
        JvmTarget target = new JvmTarget();

        target.compile(ir, tempDir, "withdraw");
        String wiring = Files.readString(tempDir.resolve("build/generated/bear/wiring/withdraw.wiring.json"));
        assertTrue(wiring.contains("\"schemaVersion\":\"v3\""));
        assertTrue(wiring.contains("\"blockRootSourceDir\":\"src/main/java/blocks/withdraw\""));
        assertTrue(wiring.contains("\"governedSourceRoots\":[\"src/main/java/blocks/withdraw\",\"src/main/java/blocks/_shared\"]"));
        assertTrue(wiring.contains("\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\""));
        assertTrue(wiring.contains("\"blockPortBindings\":[]"));

        Path sharedOwner = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/runtime/BearSharedOwner.java");
        assertTrue(Files.exists(sharedOwner));
        String sharedOwnerSource = Files.readString(sharedOwner);
        assertTrue(sharedOwnerSource.contains("public @interface BearSharedOwner"));
    }

    @Test
    void compileSupportsMixedIdempotencyModesAndNoPerOperationLogicTypes(@TempDir Path tempDir) throws Exception {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: Wallet\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Deposit\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: walletId\n"
            + "            type: string\n"
            + "          - name: amountCents\n"
            + "            type: int\n"
            + "          - name: requestId\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: balanceCents\n"
            + "            type: int\n"
            + "      uses:\n"
            + "        allow:\n"
            + "          - port: walletStore\n"
            + "            ops: [getBalance, setBalance]\n"
            + "          - port: idempotency\n"
            + "            ops: [get, put]\n"
            + "      idempotency:\n"
            + "        mode: use\n"
            + "        key: requestId\n"
            + "    - name: GetBalance\n"
            + "      contract:\n"
            + "        inputs:\n"
            + "          - name: walletId\n"
            + "            type: string\n"
            + "        outputs:\n"
            + "          - name: balanceCents\n"
            + "            type: int\n"
            + "      uses:\n"
            + "        allow:\n"
            + "          - port: walletStore\n"
            + "            ops: [getBalance]\n"
            + "      idempotency:\n"
            + "        mode: none\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: walletStore\n"
            + "        ops: [getBalance, setBalance]\n"
            + "      - port: idempotency\n"
            + "        ops: [get, put]\n"
            + "  idempotency:\n"
            + "    store:\n"
            + "      port: idempotency\n"
            + "      getOp: get\n"
            + "      putOp: put\n";
        BearIr ir = parseNormalizedYaml(tempDir, yaml);
        JvmTarget target = new JvmTarget();

        target.compile(ir, tempDir, "wallet");

        Path base = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/wallet");
        assertTrue(Files.exists(base.resolve("WalletLogic.java")));
        assertTrue(Files.exists(base.resolve("Wallet_Deposit.java")));
        assertTrue(Files.exists(base.resolve("Wallet_GetBalance.java")));
        assertTrue(Files.notExists(base.resolve("Wallet_DepositLogic.java")));
        assertTrue(Files.notExists(base.resolve("Wallet_GetBalanceLogic.java")));

        String logic = Files.readString(base.resolve("WalletLogic.java"));
        assertTrue(logic.contains("executeDeposit("));
        assertTrue(logic.contains("executeGetBalance("));

        String depositWrapper = Files.readString(base.resolve("Wallet_Deposit.java"));
        assertTrue(depositWrapper.contains("computeIdempotencyKey("));
        String getBalanceWrapper = Files.readString(base.resolve("Wallet_GetBalance.java"));
        assertTrue(!getBalanceWrapper.contains("computeIdempotencyKey("));
    }
    @Test
    void blockPortClientUsesSortedConstructorOrderAndPinnedOpErrors(@TempDir Path tempDir) throws Exception {
        String yaml = ""
            + "version: v1\n"
            + "block:\n"
            + "  name: Account\n"
            + "  kind: logic\n"
            + "  operations:\n"
            + "    - name: Deposit\n"
            + "      contract:\n"
            + "        inputs: [{name: accountId, type: string}]\n"
            + "        outputs: [{name: balanceCents, type: int}]\n"
            + "      uses:\n"
            + "        allow:\n"
            + "          - port: transactionLog\n"
            + "            kind: block\n"
            + "            targetOps: [AppendTransaction]\n"
            + "  effects:\n"
            + "    allow:\n"
            + "      - port: transactionLog\n"
            + "        kind: block\n"
            + "        targetBlock: transaction-log\n"
            + "        targetOps: [GetTransactions, AppendTransaction]\n";

        BearIr ir = parseNormalizedYaml(tempDir, yaml);
        JvmTarget target = new JvmTarget();
        target.compile(ir, tempDir, "account");

        Path client = tempDir.resolve("build/generated/bear/src/main/java/com/bear/generated/account/Account_TransactionLogBlockClient.java");
        String source = Files.readString(client);

        assertTrue(source.contains("public Account_TransactionLogBlockClient(Object wrapperAppendTransaction, Object wrapperGetTransactions)"));
        assertTrue(source.contains("BLOCK_PORT_MAP_MISSING_FIELD field=op"));
        assertTrue(source.contains("BLOCK_PORT_MAP_UNKNOWN_OP op="));
        assertTrue(source.contains("if (op == null || op.isBlank())"));
        assertTrue(source.contains("case \"AppendTransaction\":"));
        assertTrue(source.contains("case \"GetTransactions\":"));
    }
    private static BearIr parseNormalizedFixture() throws Exception {
        Path fixture = TestRepoPaths.repoRoot().resolve("bear-ir/fixtures/withdraw.bear.yaml");
        BearIrParser parser = new BearIrParser();
        BearIrValidator validator = new BearIrValidator();
        BearIrNormalizer normalizer = new BearIrNormalizer();
        BearIr ir = parser.parse(fixture);
        validator.validate(ir);
        return normalizer.normalize(ir);
    }

    private static BearIr parseNormalizedYaml(Path tempDir, String yaml) throws Exception {
        Path fixture = tempDir.resolve("input.bear.yaml");
        Files.writeString(fixture, yaml);
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







