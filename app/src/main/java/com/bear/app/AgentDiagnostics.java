package com.bear.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

final class AgentDiagnostics {
    static final String SCHEMA_VERSION = "bear.nextAction.v1";
    static final int MAX_VIOLATIONS = 200;
    static final int MAX_CLUSTER_FILES = 50;

    private AgentDiagnostics() {
    }

    static AgentProblem problem(
        AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        AgentSeverity severity,
        String blockId,
        String file,
        AgentSpan span,
        String messageKey,
        String message,
        Map<String, String> evidence
    ) {
        String normalizedReasonKey = reasonKey == null || reasonKey.isBlank() ? null : reasonKey;
        String normalizedRuleId = ruleId == null || ruleId.isBlank() ? null : ruleId;
        if (category == AgentCategory.INFRA && normalizedRuleId != null) {
            throw new IllegalArgumentException("INFRA problems must not set ruleId");
        }
        String normalizedFile = file == null || file.isBlank() ? null : file.replace('\\', '/');
        Map<String, String> normalizedEvidence = normalizeEvidence(evidence);
        String templateVariant = normalizedEvidence.getOrDefault("templateVariant", "");
        String identityKey = normalizedEvidence.getOrDefault("identityKey", "");
        if (RepeatableRuleRegistry.requiresIdentityKey(normalizedRuleId) && identityKey.isBlank()) {
            throw new IllegalArgumentException("Repeatable governance rule must set evidence.identityKey: " + normalizedRuleId);
        }
        String id = problemId(
            category,
            failureCode,
            normalizedRuleId,
            normalizedReasonKey,
            blockId,
            normalizedFile,
            span,
            templateVariant,
            identityKey,
            messageKey
        );
        return new AgentProblem(
            id,
            category,
            failureCode,
            normalizedRuleId,
            normalizedReasonKey,
            severity,
            blockId,
            normalizedFile,
            span,
            messageKey,
            message,
            normalizedEvidence
        );
    }

    static AgentPayload payloadForCheck(CheckResult result, AgentCommandContext commandContext) {
        return payload(commandContext, result.exitCode(), result.problems(), true);
    }

    static AgentPayload payloadForPrCheck(PrCheckResult result, AgentCommandContext commandContext) {
        return payload(commandContext, result.exitCode(), result.problems(), true);
    }

    static AgentPayload payloadForCheck(CheckResult result, String mode, boolean collectAll) {
        return payload(
            AgentCommandContext.minimal("check", mode, collectAll ? "all" : "first", true),
            result.exitCode(),
            result.problems(),
            true
        );
    }

    static AgentPayload payloadForPrCheck(PrCheckResult result, String mode, boolean collectAll) {
        return payload(
            AgentCommandContext.minimal("pr-check", mode, collectAll ? "all" : "first", true),
            result.exitCode(),
            result.problems(),
            true
        );
    }

    static AgentPayload payload(
        String command,
        String mode,
        String collectMode,
        int exitCode,
        List<AgentProblem> problems
    ) {
        return payload(command, mode, collectMode, exitCode, problems, false);
    }

    static AgentPayload payload(
        String command,
        String mode,
        String collectMode,
        int exitCode,
        List<AgentProblem> problems,
        boolean agentMode
    ) {
        AgentCommandContext context = AgentCommandContext.minimal(command, mode, collectMode, agentMode);
        return payload(context, exitCode, problems, agentMode);
    }

