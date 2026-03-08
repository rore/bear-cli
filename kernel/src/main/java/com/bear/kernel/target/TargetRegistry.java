package com.bear.kernel.target;

import com.bear.kernel.target.jvm.JvmTarget;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class TargetRegistry {
    private static final TargetRegistry DEFAULT = new TargetRegistry(Map.of(TargetId.JVM, new JvmTarget()));

    private final Map<TargetId, Target> targets;

    public TargetRegistry(Map<TargetId, Target> targets) {
        this.targets = Map.copyOf(targets);
        if (!this.targets.containsKey(TargetId.JVM)) {
            throw new IllegalArgumentException("TargetRegistry requires a JVM target");
        }
    }

    public static TargetRegistry defaultRegistry() {
        return DEFAULT;
    }

    public Target resolve(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        return targets.get(TargetId.JVM);
    }
}
