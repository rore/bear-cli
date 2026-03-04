package com.bear.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

record AgentCommandContext(
    String command,
    String mode,
    String irPath,
    String projectPath,
    String baseRef,
    boolean collectAll,
    boolean agent,
    boolean strictHygiene,
    boolean strictOrphans,
    boolean failFast,
    String indexPath,
    String blocksPath,
    Set<String> onlyNames
) {
    static AgentCommandContext minimal(String command, String mode, String collectMode, boolean agent) {
        return new AgentCommandContext(
            command,
            mode,
            null,
            null,
            null,
            "all".equals(collectMode),
            agent,
            false,
            false,
            false,
            null,
            null,
            Set.of()
        );
    }

    static AgentCommandContext forCheckSingle(
        Path irFile,
        Path projectRoot,
        Path indexPath,
        boolean strictHygiene,
        boolean collectAll,
        boolean agent
    ) {
        return new AgentCommandContext(
            "check",
            "single",
            normalizePathToken(irFile),
            normalizePathToken(projectRoot),
            null,
            collectAll,
            agent,
            strictHygiene,
            false,
            false,
            normalizePathToken(indexPath),
            null,
            Set.of()
        );
    }

    static AgentCommandContext forPrCheckSingle(
        String repoRelativeIr,
        Path projectRoot,
        String baseRef,
        Path indexPath,
        boolean collectAll,
        boolean agent
    ) {
        return new AgentCommandContext(
            "pr-check",
            "single",
            normalizePathToken(repoRelativeIr),
            normalizePathToken(projectRoot),
            baseRef,
            collectAll,
            agent,
            false,
            false,
            false,
            normalizePathToken(indexPath),
            null,
            Set.of()
        );
    }

    static AgentCommandContext forCheckAll(AllCheckOptions options) {
        return new AgentCommandContext(
            "check",
            "all",
            null,
            normalizePathToken(options.repoRoot()),
            null,
            options.collectAll(),
            options.agent(),
            options.strictHygiene(),
            options.strictOrphans(),
            options.failFast(),
            null,
            normalizePathToken(options.blocksPath()),
            normalizeOnlyNames(options.onlyNames())
        );
    }

    static AgentCommandContext forPrCheckAll(AllPrCheckOptions options) {
        return new AgentCommandContext(
            "pr-check",
            "all",
            null,
            normalizePathToken(options.repoRoot()),
            options.baseRef(),
            options.collectAll(),
            options.agent(),
            false,
            options.strictOrphans(),
            false,
            null,
            normalizePathToken(options.blocksPath()),
            normalizeOnlyNames(options.onlyNames())
        );
    }

    String collectMode() {
        return collectAll ? "all" : "first";
    }

    List<String> toCliArgsForRerun() {
        ArrayList<String> args = new ArrayList<>();
        args.add("bear");
        args.add(command);
        if ("all".equals(mode)) {
            args.add("--all");
            if (projectPath != null && !projectPath.isBlank()) {
                args.add("--project");
                args.add(projectPath);
            }
            if ("pr-check".equals(command) && baseRef != null && !baseRef.isBlank()) {
                args.add("--base");
                args.add(baseRef);
            }
            if (blocksPath != null && !blocksPath.isBlank()) {
                args.add("--blocks");
                args.add(blocksPath);
            }
            if (!onlyNames.isEmpty()) {
                args.add("--only");
                args.add(String.join(",", onlyNames.stream().sorted(Comparator.naturalOrder()).toList()));
            }
            if (failFast) {
                args.add("--fail-fast");
            }
            if (strictOrphans) {
                args.add("--strict-orphans");
            }
            if (strictHygiene) {
                args.add("--strict-hygiene");
            }
        } else {
            if (irPath != null && !irPath.isBlank()) {
                args.add(irPath);
            }
            if (projectPath != null && !projectPath.isBlank()) {
                args.add("--project");
                args.add(projectPath);
            }
            if ("pr-check".equals(command) && baseRef != null && !baseRef.isBlank()) {
                args.add("--base");
                args.add(baseRef);
            }
            if (indexPath != null && !indexPath.isBlank()) {
                args.add("--index");
                args.add(indexPath);
            }
            if (strictHygiene) {
                args.add("--strict-hygiene");
            }
        }
        if (collectAll) {
            args.add("--collect=all");
        }
        if (agent) {
            args.add("--agent");
        }
        return List.copyOf(args);
    }

    String rerunCommand() {
        return String.join(" ", toCliArgsForRerun());
    }

    private static Set<String> normalizeOnlyNames(Set<String> onlyNames) {
        if (onlyNames == null || onlyNames.isEmpty()) {
            return Set.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String name : onlyNames) {
            if (name != null && !name.isBlank()) {
                normalized.add(name);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalizePathToken(Path path) {
        if (path == null) {
            return null;
        }
        return normalizePathToken(path.toString());
    }

    private static String normalizePathToken(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.replace('\\', '/');
    }
}