    static AgentPayload payload(
        AgentCommandContext commandContext,
        int exitCode,
        List<AgentProblem> problems,
        boolean agentMode
    ) {
        List<AgentProblem> source = problems == null ? List.of() : List.copyOf(problems);
        List<AgentProblem> normalized = new ArrayList<>(source.size());
        for (AgentProblem problem : source) {
            normalized.add(problem(
                problem.category(),
                problem.failureCode(),
                problem.ruleId(),
                problem.reasonKey(),
                problem.severity(),
                problem.blockId(),
                problem.file(),
                problem.span(),
                problem.messageKey(),
                problem.message(),
                problem.evidence()
            ));
        }
        normalized.sort(problemComparator(commandContext.command()));
        List<ClusterInternal> fullClusters = cluster(commandContext.command(), normalized);
        ClusterInternal primaryCluster = selectPrimaryCluster(commandContext.command(), fullClusters);

        List<AgentProblem> retained = retainProblems(commandContext.command(), normalized, fullClusters, primaryCluster, MAX_VIOLATIONS);
        AgentNextAction nextAction = primaryCluster == null
            ? null
            : AgentTemplateRegistry.render(commandContext, primaryCluster.toPublicCluster());
        return new AgentPayload(
            SCHEMA_VERSION,
            commandContext.command(),
            commandContext.mode(),
            commandContext.collectMode(),
            exitCode == CliCodes.EXIT_OK ? "ok" : "fail",
            exitCode,
            retained.size() < normalized.size(),
            MAX_VIOLATIONS,
            Math.max(0, normalized.size() - retained.size()),
            List.copyOf(retained),
            toPublicClusters(fullClusters),
            nextAction,
            Map.of()
        );
    }
    static String toJson(AgentPayload payload) {
        StringBuilder out = new StringBuilder(4096);
        JsonWriter writer = new JsonWriter(out);
        writer.beginObject();
        writer.field("schemaVersion", payload.schemaVersion());
        writer.field("command", payload.command());
        writer.field("mode", payload.mode());
        writer.field("collectMode", payload.collectMode());
        writer.field("status", payload.status());
        writer.field("exitCode", payload.exitCode());

        writer.field("truncated", payload.truncated());
        writer.field("maxViolations", payload.maxViolations());
        writer.field("suppressedViolations", payload.suppressedViolations());
        writer.fieldName("problems");
        writer.beginArray();
        for (AgentProblem problem : payload.problems()) {
            writer.beginObject();
            writer.field("id", problem.id());
            writer.field("category", problem.category().name());
            writer.field("failureCode", problem.failureCode());
            writer.nullableField("ruleId", problem.ruleId());
            writer.nullableField("reasonKey", problem.reasonKey());
            writer.field("severity", problem.severity().name().toLowerCase());
            writer.nullableField("blockId", problem.blockId());
            writer.nullableField("file", problem.file());
            if (problem.span() == null) {
                writer.nullField("span");
            } else {
                writer.fieldName("span");
                writer.beginObject();
                writer.field("startLine", problem.span().startLine());
                writer.field("startCol", problem.span().startCol());
                writer.field("endLine", problem.span().endLine());
                writer.field("endCol", problem.span().endCol());
                writer.endObject();
            }
            writer.field("messageKey", problem.messageKey());
            writer.field("message", problem.message());
            if (problem.evidence().isEmpty()) {
                writer.fieldName("evidence");
                writer.beginObject();
                writer.endObject();
            } else {
                writer.fieldName("evidence");
                writer.beginObject();
                TreeMap<String, String> sorted = new TreeMap<>(problem.evidence());
                for (Map.Entry<String, String> entry : sorted.entrySet()) {
                    writer.field(entry.getKey(), entry.getValue());
                }
                writer.endObject();
            }
            writer.endObject();
        }
        writer.endArray();
        writer.fieldName("clusters");
        writer.beginArray();
        for (AgentCluster cluster : payload.clusters()) {
            writer.beginObject();
            writer.field("clusterId", cluster.clusterId());
            writer.field("category", cluster.category().name());
            writer.field("failureCode", cluster.failureCode());
            writer.nullableField("ruleId", cluster.ruleId());
            writer.nullableField("reasonKey", cluster.reasonKey());
            writer.nullableField("blockId", cluster.blockId());
            writer.field("count", cluster.count());
            writer.fieldName("files");
            writer.beginArray();
            for (String file : cluster.files()) {
                writer.arrayValue(file);
            }
            writer.endArray();
            writer.field("filesTruncated", cluster.filesTruncated());
            writer.field("summary", cluster.summary());
            writer.endObject();
        }
        writer.endArray();
        if (payload.nextAction() == null) {
            writer.nullField("nextAction");
        } else {
            writer.fieldName("nextAction");
            writer.beginObject();
            writer.field("kind", payload.nextAction().kind());
            writer.field("primaryClusterId", payload.nextAction().primaryClusterId());
            writer.field("title", payload.nextAction().title());
            writer.fieldName("steps");
            writer.beginArray();
            for (String step : payload.nextAction().steps()) {
                writer.arrayValue(step);
            }
            writer.endArray();
            writer.fieldName("commands");
            writer.beginArray();
            for (String command : payload.nextAction().commands()) {
                writer.arrayValue(command);
            }
            writer.endArray();
            writer.fieldName("links");
            writer.beginArray();
            for (String link : payload.nextAction().links()) {
                writer.arrayValue(link);
            }
            writer.endArray();
            writer.endObject();
        }
        writer.fieldName("extensions");
        writer.beginObject();
        writer.endObject();
        writer.endObject();
        return out.toString();
    }

