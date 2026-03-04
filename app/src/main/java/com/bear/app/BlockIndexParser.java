package com.bear.app;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class BlockIndexParser {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    BlockIndex parse(Path repoRoot, Path indexPath) throws IOException, BlockIndexValidationException {
        return parse(repoRoot, indexPath, false);
    }

    BlockIndex parse(Path repoRoot, Path indexPath, boolean rejectDuplicateTuple) throws IOException, BlockIndexValidationException {
        String yamlText = Files.readString(indexPath, StandardCharsets.UTF_8);
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(options);
        Object rootObject = yaml.load(yamlText);
        if (!(rootObject instanceof Map<?, ?> root)) {
            throw new BlockIndexValidationException("INDEX_INVALID: root must be a map", "bear.blocks.yaml");
        }

        Object version = root.get("version");
        if (!(version instanceof String versionText) || !"v1".equals(versionText)) {
            throw new BlockIndexValidationException("INDEX_INVALID: version must be v1", "bear.blocks.yaml");
        }

        Object blocksObject = root.get("blocks");
        if (!(blocksObject instanceof List<?> rawBlocks) || rawBlocks.isEmpty()) {
            throw new BlockIndexValidationException("INDEX_INVALID: blocks must be a non-empty list", "bear.blocks.yaml");
        }

        List<BlockIndexEntry> blocks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<String> tuples = new HashSet<>();
        for (int i = 0; i < rawBlocks.size(); i++) {
            Object blockObject = rawBlocks.get(i);
            String blockPath = "bear.blocks.yaml#blocks[" + i + "]";
            if (!(blockObject instanceof Map<?, ?> blockMap)) {
                throw new BlockIndexValidationException("INDEX_INVALID: block entry must be a map", blockPath);
            }
            String name = requireString(blockMap.get("name"), blockPath + ".name");
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new BlockIndexValidationException(
                    "INDEX_INVALID: name must match [a-z][a-z0-9-]*",
                    blockPath + ".name"
                );
            }
            if (!names.add(name)) {
                throw new BlockIndexValidationException("INDEX_INVALID: duplicate block name: " + name, blockPath + ".name");
            }

            String ir = normalizeRepoRelativePath(
                requireString(blockMap.get("ir"), blockPath + ".ir"),
                blockPath + ".ir",
                false
            );
            String projectRoot = normalizeRepoRelativePath(
                requireString(blockMap.get("projectRoot"), blockPath + ".projectRoot"),
                blockPath + ".projectRoot",
                true
            );
            boolean enabled = readEnabled(blockMap.get("enabled"), blockPath + ".enabled");

            ensureUnderRepoRoot(repoRoot, ir, blockPath + ".ir");
            ensureUnderRepoRoot(repoRoot, projectRoot, blockPath + ".projectRoot");
            if (rejectDuplicateTuple) {
                String tupleKey = ir + "|" + repoRoot.resolve(projectRoot).normalize().toString().replace('\\', '/');
                if (!tuples.add(tupleKey)) {
                    throw new BlockIndexValidationException(
                        "INDEX_INVALID: duplicate (ir,projectRoot) tuple: " + ir + ", " + projectRoot,
                        blockPath
                    );
                }
            }
            blocks.add(new BlockIndexEntry(name, ir, projectRoot, enabled));
        }
        return new BlockIndex(versionText, blocks);
    }

    private static boolean readEnabled(Object rawValue, String path) throws BlockIndexValidationException {
        if (rawValue == null) {
            return true;
        }
        if (rawValue instanceof Boolean b) {
            return b;
        }
        throw new BlockIndexValidationException("INDEX_INVALID: enabled must be boolean", path);
    }

    private static String requireString(Object rawValue, String path) throws BlockIndexValidationException {
        if (!(rawValue instanceof String value) || value.isBlank()) {
            throw new BlockIndexValidationException("INDEX_INVALID: required non-empty string", path);
        }
        return value;
    }

    private static String normalizeRepoRelativePath(
        String rawValue,
        String path,
        boolean allowCurrentDirectory
    ) throws BlockIndexValidationException {
        Path normalized = Path.of(rawValue).normalize();
        String normalizedText = normalized.toString().replace('\\', '/');
        if (normalizedText.isBlank()) {
            if (allowCurrentDirectory) {
                return ".";
            }
            throw new BlockIndexValidationException("INDEX_INVALID: path must be repo-relative", path);
        }
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new BlockIndexValidationException("INDEX_INVALID: path must be repo-relative", path);
        }
        return normalizedText;
    }

    private static void ensureUnderRepoRoot(Path repoRoot, String relativePath, String path) throws BlockIndexValidationException {
        Path resolved = repoRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(repoRoot)) {
            throw new BlockIndexValidationException("INDEX_INVALID: path escapes repo root", path);
        }
    }
}
