package com.bear.app;

import com.bear.kernel.target.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PrGovernanceTelemetry {
    static final String EXTENSION_KEY = "prGovernance";
    static final String SCHEMA_VERSION = "bear.pr-governance.v1";

    private static final List<String> CLASSIFICATION_ORDER = List.of(
        "CI_INTERNAL_ERROR",
        "CI_VALIDATION_OR_USAGE_ERROR",
        "CI_IO_GIT_ERROR",
        "CI_POLICY_BYPASS_ATTEMPT",
        "CI_BOUNDARY_EXPANSION",
        "CI_DEPENDENCY_POWER_EXPANSION",
        "CI_NO_STRUCTURAL_CHANGE"
    );

    private static final Map<String, Integer> CLASSIFICATION_ORDER_INDEX = classificationOrderIndex();
    private static final Map<String, Integer> DELTA_CLASS_ORDER = deltaClassOrder();
    private static final Map<String, Integer> DELTA_CATEGORY_ORDER = deltaCategoryOrder();
    private static final Map<String, Integer> DELTA_CHANGE_ORDER = deltaChangeOrder();

    private PrGovernanceTelemetry() {
    }

    static Snapshot single(int exitCode, List<PrDelta> deltas, List<Signal> governanceSignals) {
        List<Delta> normalizedDeltas = normalizeDeltas(deltas);
        List<Signal> normalizedSignals = normalizeSignals(governanceSignals);
        return new Snapshot(
            "single",
            !normalizedDeltas.isEmpty(),
            hasBoundaryExpansion(normalizedDeltas),
            classifications(exitCode, normalizedDeltas, List.of()),
            normalizedDeltas,
            normalizedSignals,
            List.of()
        );
    }

    static BlockSnapshot block(String blockId, String ir, int exitCode, List<PrDelta> deltas, List<Signal> governanceSignals) {
        List<Delta> normalizedDeltas = normalizeDeltas(deltas);
        List<Signal> normalizedSignals = normalizeSignals(governanceSignals);
        return new BlockSnapshot(
            blockId,
            ir,
            !normalizedDeltas.isEmpty(),
            hasBoundaryExpansion(normalizedDeltas),
            classifications(exitCode, normalizedDeltas, List.of()),
            normalizedDeltas,
            normalizedSignals
        );
    }

    static Snapshot all(int exitCode, List<PrDelta> repoDeltas, List<Signal> repoSignals, List<BlockSnapshot> blocks) {
        List<Delta> normalizedRepoDeltas = normalizeDeltas(repoDeltas);
        List<Signal> normalizedRepoSignals = normalizeSignals(repoSignals);
        ArrayList<BlockSnapshot> normalizedBlocks = new ArrayList<>();
        if (blocks != null) {
            normalizedBlocks.addAll(blocks);
        }
        normalizedBlocks.sort(Comparator
            .comparing(BlockSnapshot::blockId)
            .thenComparing(BlockSnapshot::ir));
        boolean hasDeltas = !normalizedRepoDeltas.isEmpty()
            || normalizedBlocks.stream().anyMatch(BlockSnapshot::hasDeltas);
        boolean hasBoundaryExpansion = hasBoundaryExpansion(normalizedRepoDeltas)
            || normalizedBlocks.stream().anyMatch(BlockSnapshot::hasBoundaryExpansion);
        return new Snapshot(
            "all",
            hasDeltas,
            hasBoundaryExpansion,
            classifications(exitCode, normalizedRepoDeltas, normalizedBlocks),
            normalizedRepoDeltas,
            normalizedRepoSignals,
            normalizedBlocks
        );
    }

    static Signal multiBlockPortImplAllowed(MultiBlockPortImplAllowedSignal signal) {
        TreeMap<String, Object> details = new TreeMap<>();
        details.put("generatedPackages", splitGeneratedPackages(signal.generatedPackageCsv()));
        details.put("implClassFqcn", signal.implClassFqcn());
        return new Signal(
            "MULTI_BLOCK_PORT_IMPL_ALLOWED",
            signal.path().replace('\\', '/'),
            details
        );
    }

    static Map<String, Object> extensionFields(Snapshot snapshot) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", SCHEMA_VERSION);
        fields.put("scope", snapshot.scope());
        fields.put("hasDeltas", snapshot.hasDeltas());
        fields.put("hasBoundaryExpansion", snapshot.hasBoundaryExpansion());
        fields.put("classifications", snapshot.classifications());
        fields.put("deltas", snapshot.deltas().stream().map(PrGovernanceTelemetry::toMap).toList());
        fields.put("governanceSignals", snapshot.governanceSignals().stream().map(PrGovernanceTelemetry::toMap).toList());
        if ("all".equals(snapshot.scope())) {
            fields.put("blocks", snapshot.blocks().stream().map(PrGovernanceTelemetry::toMap).toList());
        }
        return fields;
    }

    private static Map<String, Object> toMap(Delta delta) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("class", delta.clazz());
        fields.put("category", delta.category());
        fields.put("change", delta.change());
        fields.put("key", delta.key());
        fields.put("deltaId", delta.deltaId());
        return fields;
    }

    private static Map<String, Object> toMap(Signal signal) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", signal.type());
        fields.put("path", signal.path());
        fields.put("details", signal.details());
        return fields;
    }

    private static Map<String, Object> toMap(BlockSnapshot block) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("blockId", block.blockId());
        fields.put("ir", block.ir());
        fields.put("hasDeltas", block.hasDeltas());
        fields.put("hasBoundaryExpansion", block.hasBoundaryExpansion());
        fields.put("classifications", block.classifications());
        fields.put("deltas", block.deltas().stream().map(PrGovernanceTelemetry::toMap).toList());
        fields.put("governanceSignals", block.governanceSignals().stream().map(PrGovernanceTelemetry::toMap).toList());
        return fields;
    }

    private static List<String> classifications(int exitCode, List<Delta> deltas, List<BlockSnapshot> blocks) {
        ArrayList<String> out = new ArrayList<>();
        out.add(primaryClassification(exitCode, deltas, blocks));
        if (hasDependencyPowerExpansion(deltas, blocks)) {
            out.add("CI_DEPENDENCY_POWER_EXPANSION");
        }
        out.sort(Comparator.comparingInt(PrGovernanceTelemetry::classificationIndex));
        return List.copyOf(out);
    }

    private static String primaryClassification(int exitCode, List<Delta> deltas, List<BlockSnapshot> blocks) {
        if (exitCode == CliCodes.EXIT_INTERNAL) {
            return "CI_INTERNAL_ERROR";
        }
        if (exitCode == CliCodes.EXIT_VALIDATION || exitCode == CliCodes.EXIT_USAGE) {
            return "CI_VALIDATION_OR_USAGE_ERROR";
        }
        if (exitCode == CliCodes.EXIT_IO) {
            return "CI_IO_GIT_ERROR";
        }
        if (exitCode == CliCodes.EXIT_BOUNDARY_BYPASS) {
            return "CI_POLICY_BYPASS_ATTEMPT";
        }
        if (exitCode == CliCodes.EXIT_BOUNDARY_EXPANSION || hasBoundaryExpansion(deltas) || blocks.stream().anyMatch(BlockSnapshot::hasBoundaryExpansion)) {
            return "CI_BOUNDARY_EXPANSION";
        }
        return "CI_NO_STRUCTURAL_CHANGE";
    }

    private static boolean hasBoundaryExpansion(List<Delta> deltas) {
        return deltas.stream().anyMatch(delta -> "BOUNDARY_EXPANDING".equals(delta.clazz()));
    }

    private static boolean hasDependencyPowerExpansion(List<Delta> deltas, List<BlockSnapshot> blocks) {
        return deltas.stream().anyMatch(PrGovernanceTelemetry::isDependencyPowerExpansion)
            || blocks.stream()
            .flatMap(block -> block.deltas().stream())
            .anyMatch(PrGovernanceTelemetry::isDependencyPowerExpansion);
    }

    private static boolean isDependencyPowerExpansion(Delta delta) {
        return "BOUNDARY_EXPANDING".equals(delta.clazz()) && "ALLOWED_DEPS".equals(delta.category());
    }

    private static List<Delta> normalizeDeltas(List<PrDelta> deltas) {
        ArrayList<Delta> out = new ArrayList<>();
        if (deltas != null) {
            for (PrDelta delta : deltas) {
                out.add(new Delta(delta.clazz().label, delta.category().label, delta.change().label, delta.key()));
            }
        }
        out.sort(Comparator
            .comparingInt((Delta delta) -> DELTA_CLASS_ORDER.getOrDefault(delta.clazz(), Integer.MAX_VALUE))
            .thenComparingInt(delta -> DELTA_CATEGORY_ORDER.getOrDefault(delta.category(), Integer.MAX_VALUE))
            .thenComparingInt(delta -> DELTA_CHANGE_ORDER.getOrDefault(delta.change(), Integer.MAX_VALUE))
            .thenComparing(Delta::key));
        return List.copyOf(out);
    }

    private static List<Signal> normalizeSignals(List<Signal> governanceSignals) {
        ArrayList<Signal> out = new ArrayList<>();
        if (governanceSignals != null) {
            out.addAll(governanceSignals);
        }
        out.sort(Comparator
            .comparing(Signal::type)
            .thenComparing(Signal::path)
            .thenComparing(PrGovernanceTelemetry::signalDetailsKey));
        return List.copyOf(out);
    }

    private static List<String> splitGeneratedPackages(String csv) {
        ArrayList<String> packages = new ArrayList<>();
        if (csv != null && !csv.isBlank()) {
            for (String token : csv.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    packages.add(trimmed);
                }
            }
        }
        packages.sort(String::compareTo);
        return List.copyOf(packages);
    }

    private static String signalDetailsKey(Signal signal) {
        return new TreeMap<>(signal.details()).toString();
    }

    private static Map<String, Integer> classificationOrderIndex() {
        TreeMap<String, Integer> order = new TreeMap<>();
        for (int i = 0; i < CLASSIFICATION_ORDER.size(); i++) {
            order.put(CLASSIFICATION_ORDER.get(i), i);
        }
        return Map.copyOf(order);
    }

    private static Map<String, Integer> deltaClassOrder() {
        return Map.of(
            "BOUNDARY_EXPANDING", PrClass.BOUNDARY_EXPANDING.order,
            "ORDINARY", PrClass.ORDINARY.order
        );
    }

    private static Map<String, Integer> deltaCategoryOrder() {
        TreeMap<String, Integer> order = new TreeMap<>();
        for (PrCategory category : PrCategory.values()) {
            order.put(category.label, category.order);
        }
        return Map.copyOf(order);
    }

    private static Map<String, Integer> deltaChangeOrder() {
        TreeMap<String, Integer> order = new TreeMap<>();
        for (PrChange change : PrChange.values()) {
            order.put(change.label, change.order);
        }
        return Map.copyOf(order);
    }

    private static int classificationIndex(String classification) {
        return CLASSIFICATION_ORDER_INDEX.getOrDefault(classification, Integer.MAX_VALUE);
    }

    record Snapshot(
        String scope,
        boolean hasDeltas,
        boolean hasBoundaryExpansion,
        List<String> classifications,
        List<Delta> deltas,
        List<Signal> governanceSignals,
        List<BlockSnapshot> blocks
    ) {
        Snapshot {
            classifications = List.copyOf(classifications);
            deltas = List.copyOf(deltas);
            governanceSignals = List.copyOf(governanceSignals);
            blocks = List.copyOf(blocks);
        }
    }

    record BlockSnapshot(
        String blockId,
        String ir,
        boolean hasDeltas,
        boolean hasBoundaryExpansion,
        List<String> classifications,
        List<Delta> deltas,
        List<Signal> governanceSignals
    ) {
        BlockSnapshot {
            classifications = List.copyOf(classifications);
            deltas = List.copyOf(deltas);
            governanceSignals = List.copyOf(governanceSignals);
        }
    }

    record Delta(String clazz, String category, String change, String key) {
        String deltaId() {
            return clazz + "|" + category + "|" + change + "|" + key;
        }
    }

    record Signal(String type, String path, Map<String, Object> details) {
        Signal {
            details = sortedDetails(details);
        }
    }

    private static Map<String, Object> sortedDetails(Map<String, Object> details) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        if (details != null) {
            sorted.putAll(details);
        }
        return Collections.unmodifiableMap(sorted);
    }
}


