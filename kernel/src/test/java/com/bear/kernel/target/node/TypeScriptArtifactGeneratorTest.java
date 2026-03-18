package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptArtifactGeneratorTest {

    @Test
    void generatesPortsTs(@TempDir Path tempDir) throws IOException {
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        BearIr ir = createTestIrWithPorts();

        generator.generatePorts(ir, tempDir, "user-auth");

        Path portsFile = tempDir.resolve("UserAuthPorts.ts");
        assertTrue(Files.exists(portsFile));
        String content = Files.readString(portsFile);
        assertTrue(content.contains("export interface DatabasePort"));
        assertTrue(content.contains("query(input: BearValue): BearValue;"));
    }

    @Test
    void generatesLogicTs(@TempDir Path tempDir) throws IOException {
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        generator.generateLogic(ir, tempDir, "user-auth");

        Path logicFile = tempDir.resolve("UserAuthLogic.ts");
        String content = Files.readString(logicFile);
        // blockName="UserAuth", opName="login" → "UserAuthloginRequest" (opName not capitalized)
        assertTrue(content.contains("export interface UserAuthloginRequest"), "content: " + content);
        assertTrue(content.contains("export interface UserAuthloginResult"));
        assertTrue(content.contains("export interface UserAuthLogic"));
    }

    @Test
    void generatesWrapperTs(@TempDir Path tempDir) throws IOException {
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        generator.generateWrapper(ir, tempDir, "user-auth");

        Path wrapperFile = tempDir.resolve("UserAuthWrapper.ts");
        String content = Files.readString(wrapperFile);
        // blockName="UserAuth", opName="login" → wrapperName="UserAuth_login"
        assertTrue(content.contains("export class UserAuth_login"));
        assertTrue(content.contains("static of("));
    }

    @Test
    void generatesUserImplSkeletonOnce(@TempDir Path tempDir) throws IOException {
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        Path implDir = tempDir.resolve("impl");
        generator.generateUserImplSkeleton(ir, implDir, "user-auth");

        Path implFile = implDir.resolve("UserAuthImpl.ts");
        assertTrue(Files.exists(implFile));

        // Second call should not overwrite
        String firstContent = Files.readString(implFile);
        generator.generateUserImplSkeleton(ir, implDir, "user-auth");
        String secondContent = Files.readString(implFile);
        assertEquals(firstContent, secondContent);
    }

    @Test
    void generatesUserImplSkeletonOnlyIfAbsent(@TempDir Path tempDir) throws IOException {
        TypeScriptArtifactGenerator generator = new TypeScriptArtifactGenerator();
        BearIr ir = createTestIrWithOperations();

        Path implDir = tempDir.resolve("impl");
        Files.createDirectories(implDir);
        Path implFile = implDir.resolve("UserAuthImpl.ts");
        Files.writeString(implFile, "// existing content");

        generator.generateUserImplSkeleton(ir, implDir, "user-auth");

        String content = Files.readString(implFile);
        assertEquals("// existing content", content);
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
