package com.bear.app;

import com.bear.kernel.ir.BearIr;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

record CheckResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail
) {
}

record PrCheckResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail,
    List<String> deltaLines,
    boolean hasBoundary,
    boolean hasDeltas,
    List<String> governanceLines
) {
    PrCheckResult(
        int exitCode,
        List<String> stdoutLines,
        List<String> stderrLines,
        String category,
        String failureCode,
        String failurePath,
        String failureRemediation,
        String detail,
        List<String> deltaLines,
        boolean hasBoundary,
        boolean hasDeltas
    ) {
        this(
            exitCode,
            stdoutLines,
            stderrLines,
            category,
            failureCode,
            failurePath,
            failureRemediation,
            detail,
            deltaLines,
            hasBoundary,
            hasDeltas,
            List.of()
        );
    }
}

record FixResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail
) {
}

record CompileResult(
    int exitCode,
    List<String> stdoutLines,
    List<String> stderrLines,
    String category,
    String failureCode,
    String failurePath,
    String failureRemediation,
    String detail
) {
}

enum BlockStatus {
    PASS,
    FAIL,
    SKIP
}

record BlockExecutionResult(
    String name,
    String ir,
    String project,
    BlockStatus status,
    int exitCode,
    String category,
    String blockCode,
    String blockPath,
    String detail,
    String blockRemediation,
    String reason,
    String classification,
    List<String> deltaLines,
    List<String> governanceLines
) {
    BlockExecutionResult(
        String name,
        String ir,
        String project,
        BlockStatus status,
        int exitCode,
        String category,
        String blockCode,
        String blockPath,
        String detail,
        String blockRemediation,
        String reason,
        String classification,
        List<String> deltaLines
    ) {
        this(
            name,
            ir,
            project,
            status,
            exitCode,
            category,
            blockCode,
            blockPath,
            detail,
            blockRemediation,
            reason,
            classification,
            deltaLines,
            List.of()
        );
    }
}

record RepoAggregationResult(
    int exitCode,
    int total,
    int checked,
    int passed,
    int failed,
    int skipped,
    boolean failFastTriggered,
    int rootReachFailed,
    int rootTestFailed,
    int rootTestSkippedDueToReach,
    List<String> repoDeltaLines
) {
    RepoAggregationResult(
        int exitCode,
        int total,
        int checked,
        int passed,
        int failed,
        int skipped,
        boolean failFastTriggered,
        int rootReachFailed,
        int rootTestFailed,
        int rootTestSkippedDueToReach
    ) {
        this(
            exitCode,
            total,
            checked,
            passed,
            failed,
            skipped,
            failFastTriggered,
            rootReachFailed,
            rootTestFailed,
            rootTestSkippedDueToReach,
            List.of()
        );
    }
}

record CheckBlockedState(boolean blocked, String reason, String detail) {
    static CheckBlockedState notBlocked() {
        return new CheckBlockedState(false, "", "");
    }

    String summary() {
        return "reason=" + reason + "; detail=" + detail;
    }
}

record UndeclaredReachFinding(String path, String surface) {
}

record UndeclaredReachSurface(String label, Pattern pattern) {
    boolean matches(String content) {
        return pattern.matcher(content).find();
    }
}

record BoundaryBypassFinding(String rule, String path, String detail) {
}

record PortImplContainmentFinding(String interfaceFqcn, String implClassFqcn, String path) {
}

record MultiBlockPortImplFinding(String kind, String implClassFqcn, String generatedPackageCsv, String path) {
}

record MultiBlockPortImplAllowedSignal(String implClassFqcn, String generatedPackageCsv, String path) {
}

record AllCheckOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean failFast,
    boolean strictOrphans,
    boolean strictHygiene
) {
}

record AllCompileOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean failFast,
    boolean strictOrphans
) {
}

record AllFixOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean failFast,
    boolean strictOrphans
) {
}

record AllPrCheckOptions(
    Path repoRoot,
    Path blocksPath,
    Set<String> onlyNames,
    boolean strictOrphans,
    String baseRef
) {
}

enum DriftType {
    ADDED("ADDED", 0),
    REMOVED("REMOVED", 1),
    CHANGED("CHANGED", 2);

    final String label;
    final int order;

    DriftType(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

record DriftItem(String path, DriftType type) {
}

enum BoundaryType {
    CAPABILITY_ADDED("CAPABILITY_ADDED", 0),
    PURE_DEP_ADDED("PURE_DEP_ADDED", 1),
    PURE_DEP_VERSION_CHANGED("PURE_DEP_VERSION_CHANGED", 2),
    CAPABILITY_OP_ADDED("CAPABILITY_OP_ADDED", 3),
    INVARIANT_RELAXED("INVARIANT_RELAXED", 4);

    final String label;
    final int order;

    BoundaryType(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

record BoundarySignal(BoundaryType type, String key) {
}

enum PrClass {
    BOUNDARY_EXPANDING("BOUNDARY_EXPANDING", 0),
    ORDINARY("ORDINARY", 1);

    final String label;
    final int order;

    PrClass(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

enum PrCategory {
    SURFACE("SURFACE", 0),
    PORTS("PORTS", 1),
    ALLOWED_DEPS("ALLOWED_DEPS", 2),
    OPS("OPS", 3),
    IDEMPOTENCY("IDEMPOTENCY", 4),
    CONTRACT("CONTRACT", 5),
    INVARIANTS("INVARIANTS", 6);

    final String label;
    final int order;

    PrCategory(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

enum PrChange {
    CHANGED("CHANGED", 0),
    ADDED("ADDED", 1),
    REMOVED("REMOVED", 2);

    final String label;
    final int order;

    PrChange(String label, int order) {
        this.label = label;
        this.order = order;
    }
}

record PrDelta(PrClass clazz, PrCategory category, PrChange change, String key) {
}

record PrSurface(
    TreeSet<String> ports,
    Map<String, TreeSet<String>> opsByPort,
    Map<String, String> allowedDeps,
    BearIr.BlockIdempotency blockIdempotency,
    TreeSet<String> blockInvariants,
    TreeSet<String> operations,
    Map<String, Map<String, BearIr.FieldType>> inputsByOperation,
    Map<String, Map<String, BearIr.FieldType>> outputsByOperation,
    Map<String, TreeSet<String>> usesByOperation,
    Map<String, BearIr.OperationIdempotency> idempotencyByOperation,
    Map<String, TreeSet<String>> invariantsByOperation
) {
}

record BoundaryManifest(
    String schemaVersion,
    String target,
    String block,
    String irHash,
    String generatorVersion,
    Map<String, TreeSet<String>> capabilities,
    Map<String, String> allowedDeps,
    TreeSet<String> invariants
) {
}

record WiringManifest(
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
}

final class ManifestParseException extends Exception {
    private final String reasonCode;

    ManifestParseException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    String reasonCode() {
        return reasonCode;
    }
}

enum ProjectTestStatus {
    PASSED,
    FAILED,
    SHARED_DEPS_VIOLATION,
    INVARIANT_VIOLATION,
    TIMEOUT,
    LOCKED,
    BOOTSTRAP_IO
}

record ProjectTestResult(
    ProjectTestStatus status,
    String output,
    String attemptTrail,
    String firstLockLine,
    String firstBootstrapLine,
    String firstSharedDepsViolationLine,
    String cacheMode,
    boolean fallbackToUserCache
) {
}
