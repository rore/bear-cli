package com.bear.kernel.policy;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record SharedAllowedDepsPolicy(List<Dependency> allowedDeps) {
    public static final String DEFAULT_RELATIVE_PATH = "bear-policy/_shared.policy.yaml";

    public SharedAllowedDepsPolicy {
        allowedDeps = allowedDeps == null ? List.of() : List.copyOf(allowedDeps);
    }

    public static SharedAllowedDepsPolicy empty() {
        return new SharedAllowedDepsPolicy(List.of());
    }

    public Map<String, String> asMap() {
        TreeMap<String, String> map = new TreeMap<>();
        for (Dependency dependency : allowedDeps) {
            map.put(dependency.maven(), dependency.version());
        }
        return Map.copyOf(map);
    }

    public record Dependency(String maven, String version) {
    }
}