    private static List<AgentCluster> toPublicClusters(List<ClusterInternal> internals) {
        ArrayList<AgentCluster> out = new ArrayList<>(internals.size());
        for (ClusterInternal internal : internals) {
            out.add(internal.toPublicCluster());
        }
        return List.copyOf(out);
    }

    private static List<ClusterInternal> cluster(String command, List<AgentProblem> problems) {
        LinkedHashMap<String, List<AgentProblem>> byKey = new LinkedHashMap<>();
        for (AgentProblem problem : problems) {
            String key = clusterKey(problem);
            byKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(problem);
        }
        ArrayList<ClusterInternal> clusters = new ArrayList<>();
        for (Map.Entry<String, List<AgentProblem>> entry : byKey.entrySet()) {
            List<AgentProblem> clusterProblems = entry.getValue();
            if (clusterProblems.isEmpty()) {
                continue;
            }
            clusterProblems.sort(problemComparator(command));
            AgentProblem representative = clusterProblems.get(0);
            TreeSet<String> allFiles = new TreeSet<>();
            for (AgentProblem clusterProblem : clusterProblems) {
                if (clusterProblem.file() != null) {
                    allFiles.add(clusterProblem.file());
                }
            }
            ArrayList<String> files = new ArrayList<>();
            int idx = 0;
            for (String file : allFiles) {
                if (idx >= MAX_CLUSTER_FILES) {
                    break;
                }
                files.add(file);
                idx++;
            }
            boolean filesTruncated = allFiles.size() > files.size();
            String clusterId = shortHash("cluster", entry.getKey());
            String summary = summary(representative, clusterProblems.size(), files);
            clusters.add(new ClusterInternal(
                clusterId,
                entry.getKey(),
                representative.category(),
                representative.failureCode(),
                representative.ruleId(),
                representative.reasonKey(),
                representative.blockId(),
                clusterProblems,
                List.copyOf(files),
                filesTruncated,
                summary
            ));
        }
        clusters.sort(clusterComparator(command));
        return clusters;
    }

    private static String summary(AgentProblem representative, int count, List<String> files) {
        StringBuilder out = new StringBuilder();
        out.append(representative.failureCode());
        if (representative.ruleId() != null) {
            out.append(":").append(representative.ruleId());
        } else if (representative.reasonKey() != null) {
            out.append(":").append(representative.reasonKey());
        }
        out.append(" (count=").append(count).append(")");
        if (!files.isEmpty()) {
            out.append(" files=").append(files.get(0));
            if (files.size() > 1) {
                out.append(",+").append(files.size() - 1).append(" more");
            }
        }
        return out.toString();
    }

    private static ClusterInternal selectPrimaryCluster(String command, List<ClusterInternal> clusters) {
        if (clusters.isEmpty()) {
            return null;
        }
        return Collections.min(clusters, primaryClusterComparator(command));
    }

