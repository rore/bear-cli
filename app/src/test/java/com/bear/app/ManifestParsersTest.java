package com.bear.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManifestParsersTest {
    @Test
    void parseManifestParsesCanonicalMinifiedPayload(@TempDir Path tempDir) throws Exception {
        Path manifest = tempDir.resolve("x.surface.json");
        Files.writeString(
            manifest,
            "{\"schemaVersion\":\"v0\",\"target\":\"jvm\",\"block\":\"withdraw\",\"irHash\":\"abc\",\"generatorVersion\":\"jvm-v0\",\"capabilities\":[{\"name\":\"ledger\",\"ops\":[\"debit\"]}],\"allowedDeps\":[{\"ga\":\"com.fasterxml.jackson.core:jackson-databind\",\"version\":\"2.17.2\"}],\"invariants\":[{\"kind\":\"non_negative\",\"field\":\"result.balance\"}]}"
        );

        BoundaryManifest parsed = ManifestParsers.parseManifest(manifest);

        assertEquals("v0", parsed.schemaVersion());
        assertEquals("withdraw", parsed.block());
        assertEquals("jvm-v0", parsed.generatorVersion());
        assertEquals("2.17.2", parsed.allowedDeps().get("com.fasterxml.jackson.core:jackson-databind"));
        assertEquals(true, parsed.capabilities().get("ledger").contains("debit"));
    }

    @Test
    void parseManifestRejectsMalformedJson(@TempDir Path tempDir) throws Exception {
        Path manifest = tempDir.resolve("x.surface.json");
        Files.writeString(manifest, "{");

        ManifestParseException ex = assertThrows(ManifestParseException.class, () -> ManifestParsers.parseManifest(manifest));
        assertEquals("MALFORMED_JSON", ex.reasonCode());
    }

    @Test
    void parseWiringManifestParsesRequiredFields(@TempDir Path tempDir) throws Exception {
        Path wiring = tempDir.resolve("x.wiring.json");
        Files.writeString(
            wiring,
            "{\"schemaVersion\":\"v1\",\"blockKey\":\"withdraw\",\"entrypointFqcn\":\"com.bear.generated.withdraw.Withdraw\",\"logicInterfaceFqcn\":\"com.bear.generated.withdraw.WithdrawLogic\",\"implFqcn\":\"blocks.withdraw.impl.WithdrawImpl\",\"implSourcePath\":\"src/main/java/blocks/withdraw/impl/WithdrawImpl.java\",\"requiredEffectPorts\":[\"ledgerPort\"],\"constructorPortParams\":[\"logic\",\"ledgerPort\"]}"
        );

        WiringManifest parsed = ManifestParsers.parseWiringManifest(wiring);

        assertEquals("withdraw", parsed.blockKey());
        assertEquals(1, parsed.requiredEffectPorts().size());
        assertEquals("ledgerPort", parsed.requiredEffectPorts().get(0));
        assertEquals(1, parsed.logicRequiredPorts().size());
        assertEquals("ledgerPort", parsed.logicRequiredPorts().get(0));
        assertEquals(0, parsed.wrapperOwnedSemanticPorts().size());
    }
}
