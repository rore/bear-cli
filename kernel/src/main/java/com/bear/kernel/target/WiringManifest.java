package com.bear.kernel.target;

import java.util.List;

public record WiringManifest(
    String schemaVersion,
    String blockKey,
    String entrypointFqcn,
    String logicInterfaceFqcn,
    String implFqcn,
    String implSourcePath,
    String blockRootSourceDir,
    List<String> governedSourceRoots,
    List<String> requiredEffectPorts,
    List<String> constructorPortParams,
    List<String> logicRequiredPorts,
    List<String> wrapperOwnedSemanticPorts,
    List<String> wrapperOwnedSemanticChecks,
    List<BlockPortBinding> blockPortBindings
) {
    public WiringManifest(
        String schemaVersion,
        String blockKey,
        String entrypointFqcn,
        String logicInterfaceFqcn,
        String implFqcn,
        String implSourcePath,
        String blockRootSourceDir,
        List<String> governedSourceRoots,
        List<String> requiredEffectPorts,
        List<String> constructorPortParams,
        List<String> logicRequiredPorts,
        List<String> wrapperOwnedSemanticPorts,
        List<String> wrapperOwnedSemanticChecks
    ) {
        this(
            schemaVersion,
            blockKey,
            entrypointFqcn,
            logicInterfaceFqcn,
            implFqcn,
            implSourcePath,
            blockRootSourceDir,
            governedSourceRoots,
            requiredEffectPorts,
            constructorPortParams,
            logicRequiredPorts,
            wrapperOwnedSemanticPorts,
            wrapperOwnedSemanticChecks,
            List.of()
        );
    }
}