    private static List<AgentProblem> retainProblems(
        String command,
        List<AgentProblem> sortedProblems,
        List<ClusterInternal> sortedClusters,
        ClusterInternal primaryCluster,
        int cap
    ) {
        if (sortedProblems.size() <= cap) {
            return List.copyOf(sortedProblems);
        }

        Map<String, List<AgentProblem>> byCluster = new LinkedHashMap<>();
        for (ClusterInternal cluster : sortedClusters) {
            byCluster.put(cluster.clusterKey(), new ArrayList<>(cluster.problems()));
        }
        LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
        ArrayList<AgentProblem> selected = new ArrayList<>();

        if (primaryCluster != null && !primaryCluster.problems().isEmpty()) {
            AgentProblem representative = primaryCluster.problems().get(0);
            selectedIds.add(representative.id());
            selected.add(representative);
        }

        for (ClusterInternal cluster : sortedClusters) {
            if (selected.size() >= cap || cluster.problems().isEmpty()) {
                break;
            }
            AgentProblem representative = cluster.problems().get(0);
            if (selectedIds.add(representative.id())) {
                selected.add(representative);
            }
        }
        if (selected.size() >= cap) {
            return List.copyOf(selected.subList(0, cap));
        }

        HashMap<String, Integer> offsets = new HashMap<>();
        for (ClusterInternal cluster : sortedClusters) {
            offsets.put(cluster.clusterKey(), 1);
        }

        boolean advanced = true;
        while (selected.size() < cap && advanced) {
            advanced = false;
            for (ClusterInternal cluster : sortedClusters) {
                if (selected.size() >= cap) {
                    break;
                }
                List<AgentProblem> problems = byCluster.getOrDefault(cluster.clusterKey(), List.of());
                int offset = offsets.getOrDefault(cluster.clusterKey(), 1);
                while (offset < problems.size() && selectedIds.contains(problems.get(offset).id())) {
                    offset++;
                }
                offsets.put(cluster.clusterKey(), offset + 1);
                if (offset < problems.size()) {
                    AgentProblem candidate = problems.get(offset);
                    if (selectedIds.add(candidate.id())) {
                        selected.add(candidate);
                        advanced = true;
                    }
                }
            }
        }
        return List.copyOf(selected);
    }

    private static Comparator<ClusterInternal> primaryClusterComparator(String command) {
        return Comparator
            .comparingInt((ClusterInternal cluster) -> clusterRank(command, cluster))
            .thenComparingInt(cluster -> clusterSeverity(cluster).order)
            .thenComparingInt(cluster -> cluster.category() == AgentCategory.INFRA ? 0 : 1)
            .thenComparing(ClusterInternal::clusterKey);
    }

    private static Comparator<ClusterInternal> clusterComparator(String command) {
        return Comparator
            .comparingInt((ClusterInternal cluster) -> clusterRank(command, cluster))
            .thenComparingInt(cluster -> clusterSeverity(cluster).order)
            .thenComparing(cluster -> cluster.category().name())
            .thenComparing(cluster -> cluster.failureCode())
            .thenComparing(cluster -> nullToEmpty(cluster.ruleId()))
            .thenComparing(cluster -> nullToEmpty(cluster.reasonKey()))
            .thenComparing(cluster -> nullToEmpty(cluster.blockId()))
            .thenComparing(ClusterInternal::clusterKey);
    }

    private static int clusterRank(String command, ClusterInternal cluster) {
        int best = Integer.MAX_VALUE;
        for (AgentProblem problem : cluster.problems()) {
            best = Math.min(best, exitRank(command, problem.failureCode()));
        }
        return best;
    }

    private static AgentSeverity clusterSeverity(ClusterInternal cluster) {
        AgentSeverity best = AgentSeverity.WARNING;
        for (AgentProblem problem : cluster.problems()) {
            if (problem.severity() == AgentSeverity.ERROR) {
                best = AgentSeverity.ERROR;
                break;
            }
        }
        return best;
    }

    private static Comparator<AgentProblem> problemComparator(String command) {
        return Comparator
            .comparingInt((AgentProblem problem) -> exitRank(command, problem.failureCode()))
            .thenComparingInt(problem -> problem.severity().order)
            .thenComparing(problem -> problem.category().name())
            .thenComparing(AgentProblem::failureCode)
            .thenComparing(problem -> nullToEmpty(problem.ruleId()))
            .thenComparing(problem -> nullToEmpty(problem.reasonKey()))
            .thenComparing(problem -> nullToEmpty(problem.blockId()))
            .thenComparing(problem -> nullToEmpty(problem.file()))
            .thenComparing(problem -> spanKey(problem.span()))
            .thenComparing(AgentProblem::id);
    }

