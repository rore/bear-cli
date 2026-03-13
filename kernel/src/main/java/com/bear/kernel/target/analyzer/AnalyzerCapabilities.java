package com.bear.kernel.target.analyzer;

public record AnalyzerCapabilities(
    boolean supportsImports,
    boolean supportsDependencies,
    boolean supportsOwnership,
    boolean supportsSymbols,
    boolean supportsSpans,
    boolean supportsReferences
) {
    public static AnalyzerCapabilities all() {
        return new AnalyzerCapabilities(true, true, true, true, true, true);
    }

    public static AnalyzerCapabilities none() {
        return new AnalyzerCapabilities(false, false, false, false, false, false);
    }
}
