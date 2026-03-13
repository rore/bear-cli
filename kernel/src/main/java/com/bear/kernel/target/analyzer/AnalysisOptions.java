package com.bear.kernel.target.analyzer;

public record AnalysisOptions(boolean includeReferences, int maxDepth) {

    public static AnalysisOptions defaults() {
        return new AnalysisOptions(false, 1);
    }
}