    private static String spanKey(AgentSpan span) {
        if (span == null) {
            return "";
        }
        return span.startLine() + ":" + span.startCol() + ":" + span.endLine() + ":" + span.endCol();
    }

    private static int exitRank(String command, String failureCode) {
        int exitCode = failureCodeToExitCode(failureCode);
        if ("pr-check".equals(command)) {
            return AllModeAggregation.severityRankPr(exitCode);
        }
        return AllModeAggregation.severityRankCheck(exitCode);
    }

    private static int failureCodeToExitCode(String failureCode) {
        if (failureCode == null) {
            return CliCodes.EXIT_INTERNAL;
        }
        return switch (failureCode) {
            case CliCodes.USAGE_INVALID_ARGS, CliCodes.USAGE_UNKNOWN_COMMAND -> CliCodes.EXIT_USAGE;
            case CliCodes.IR_VALIDATION, CliCodes.MANIFEST_INVALID, CliCodes.POLICY_INVALID, CliCodes.INDEX_REQUIRED_MISSING -> CliCodes.EXIT_VALIDATION;
            case CliCodes.DRIFT_MISSING_BASELINE, CliCodes.DRIFT_DETECTED -> CliCodes.EXIT_DRIFT;
            case CliCodes.TEST_FAILURE, CliCodes.COMPILE_FAILURE, CliCodes.TEST_TIMEOUT, CliCodes.INVARIANT_VIOLATION -> CliCodes.EXIT_TEST_FAILURE;
            case CliCodes.BOUNDARY_EXPANSION -> CliCodes.EXIT_BOUNDARY_EXPANSION;
            case CliCodes.UNDECLARED_REACH, CliCodes.HYGIENE_UNEXPECTED_PATHS, CliCodes.REFLECTION_DISPATCH_FORBIDDEN -> CliCodes.EXIT_UNDECLARED_REACH;
            case CliCodes.BOUNDARY_BYPASS, CliCodes.PORT_IMPL_OUTSIDE_GOVERNED_ROOT, CliCodes.MULTI_BLOCK_PORT_IMPL_FORBIDDEN -> CliCodes.EXIT_BOUNDARY_BYPASS;
            case CliCodes.IO_ERROR, CliCodes.IO_GIT, CliCodes.CONTAINMENT_NOT_VERIFIED, CliCodes.UNBLOCK_LOCKED -> CliCodes.EXIT_IO;
            case CliCodes.REPO_MULTI_BLOCK_FAILED -> CliCodes.EXIT_INTERNAL;
            default -> CliCodes.EXIT_INTERNAL;
        };
    }

    private static String clusterKey(AgentProblem problem) {
        String qualifier = problem.ruleId() != null ? problem.ruleId() : nullToEmpty(problem.reasonKey());
        String block = problem.blockId() == null || problem.blockId().isBlank() ? "_global" : problem.blockId();
        String primaryFileGroup;
        if (problem.file() != null && !problem.file().isBlank()) {
            primaryFileGroup = "FILE:" + problem.file();
        } else if (problem.evidence().containsKey("governedRoot")) {
            primaryFileGroup = "ROOT:" + problem.evidence().get("governedRoot");
        } else {
            primaryFileGroup = "GLOBAL:_global";
        }
        return problem.category().name() + "|" + problem.failureCode() + "|" + qualifier + "|" + block + "|" + primaryFileGroup;
    }

    private static String problemId(
        AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        String blockId,
        String file,
        AgentSpan span,
        String templateVariant,
        String identityKey,
        String messageKey
    ) {
        String tuple = String.join(
            "\u001f",
            category.name(),
            nullToEmpty(failureCode),
            nullToEmpty(ruleId),
            nullToEmpty(reasonKey),
            nullToEmpty(blockId),
            nullToEmpty(file),
            span == null ? "" : Integer.toString(span.startLine()),
            span == null ? "" : Integer.toString(span.startCol()),
            span == null ? "" : Integer.toString(span.endLine()),
            span == null ? "" : Integer.toString(span.endCol()),
            nullToEmpty(templateVariant),
            nullToEmpty(identityKey),
            nullToEmpty(messageKey)
        );
        return shortHash("problem", tuple);
    }

