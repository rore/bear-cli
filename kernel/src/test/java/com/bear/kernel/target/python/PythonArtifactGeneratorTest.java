package com.bear.kernel.target.python;

import com.bear.kernel.ir.BearIr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PythonArtifactGeneratorTest {

    @Test
    void generatesPortsPy(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithPorts();

        generator.generatePorts(ir, tempDir, "user-auth");

        Path portsFile = tempDir.resolve("user_auth_ports.py");
        assertTrue(Files.exists(portsFile));
        String content = Files.readString(portsFile);
        assertTrue(content.contains("class DatabasePort(Protocol):"));
        assertTrue(content.contains("def query(self, input: BearValue) -> BearValue: ..."));
    }

    @Test
    void generatesLogicPy(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        generator.generateLogic(ir, tempDir, "user-auth");

        Path logicFile = tempDir.resolve("user_auth_logic.py");
        String content = Files.readString(logicFile);
        assertTrue(content.contains("@dataclass"));
        assertTrue(content.contains("class UserAuthLoginRequest:"));
        assertTrue(content.contains("class UserAuthLoginResult:"));
        assertTrue(content.contains("class UserAuthLogic(Protocol):"));
    }

    @Test
    void generatesWrapperPy(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        generator.generateWrapper(ir, tempDir, "user-auth");

        Path wrapperFile = tempDir.resolve("user_auth_wrapper.py");
        String content = Files.readString(wrapperFile);
        assertTrue(content.contains("class UserAuth_Login:"));
        assertTrue(content.contains("def execute(self, request:"));
        assertTrue(content.contains("@staticmethod"));
        assertTrue(content.contains("def of("));
    }

    @Test
    void generatesUserImplSkeletonOnce(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        Path implDir = tempDir.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir, "user-auth");

        Path implFile = implDir.resolve("user_auth_impl.py");
        assertTrue(Files.exists(implFile));

        // Second call should not overwrite
        String firstContent = Files.readString(implFile);
        generator.generateUserImplSkeleton(ir, implDir, "user-auth");
        String secondContent = Files.readString(implFile);
        assertEquals(firstContent, secondContent);
    }

    @Test
    void generatesUserImplSkeletonOnlyIfAbsent(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        Path implDir = tempDir.resolve("impl");
        Files.createDirectories(implDir);
        Path implFile = implDir.resolve("user_auth_impl.py");
        Files.writeString(implFile, "# existing content");

        generator.generateUserImplSkeleton(ir, implDir, "user-auth");

        String content = Files.readString(implFile);
        assertEquals("# existing content", content);
    }

    @Test
    void generatedPythonPortsIsSyntacticallyValid(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithPorts();

        generator.generatePorts(ir, tempDir, "user-auth");

        Path portsFile = tempDir.resolve("user_auth_ports.py");
        String content = Files.readString(portsFile);
        
        // Basic syntax validation - check for common Python syntax errors
        assertFalse(content.contains(";;"));
        assertFalse(content.contains("{}"));
        assertTrue(content.contains("Protocol"));
    }

    @Test
    void generatedPythonLogicIsSyntacticallyValid(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        generator.generateLogic(ir, tempDir, "user-auth");

        Path logicFile = tempDir.resolve("user_auth_logic.py");
        String content = Files.readString(logicFile);
        
        // Basic syntax validation
        assertTrue(content.contains("@dataclass"));
        assertTrue(content.contains("Protocol"));
        assertFalse(content.contains(";;"));
    }

    @Test
    void generatedPythonWrapperIsSyntacticallyValid(@TempDir Path tempDir) throws IOException {
        PythonArtifactGenerator generator = new PythonArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        generator.generateWrapper(ir, tempDir, "user-auth");

        Path wrapperFile = tempDir.resolve("user_auth_wrapper.py");
        String content = Files.readString(wrapperFile);
        
        // Basic syntax validation
        assertTrue(content.contains("class "));
        assertTrue(content.contains("def __init__"));
        assertTrue(content.contains("def execute"));
        assertFalse(content.contains(";;"));
    }

    private BearIr createTestIrWithPorts() {
        return new BearIr("1", new BearIr.Block(
            "UserAuth",
            BearIr.BlockKind.LOGIC,
            java.util.List.of(),
            new BearIr.Effects(java.util.List.of(
                new BearIr.EffectPort("database", BearIr.EffectPortKind.EXTERNAL, java.util.List.of("query", "execute"), null, java.util.List.of())
            )),
            null,
            null,
            java.util.List.of()
        ));
    }

    private BearIr createTestIrWithOperations() {
        return new BearIr("1", new BearIr.Block(
            "UserAuth",
            BearIr.BlockKind.LOGIC,
            java.util.List.of(
                new BearIr.Operation(
                    "login",
                    new BearIr.Contract(
                        java.util.List.of(new BearIr.Field("username", BearIr.FieldType.STRING)),
                        java.util.List.of(new BearIr.Field("token", BearIr.FieldType.STRING))
                    ),
                    new BearIr.Effects(java.util.List.of()),
                    null,
                    java.util.List.of()
                )
            ),
            new BearIr.Effects(java.util.List.of()),
            null,
            null,
            java.util.List.of()
        ));
    }
}
