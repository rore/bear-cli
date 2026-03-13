package com.bear.kernel.target.analyzer;

import com.bear.kernel.target.GovernanceProfile;
import com.bear.kernel.target.TargetId;

import java.nio.file.Path;
import java.util.List;

public interface AnalyzerProvider {

    AnalyzerId analyzerId();

    AnalyzerCapabilities capabilities();

    boolean supports(TargetId targetId, GovernanceProfile profile);

    EvidenceBundle collectEvidence(Path projectRoot, List<Path> governedRoots, AnalysisOptions options);
}