    private static String shortHash(String prefix, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((prefix + ":" + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> normalizeEvidence(Map<String, String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Map.of();
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : evidence.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            sorted.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(sorted);
    }

    enum AgentCategory {
        INFRA,
        GOVERNANCE
    }

    enum AgentSeverity {
        ERROR(0),
        WARNING(1);

        final int order;

        AgentSeverity(int order) {
            this.order = order;
        }
    }

    record AgentSpan(int startLine, int startCol, int endLine, int endCol) {
    }

    record AgentProblem(
        String id,
        AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        AgentSeverity severity,
        String blockId,
        String file,
        AgentSpan span,
        String messageKey,
        String message,
        Map<String, String> evidence
    ) {
    }

    record AgentCluster(
        String clusterId,
        AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        String blockId,
        int count,
        List<String> files,
        boolean filesTruncated,
        String summary
    ) {
    }

    record AgentNextAction(
        String kind,
        String primaryClusterId,
        String title,
        List<String> steps,
        List<String> commands,
        List<String> links
    ) {
    }

    record AgentPayload(
        String schemaVersion,
        String command,
        String mode,
        String collectMode,
        String status,
        int exitCode,
        boolean truncated,
        int maxViolations,
        int suppressedViolations,
        List<AgentProblem> problems,
        List<AgentCluster> clusters,
        AgentNextAction nextAction,
        Map<String, String> extensions
    ) {
    }

    private record ClusterInternal(
        String clusterId,
        String clusterKey,
        AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        String blockId,
        List<AgentProblem> problems,
        List<String> files,
        boolean filesTruncated,
        String summary
    ) {
        AgentCluster toPublicCluster() {
            return new AgentCluster(
                clusterId,
                category,
                failureCode,
                ruleId,
                reasonKey,
                blockId,
                problems.size(),
                files,
                filesTruncated,
                summary
            );
        }
    }

    private static final class JsonWriter {
        private final StringBuilder out;
        private final ArrayList<Boolean> firstStack = new ArrayList<>();
        private boolean awaitingFieldValue;

        private JsonWriter(StringBuilder out) {
            this.out = Objects.requireNonNull(out);
        }

        void beginObject() {
            beforeValue();
            out.append('{');
            firstStack.add(true);
        }

        void endObject() {
            out.append('}');
            firstStack.remove(firstStack.size() - 1);
            awaitingFieldValue = false;
        }

        void beginArray() {
            beforeValue();
            out.append('[');
            firstStack.add(true);
        }

        void endArray() {
            out.append(']');
            firstStack.remove(firstStack.size() - 1);
            awaitingFieldValue = false;
        }

        void fieldName(String name) {
            beforeField();
            string(name);
            out.append(':');
            awaitingFieldValue = true;
        }

        void field(String name, String value) {
            fieldName(name);
            string(value);
            awaitingFieldValue = false;
        }

        void field(String name, int value) {
            fieldName(name);
            out.append(value);
            awaitingFieldValue = false;
        }

        void field(String name, boolean value) {
            fieldName(name);
            out.append(value);
            awaitingFieldValue = false;
        }

        void nullableField(String name, String value) {
            fieldName(name);
            if (value == null) {
                out.append("null");
            } else {
                string(value);
            }
            awaitingFieldValue = false;
        }

        void nullField(String name) {
            fieldName(name);
            out.append("null");
            awaitingFieldValue = false;
        }

        void arrayValue(String value) {
            beforeValue();
            string(value);
        }

        private void beforeField() {
            if (firstStack.isEmpty()) {
                return;
            }
            int idx = firstStack.size() - 1;
            if (firstStack.get(idx)) {
                firstStack.set(idx, false);
            } else {
                out.append(',');
            }
        }

        private void beforeValue() {
            if (awaitingFieldValue) {
                awaitingFieldValue = false;
                return;
            }
            if (firstStack.isEmpty()) {
                return;
            }
            int idx = firstStack.size() - 1;
            if (firstStack.get(idx)) {
                firstStack.set(idx, false);
            } else {
                out.append(',');
            }
        }

        private void string(String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                    }
                }
            }
            out.append('"');
        }
    }
}
