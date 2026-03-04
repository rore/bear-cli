package com.bear.app;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrValidationException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class BlockPortGraphResolver {
    private static final IrPipeline IR_PIPELINE = new DefaultIrPipeline();

    private BlockPortGraphResolver() {
    }

    static boolean hasBlockPortEffects(BearIr ir) {
        for (BearIr.EffectPort port : ir.block().effects().allow()) {
            BearIr.EffectPortKind kind = port.kind() == null ? BearIr.EffectPortKind.EXTERNAL : port.kind();
            if (kind == BearIr.EffectPortKind.BLOCK) {
                return true;
            }
        }
        return false;
    }

    static BlockPortGraph resolveAndValidate(Path repoRoot, Path indexPath)
        throws IOException, BlockIndexValidationException, BearIrValidationException, BlockIdentityResolutionException {
        BlockIndex index = new BlockIndexParser().parse(repoRoot, indexPath, true);

        TreeMap<String, BearIr> irByBlockKey = new TreeMap<>();
        TreeMap<String, Set<String>> opNamesByBlockKey = new TreeMap<>();

        for (BlockIndexEntry entry : index.blocks()) {
            Path irPath = repoRoot.resolve(entry.ir()).normalize();
            BearIr ir = IR_PIPELINE.parseValidateNormalize(irPath);
            BlockIdentityResolver.resolveIndexIdentity(entry.name(), BlockIdentityResolver.formatIndexLocator(entry), ir.block().name());

            irByBlockKey.put(entry.name(), ir);
            TreeSet<String> opNames = new TreeSet<>();
            for (BearIr.Operation operation : ir.block().operations()) {
                opNames.add(operation.name());
            }
            opNamesByBlockKey.put(entry.name(), Set.copyOf(opNames));
        }

        ArrayList<BlockPortEdge> edges = new ArrayList<>();
        TreeMap<String, TreeSet<String>> adjacency = new TreeMap<>();
        for (BlockIndexEntry entry : index.blocks()) {
            String sourceBlockKey = entry.name();
            BearIr ir = irByBlockKey.get(sourceBlockKey);
            for (int i = 0; i < ir.block().effects().allow().size(); i++) {
                BearIr.EffectPort port = ir.block().effects().allow().get(i);
                BearIr.EffectPortKind kind = port.kind() == null ? BearIr.EffectPortKind.EXTERNAL : port.kind();
                if (kind != BearIr.EffectPortKind.BLOCK) {
                    continue;
                }
                String targetBlockKey = port.targetBlock();
                if (!irByBlockKey.containsKey(targetBlockKey)) {
                    throw new BlockIndexValidationException(
                        "BLOCK_PORT_TARGET_NOT_FOUND: source=" + sourceBlockKey + ",port=" + port.port() + ",targetBlock=" + targetBlockKey,
                        entry.ir()
                    );
                }
                Set<String> targetOps = opNamesByBlockKey.get(targetBlockKey);
                for (int j = 0; j < port.targetOps().size(); j++) {
                    String op = port.targetOps().get(j);
                    if (!targetOps.contains(op)) {
                        throw new BlockIndexValidationException(
                            "BLOCK_PORT_TARGET_OP_NOT_FOUND: source=" + sourceBlockKey
                                + ",port=" + port.port()
                                + ",targetBlock=" + targetBlockKey
                                + ",targetOp=" + op,
                            entry.ir() + "#block.effects.allow[" + i + "].targetOps[" + j + "]"
                        );
                    }
                }
                ArrayList<String> sortedTargetOps = new ArrayList<>(port.targetOps());
                sortedTargetOps.sort(String::compareTo);
                List<String> frozenTargetOps = List.copyOf(sortedTargetOps);
                edges.add(new BlockPortEdge(sourceBlockKey, port.port(), targetBlockKey, frozenTargetOps));
                adjacency.computeIfAbsent(sourceBlockKey, ignored -> new TreeSet<>()).add(targetBlockKey);
                adjacency.computeIfAbsent(targetBlockKey, ignored -> new TreeSet<>());
            }
            adjacency.computeIfAbsent(sourceBlockKey, ignored -> new TreeSet<>());
        }

        String cycle = firstCanonicalCycle(adjacency);
        if (cycle != null) {
            throw new BlockIndexValidationException(
                "BLOCK_PORT_CYCLE_DETECTED: cycle=" + cycle,
                "bear.blocks.yaml"
            );
        }

        edges.sort(
            Comparator.comparing(BlockPortEdge::sourceBlockKey)
                .thenComparing(BlockPortEdge::port)
                .thenComparing(BlockPortEdge::targetBlockKey)
                .thenComparing(edge -> String.join(",", edge.targetOps()))
        );

        return new BlockPortGraph(index, Map.copyOf(irByBlockKey), List.copyOf(edges));
    }

    static TreeSet<String> inboundTargetWrapperFqcns(BlockPortGraph graph) {
        TreeSet<String> wrappers = new TreeSet<>();
        for (BlockPortEdge edge : graph.edges()) {
            for (String op : edge.targetOps()) {
                wrappers.add(wrapperFqcn(edge.targetBlockKey(), op));
            }
        }
        return wrappers;
    }

    static String wrapperFqcn(String blockKey, String operationName) {
        String packageSegment = sanitizePackageSegment(blockKey);
        String blockName = toPascalCase(blockKey);
        String opName = toPascalCase(operationName);
        return "com.bear.generated." + packageSegment + "." + blockName + "_" + opName;
    }

    private static String sanitizePackageSegment(String raw) {
        List<String> tokens = com.bear.kernel.identity.BlockIdentityCanonicalizer.canonicalTokens(raw);
        if (tokens.isEmpty()) {
            return "block";
        }
        return String.join(".", tokens);
    }

    private static String toPascalCase(String raw) {
        List<String> tokens = com.bear.kernel.identity.BlockIdentityCanonicalizer.canonicalTokens(raw);
        if (tokens.isEmpty()) {
            return "Block";
        }
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            out.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
        }
        return out.toString();
    }

    private static String firstCanonicalCycle(Map<String, TreeSet<String>> adjacency) {
        HashSet<String> visited = new HashSet<>();
        HashSet<String> stackSet = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();

        ArrayList<String> sortedNodes = new ArrayList<>(adjacency.keySet());
        sortedNodes.sort(String::compareTo);
        for (String node : sortedNodes) {
            String cycle = dfsCycle(node, adjacency, visited, stackSet, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private static String dfsCycle(
        String node,
        Map<String, TreeSet<String>> adjacency,
        Set<String> visited,
        Set<String> stackSet,
        ArrayDeque<String> stack
    ) {
        if (stackSet.contains(node)) {
            ArrayList<String> cycle = new ArrayList<>();
            boolean capture = false;
            for (String current : stack) {
                if (current.equals(node)) {
                    capture = true;
                }
                if (capture) {
                    cycle.add(current);
                }
            }
            if (cycle.isEmpty()) {
                cycle.add(node);
            }
            return canonicalCycle(cycle);
        }
        if (!visited.add(node)) {
            return null;
        }

        stack.addLast(node);
        stackSet.add(node);
        TreeSet<String> neighbors = adjacency.getOrDefault(node, new TreeSet<>());
        for (String next : neighbors) {
            String cycle = dfsCycle(next, adjacency, visited, stackSet, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        stack.removeLast();
        stackSet.remove(node);
        return null;
    }

    private static String canonicalCycle(List<String> cycleNodes) {
        if (cycleNodes.size() == 1) {
            String only = cycleNodes.get(0);
            return only + "->" + only;
        }
        String best = null;
        int n = cycleNodes.size();
        for (int start = 0; start < n; start++) {
            StringBuilder candidate = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    candidate.append("->");
                }
                candidate.append(cycleNodes.get((start + i) % n));
            }
            candidate.append("->").append(cycleNodes.get(start));
            String value = candidate.toString();
            if (best == null || value.compareTo(best) < 0) {
                best = value;
            }
        }
        return best;
    }
}

record BlockPortGraph(
    BlockIndex index,
    Map<String, BearIr> irByBlockKey,
    List<BlockPortEdge> edges
) {
}

record BlockPortEdge(
    String sourceBlockKey,
    String port,
    String targetBlockKey,
    List<String> targetOps
) {
}