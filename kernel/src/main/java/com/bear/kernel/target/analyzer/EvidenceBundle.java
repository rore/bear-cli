package com.bear.kernel.target.analyzer;

import java.util.List;

public record EvidenceBundle(
    List<ImportEdge> imports,
    List<DependencyEdge> dependencies,
    List<OwnershipFact> ownership,
    List<ReferenceEdge> references,
    List<AnalyzerFinding> findings
) {
    public static EvidenceBundle empty() {
        return new EvidenceBundle(List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
