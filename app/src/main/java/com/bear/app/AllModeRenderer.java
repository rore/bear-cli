package com.bear.app;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

final class AllModeRenderer {
    private AllModeRenderer() {
    }

    static List<String> renderCheckAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.status() == BlockStatus.FAIL) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.PASS && result.detail() != null && !result.detail().isBlank()) {
                lines.add("DETAIL: " + result.detail());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("ROOT_REACH_FAILED: " + summary.rootReachFailed());
        lines.add("ROOT_TEST_FAILED: " + summary.rootTestFailed());
        lines.add("ROOT_TEST_SKIPPED_DUE_TO_REACH: " + summary.rootTestSkippedDueToReach());
        lines.add("FAIL_FAST_TRIGGERED: " + summary.failFastTriggered());
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }

    static List<String> renderPrAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        int boundaryCount = 0;
        TreeSet<String> governanceSignals = new TreeSet<>();
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.classification() != null) {
                lines.add("CLASSIFICATION: " + result.classification());
                if ("BOUNDARY_EXPANDING".equals(result.classification())) {
                    boundaryCount++;
                }
            }
            if (!result.deltaLines().isEmpty()) {
                lines.add("DELTA:");
                for (String deltaLine : result.deltaLines()) {
                    lines.add("  " + deltaLine);
                }
            } else {
                lines.add("DELTA: (no changes)");
            }
            governanceSignals.addAll(result.governanceLines());
            if (result.status() == BlockStatus.FAIL && result.blockCode() != null) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        if (summary.exitCode() == 0 && !governanceSignals.isEmpty()) {
            lines.add("GOVERNANCE SIGNALS:");
            for (String line : governanceSignals) {
                lines.add("  " + line);
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("BOUNDARY_EXPANDING: " + boundaryCount);
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }

    static List<String> renderFixAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.status() == BlockStatus.FAIL) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("FAIL_FAST_TRIGGERED: " + summary.failFastTriggered());
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }

    static List<String> renderCompileAllOutput(List<BlockExecutionResult> results, RepoAggregationResult summary) {
        List<String> lines = new ArrayList<>();
        for (BlockExecutionResult result : results) {
            lines.add("BLOCK: " + result.name());
            lines.add("IR: " + result.ir());
            lines.add("PROJECT: " + result.project());
            lines.add("STATUS: " + result.status());
            lines.add("EXIT_CODE: " + result.exitCode());
            if (result.status() == BlockStatus.FAIL) {
                lines.add("CATEGORY: " + result.category());
                lines.add("BLOCK_CODE: " + result.blockCode());
                lines.add("BLOCK_PATH: " + result.blockPath());
                lines.add("DETAIL: " + result.detail());
                lines.add("BLOCK_REMEDIATION: " + result.blockRemediation());
            } else if (result.status() == BlockStatus.SKIP) {
                lines.add("REASON: " + result.reason());
            }
            lines.add("");
        }
        lines.add("SUMMARY:");
        lines.add(summary.total() + " blocks total");
        lines.add(summary.checked() + " checked");
        lines.add(summary.passed() + " passed");
        lines.add(summary.failed() + " failed");
        lines.add(summary.skipped() + " skipped");
        lines.add("FAIL_FAST_TRIGGERED: " + summary.failFastTriggered());
        lines.add("EXIT_CODE: " + summary.exitCode());
        return lines;
    }
}
