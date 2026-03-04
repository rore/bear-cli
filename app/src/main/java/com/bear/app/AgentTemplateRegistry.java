package com.bear.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class AgentTemplateRegistry {
    private static final Map<String, Template> EXACT = new HashMap<>();
    private static final Map<String, Template> FAILURE_DEFAULTS = new HashMap<>();

    static {
        registerExact(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, "DIRECT_IMPL_USAGE", template(
            "Fix boundary bypass: remove direct impl seam usage",
            List.of(
                "Remove direct impl/service/module binding usage from governed source lanes.",
                "Route calls through generated wrappers and declared ports.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#boundary_bypass")
        ));
        registerExact(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, "IMPL_CONTAINMENT_BYPASS", template(
            "Fix containment bypass in governed impl",
            List.of(
                "Move or refactor cross-call targets to governed roots for the block.",
                "Remove execute(...) references to non-governed source paths.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#boundary_bypass")
        ));
        registerExact(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.UNDECLARED_REACH, "UNDECLARED_REACH", template(
            "Fix undeclared reach",
            List.of(
                "Declare required port/op in IR where boundary expansion is intended.",
                "Run `bear compile` for updated generated artifacts.",
                "Route calls through generated port interfaces and re-run: {rerunCommand}"
            ),
            List.of("bear compile", "{rerunCommand}"),
            List.of("troubleshooting#undeclared_reach")
        ));
        registerExact(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.REFLECTION_DISPATCH_FORBIDDEN, "REFLECTION_DISPATCH_FORBIDDEN", template(
            "Remove reflection dispatch from governed roots",
            List.of(
                "Remove reflection/method-handle dynamic dispatch from governed source paths.",
                "Route behavior through declared generated boundaries.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#undeclared_reach")
        ));

        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.DRIFT_MISSING_BASELINE, CliCodes.DRIFT_MISSING_BASELINE, template(
            "Regenerate missing baseline artifacts",
            List.of(
                "Run `bear compile` for the target block(s) to regenerate BEAR-owned artifacts.",
                "Re-run: {rerunCommand}"
            ),
            List.of("bear compile", "{rerunCommand}"),
            List.of("troubleshooting#drift_detected-or-drift_missing_baseline")
        ));
        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, "PROJECT_TEST_LOCK", template(
            "Clear blocked check marker and retry",
            List.of(
                "Run `bear unblock` to clear stale blocked marker.",
                "Re-run: {rerunCommand}"
            ),
            List.of("bear unblock", "{rerunCommand}"),
            List.of("troubleshooting#io_error")
        ));
        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, "PROJECT_TEST_BOOTSTRAP", template(
            "Recover from project test bootstrap IO",
            List.of(
                "Run `bear unblock` to clear stale blocked marker.",
                "Verify project wrapper/cache accessibility.",
                "Re-run: {rerunCommand}"
            ),
            List.of("bear unblock", "{rerunCommand}"),
            List.of("troubleshooting#io_error")
        ));
        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_GIT, "MERGE_BASE_FAILED", template(
            "Capture base-resolution diagnostics and escalate",
            List.of(
                "Ensure the configured base reference exists in CI/local git context.",
                "Re-run: {rerunCommand}",
                "If merge-base still fails, escalate with CODE/PATH diagnostics and base-ref evidence."
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#io_git")
        ));
        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_GIT, "NOT_A_GIT_REPO", template(
            "Run PR check from a git work tree",
            List.of(
                "Ensure command runs from a git work tree with accessible repository metadata.",
                "Re-run: {rerunCommand}",
                "If issue persists, escalate with CODE/PATH diagnostics."
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#io_git")
        ));
        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, "READ_HEAD_FAILED", template(
            "Capture head IR read diagnostics and escalate",
            List.of(
                "Ensure the IR path exists at HEAD in the current repository state.",
                "Re-run: {rerunCommand}",
                "If issue persists, escalate with CODE/PATH diagnostics and failing IR locator."
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#io_git")
        ));
        registerExact(AgentDiagnostics.AgentCategory.INFRA, CliCodes.MANIFEST_INVALID, CliCodes.MANIFEST_INVALID, template(
            "Regenerate invalid wiring manifest",
            List.of(
                "Run `bear compile` to regenerate wiring metadata.",
                "Re-run: {rerunCommand}"
            ),
            List.of("bear compile", "{rerunCommand}"),
            List.of("troubleshooting#ir_validation")
        ));

        registerFailureDefault(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, template(
            "Fix boundary bypass policy violations",
            List.of(
                "Apply rule-specific remediation under governed roots.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#boundary_bypass")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_EXPANSION, template(
            "Review explicit boundary expansion",
            List.of(
                "Review boundary expansion report items and confirm intent.",
                "If intentional, record required justification per governance process.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#boundary_expansion")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IR_VALIDATION, template(
            "Fix validation error",
            List.of(
                "Fix the reported validation issue at the indicated path.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#ir_validation")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.POLICY_INVALID, template(
            "Fix policy contract file",
            List.of(
                "Correct policy schema/content in the reported policy file.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#ir_validation")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.DRIFT_DETECTED, template(
            "Regenerate drifted generated artifacts",
            List.of(
                "Run `bear compile` for the relevant block(s).",
                "Re-run: {rerunCommand}"
            ),
            List.of("bear compile", "{rerunCommand}"),
            List.of("troubleshooting#drift_detected-or-drift_missing_baseline")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_ERROR, template(
            "Resolve deterministic IO failure",
            List.of(
                "Address the reported BEAR IO locator/path issue.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#io_error")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.IO_GIT, template(
            "Resolve deterministic git failure",
            List.of(
                "Fix base-ref/repository access issue reported by BEAR.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#io_git")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.TEST_FAILURE, template(
            "Fix failing project tests",
            List.of(
                "Fix project test failures in the reported scope.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#test_failure-test_timeout-or-invariant_violation")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.COMPILE_FAILURE, template(
            "Fix compile preflight failures",
            List.of(
                "Resolve compile errors in project sources.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#test_failure-test_timeout-or-invariant_violation")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.TEST_TIMEOUT, template(
            "Resolve test timeout",
            List.of(
                "Address long-running or blocked tests in reported scope.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#test_failure-test_timeout-or-invariant_violation")
        ));
        registerFailureDefault(AgentDiagnostics.AgentCategory.INFRA, CliCodes.INVARIANT_VIOLATION, template(
            "Fix invariant violation",
            List.of(
                "Adjust implementation/fixtures to satisfy declared invariant checks.",
                "Re-run: {rerunCommand}"
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting#test_failure-test_timeout-or-invariant_violation")
        ));
    }

    private AgentTemplateRegistry() {
    }

    static AgentDiagnostics.AgentNextAction render(
        AgentCommandContext commandContext,
        AgentDiagnostics.AgentCluster cluster
    ) {
        Template template = resolve(cluster);
        String rerunCommand = buildRerunCommand(commandContext);
        ArrayList<String> renderedSteps = new ArrayList<>(template.steps().size());
        for (String step : template.steps()) {
            renderedSteps.add(interpolate(step, rerunCommand));
        }
        ArrayList<String> renderedCommands = new ArrayList<>(template.commands().size());
        for (String commandTemplate : template.commands()) {
            renderedCommands.add(interpolate(commandTemplate, rerunCommand));
        }
        return new AgentDiagnostics.AgentNextAction(
            cluster.category().name(),
            cluster.clusterId(),
            template.title(),
            List.copyOf(renderedSteps),
            List.copyOf(renderedCommands),
            template.links()
        );
    }

    private static String buildRerunCommand(AgentCommandContext commandContext) {
        return commandContext.rerunCommand();
    }

    private static String interpolate(String template, String rerunCommand) {
        return template.replace("{rerunCommand}", rerunCommand);
    }

    private static Template resolve(AgentDiagnostics.AgentCluster cluster) {
        String qualifier = cluster.ruleId() != null ? cluster.ruleId() : cluster.reasonKey();
        String exactKey = key(cluster.category(), cluster.failureCode(), qualifier);
        Template exact = EXACT.get(exactKey);
        if (exact != null) {
            return exact;
        }
        Template failure = FAILURE_DEFAULTS.get(key(cluster.category(), cluster.failureCode(), ""));
        if (failure != null) {
            return failure;
        }
        if (cluster.category() == AgentDiagnostics.AgentCategory.GOVERNANCE) {
            return template(
                "Resolve governance failure",
                List.of(
                    "Apply deterministic rule remediation for the reported governance failure.",
                    "Re-run: {rerunCommand}"
                ),
                List.of("{rerunCommand}"),
                List.of("troubleshooting#boundary_bypass")
            );
        }
        return template(
            "Capture deterministic diagnostics and escalate",
            List.of(
                "Re-run BEAR command and capture CODE/PATH/REMEDIATION diagnostics.",
                "If issue persists, file issue with captured deterministic failure details."
            ),
            List.of("{rerunCommand}"),
            List.of("troubleshooting")
        );
    }

    static Set<String> exactInfraQualifiers() {
        TreeSet<String> qualifiers = new TreeSet<>();
        for (String key : EXACT.keySet()) {
            String[] parts = key.split("\\|", 3);
            if (parts.length == 3 && "INFRA".equals(parts[0]) && !parts[2].equals(parts[1])) {
                qualifiers.add(parts[2]);
            }
        }
        return Set.copyOf(qualifiers);
    }
    private static void registerExact(
        AgentDiagnostics.AgentCategory category,
        String failureCode,
        String qualifier,
        Template template
    ) {
        EXACT.put(key(category, failureCode, qualifier), template);
    }

    private static void registerFailureDefault(
        AgentDiagnostics.AgentCategory category,
        String failureCode,
        Template template
    ) {
        FAILURE_DEFAULTS.put(key(category, failureCode, ""), template);
    }

    private static String key(AgentDiagnostics.AgentCategory category, String failureCode, String qualifier) {
        return category.name() + "|" + failureCode + "|" + (qualifier == null ? "" : qualifier);
    }

    private static Template template(String title, List<String> steps, List<String> commands, List<String> links) {
        return new Template(title, List.copyOf(steps), List.copyOf(commands), List.copyOf(links));
    }

    private record Template(
        String title,
        List<String> steps,
        List<String> commands,
        List<String> links
    ) {
    }
}
