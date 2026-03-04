package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedReflectionDispatchScannerTest {
    @Test
    void detectsInvokeWithoutExplicitTargetTokenInSourceOwnedFile(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        writeJava(
            projectRoot,
            "src/main/java/blocks/account/impl/Bypass.java",
            "package blocks.account.impl;\n"
                + "public final class Bypass {\n"
                + "  void run(Object any) { any.invoke(); }\n"
                + "}\n"
        );

        List<UndeclaredReachFinding> findings = GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(
            projectRoot,
            List.of(accountManifest())
        );

        assertEquals(1, findings.size());
        assertEquals("src/main/java/blocks/account/impl/Bypass.java", findings.get(0).path());
        assertEquals("REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=.invoke(", findings.get(0).surface());
    }

    @Test
    void detectsMethodHandlesAndLambdaMetafactoryTokens(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        writeJava(
            projectRoot,
            "src/main/java/blocks/account/impl/Handles.java",
            "package blocks.account.impl;\n"
                + "public final class Handles {\n"
                + "  void run() { MethodHandles.lookup(); }\n"
                + "}\n"
        );
        writeJava(
            projectRoot,
            "src/main/java/blocks/account/impl/LambdaMeta.java",
            "package blocks.account.impl;\n"
                + "public final class LambdaMeta {\n"
                + "  void run() { LambdaMetafactory.metafactory(null, null, null, null, null, null); }\n"
                + "}\n"
        );

        List<UndeclaredReachFinding> findings = GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(
            projectRoot,
            List.of(accountManifest())
        );

        assertTrue(findings.stream().anyMatch(f ->
            "src/main/java/blocks/account/impl/Handles.java".equals(f.path())
                && "REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=MethodHandles.".equals(f.surface())
        ));
        assertTrue(findings.stream().anyMatch(f ->
            "src/main/java/blocks/account/impl/LambdaMeta.java".equals(f.path())
                && "REACH_HYGIENE: KIND=REFLECTION_DISPATCH token=LambdaMetafactory.".equals(f.surface())
        ));
    }

    @Test
    void ignoresCommentsAndStrings(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        writeJava(
            projectRoot,
            "src/main/java/blocks/account/impl/Safe.java",
            "package blocks.account.impl;\n"
                + "public final class Safe {\n"
                + "  // any.invoke();\n"
                + "  String s = \"MethodHandles.lookup()\";\n"
                + "}\n"
        );

        List<UndeclaredReachFinding> findings = GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(
            projectRoot,
            List.of(accountManifest())
        );

        assertTrue(findings.isEmpty());
    }

    @Test
    void ignoresUnownedFilesOutsideGovernedRootUnion(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        writeJava(
            projectRoot,
            "src/main/java/blocks/other/impl/Bypass.java",
            "package blocks.other.impl;\n"
                + "public final class Bypass {\n"
                + "  void run(Object any) { any.invoke(); }\n"
                + "}\n"
        );

        List<UndeclaredReachFinding> findings = GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(
            projectRoot,
            List.of(accountManifest())
        );

        assertTrue(findings.isEmpty());
    }

    @Test
    void sharedConcreteGeneratedPortImplementorIsScanned(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        writeJava(
            projectRoot,
            "src/main/java/blocks/_shared/state/SharedPortImpl.java",
            "package blocks._shared.state;\n"
                + "import com.bear.generated.account.TransactionLogPort;\n"
                + "import com.bear.generated.account.BearValue;\n"
                + "public final class SharedPortImpl implements TransactionLogPort {\n"
                + "  @Override public BearValue call(BearValue input) {\n"
                + "    input.invoke();\n"
                + "    return input;\n"
                + "  }\n"
                + "}\n"
        );

        List<UndeclaredReachFinding> findings = GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(
            projectRoot,
            List.of(accountManifest())
        );

        assertTrue(findings.stream().anyMatch(f ->
            "src/main/java/blocks/_shared/state/SharedPortImpl.java".equals(f.path())
                && f.surface().contains("token=.invoke(")
        ));
    }

    @Test
    void sharedInterfaceAbstractAndNonPortAreIgnored(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("repo");
        writeJava(
            projectRoot,
            "src/main/java/blocks/_shared/state/Helper.java",
            "package blocks._shared.state;\n"
                + "public interface Helper {\n"
                + "  default void run(Object any) { any.invoke(); }\n"
                + "}\n"
        );
        writeJava(
            projectRoot,
            "src/main/java/blocks/_shared/state/AbstractHelper.java",
            "package blocks._shared.state;\n"
                + "public abstract class AbstractHelper {\n"
                + "  void run(Object any) { any.invoke(); }\n"
                + "}\n"
        );
        writeJava(
            projectRoot,
            "src/main/java/blocks/_shared/state/PlainClass.java",
            "package blocks._shared.state;\n"
                + "public final class PlainClass {\n"
                + "  void run(Object any) { any.invoke(); }\n"
                + "}\n"
        );

        List<UndeclaredReachFinding> findings = GovernedReflectionDispatchScanner.scanForbiddenReflectionDispatch(
            projectRoot,
            List.of(accountManifest())
        );

        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("Helper.java")));
        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("AbstractHelper.java")));
        assertFalse(findings.stream().anyMatch(f -> f.path().endsWith("PlainClass.java")));
    }

    private static void writeJava(Path projectRoot, String relPath, String source) throws Exception {
        Path file = projectRoot.resolve(relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static WiringManifest accountManifest() {
        return new WiringManifest(
            "v3",
            "account",
            "com.bear.generated.account.Account",
            "com.bear.generated.account.AccountLogic",
            "blocks.account.impl.AccountImpl",
            "src/main/java/blocks/account/impl/AccountImpl.java",
            "src/main/java/blocks/account",
            List.of("src/main/java/blocks/account", "src/main/java/blocks/_shared"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }
}
