package com.bear.kernel;

import com.bear.kernel.target.jvm.JvmTarget;
import com.bear.kernel.target.Target;
import com.bear.kernel.target.TargetId;
import com.bear.kernel.target.TargetRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TargetRegistryTest {
    @Test
    void defaultRegistryResolvesJvmTarget() {
        Target target = TargetRegistry.defaultRegistry().resolve(Path.of("."));

        assertEquals(TargetId.JVM, target.targetId());
        assertInstanceOf(JvmTarget.class, target);
    }

    @Test
    void customRegistryReturnsConfiguredJvmTarget() {
        TargetRegistry registry = new TargetRegistry(Map.of(TargetId.JVM, new JvmTarget()));

        assertEquals(TargetId.JVM, registry.resolve(Path.of("." )).targetId());
    }
}
