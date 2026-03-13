package com.bear.kernel.target.analyzer;

public record DependencyEdge(String packageName, String version, String scope) {
}
