package com.bear.app;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BearCiIntegrationScriptsTest {
    @Test
    void powerShellWrapperUsesPullRequestBaseShaAndWritesPassReport(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");
        writePrFixture(repoRoot, CliCodes.EXIT_OK, prJson(CliCodes.EXIT_OK, PrGovernanceTelemetry.all(CliCodes.EXIT_OK, List.of(), List.of(), List.of())), "");

        Path eventPath = repoRoot.resolve("event.json");
        Files.writeString(eventPath, "{\"pull_request\":{\"base\":{\"sha\":\"base-sha-123\"}}}", StandardCharsets.UTF_8);
        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of("GITHUB_EVENT_PATH", eventPath.toString()), "--mode", "enforce");

        assertEquals(0, run.exitCode());
        assertTrue(run.stdout().contains("MODE=enforce DECISION=pass BASE=base-sha-123"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"resolvedBaseSha\":\"base-sha-123\""), report);
        assertTrue(report.contains("\"decision\":\"pass\""), report);
        assertTrue(report.contains("\"prCheck\":{\"status\":\"ran\""), report);
        assertTrue(report.contains("\"commands\":[\"" + expectedBearCommandPrefix() + " check --all --project . --blocks bear.blocks.yaml --collect=all --agent\",\"" + expectedBearCommandPrefix() + " pr-check --all --project . --base base-sha-123 --blocks bear.blocks.yaml --collect=all --agent\"]"), report);
    }

    @Test
    void powerShellWrapperObserveRunsPrCheckAfterCheckGovernanceExit(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_DRIFT, checkJson(CliCodes.EXIT_DRIFT), footer(CliCodes.DRIFT_DETECTED, "build/generated/bear", "Refresh generated baseline and rerun."));
        writePrFixture(repoRoot, CliCodes.EXIT_OK, prJson(CliCodes.EXIT_OK, PrGovernanceTelemetry.all(CliCodes.EXIT_OK, List.of(), List.of(), List.of())), "");

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "observe", "--base-sha", "base-sha-200");

        assertEquals(0, run.exitCode());
        assertTrue(run.stdout().contains("CHECK exit=3 code=DRIFT_DETECTED classes=CI_GOVERNANCE_DRIFT"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"decision\":\"pass\""), report);
        assertTrue(report.contains("\"check\":{\"status\":\"ran\",\"exitCode\":3,\"code\":\"DRIFT_DETECTED\""), report);
        assertTrue(report.contains("\"classes\":[\"CI_GOVERNANCE_DRIFT\"]"), report);
        assertTrue(report.contains("\"prCheck\":{\"status\":\"ran\""), report);
    }


    @Test
    void powerShellWrapperUsesAgentJsonWhenFailureFooterIsMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(
            repoRoot,
            CliCodes.EXIT_DRIFT,
            AgentDiagnostics.toJson(AgentDiagnostics.payload(
                AgentCommandContext.minimal("check", "all", "all", true),
                CliCodes.EXIT_DRIFT,
                List.of(problem(AgentDiagnostics.AgentCategory.INFRA, CliCodes.DRIFT_MISSING_BASELINE, null, CliCodes.DRIFT_MISSING_BASELINE, "build/generated/bear")),
                true
            )),
            ""
        );
        writePrFixture(
            repoRoot,
            CliCodes.EXIT_BOUNDARY_BYPASS,
            AgentDiagnostics.toJson(AgentDiagnostics.payload(
                AgentCommandContext.minimal("pr-check", "all", "all", true),
                CliCodes.EXIT_BOUNDARY_BYPASS,
                List.of(problem(AgentDiagnostics.AgentCategory.GOVERNANCE, CliCodes.BOUNDARY_BYPASS, "BLOCK_PORT_IMPL_INVALID", null, "build/generated/bear/src/main/java")),
                true
            )),
            ""
        );

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "observe", "--base-sha", "base-sha-agent-json");

        assertEquals(0, run.exitCode(), run.stdout());
        assertTrue(run.stdout().contains("CHECK exit=3 code=DRIFT_MISSING_BASELINE classes=CI_GOVERNANCE_DRIFT"), run.stdout());
        assertTrue(run.stdout().contains("PR-CHECK exit=7 code=BLOCK_PORT_IMPL_INVALID classes=CI_POLICY_BYPASS_ATTEMPT"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"decision\":\"pass\""), report);
        assertFalse(report.contains("WRAPPER_FOOTER_INVALID"), report);
    }

    @Test
    void powerShellWrapperObserveFailsWhenFooterAndAgentJsonAreBothUnusable(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_DRIFT, "not-json", "");
        writePrFixture(repoRoot, CliCodes.EXIT_OK, prJson(CliCodes.EXIT_OK, PrGovernanceTelemetry.all(CliCodes.EXIT_OK, List.of(), List.of(), List.of())), "");

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "observe", "--base-sha", "base-sha-invalid-footer");

        assertEquals(1, run.exitCode(), run.stdout());
        assertTrue(run.stdout().contains("CHECK exit=3 code=WRAPPER_FOOTER_INVALID classes=CI_INTERNAL_ERROR"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"decision\":\"fail\""), report);
    }

    @Test
    void powerShellWrapperEnforceAllowsExactBoundaryExpansionMatch(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");
        PrGovernanceTelemetry.Snapshot snapshot = PrGovernanceTelemetry.all(
            CliCodes.EXIT_BOUNDARY_EXPANSION,
            List.of(new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.ALLOWED_DEPS, PrChange.ADDED, ".:_shared:com.example:demo@1.0.0")),
            List.of(),
            List.of()
        );
        writePrFixture(repoRoot, CliCodes.EXIT_BOUNDARY_EXPANSION, prJson(CliCodes.EXIT_BOUNDARY_EXPANSION, snapshot), footer(CliCodes.BOUNDARY_EXPANSION, "bear.blocks.yaml", "Review boundary-expanding deltas and route through explicit boundary review."));
        Files.writeString(
            repoRoot.resolve(".bear/ci/baseline-allow.json"),
            "{\"schemaVersion\":\"bear.ci.allow.v1\",\"entries\":[{\"baseSha\":\"base-sha-allow\",\"deltaIds\":[\"BOUNDARY_EXPANDING|ALLOWED_DEPS|ADDED|.:_shared:com.example:demo@1.0.0\"]}]}",
            StandardCharsets.UTF_8
        );

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "enforce", "--base-sha", "base-sha-allow");

        assertEquals(0, run.exitCode());
        assertTrue(run.stdout().contains("MODE=enforce DECISION=allowed-expansion BASE=base-sha-allow"), run.stdout());
        assertTrue(run.stdout().contains("ALLOW_ENTRY_CANDIDATE:"), run.stdout());
        assertTrue(run.stdout().contains("{\"baseSha\":\"base-sha-allow\",\"deltaIds\":[\"BOUNDARY_EXPANDING|ALLOWED_DEPS|ADDED|.:_shared:com.example:demo@1.0.0\"]}"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"decision\":\"allowed-expansion\""), report);
        assertTrue(report.contains("\"allowEvaluation\":{\"status\":\"matched\""), report);
        assertTrue(report.contains("\"classes\":[\"CI_BOUNDARY_EXPANSION\",\"CI_DEPENDENCY_POWER_EXPANSION\"]"), report);
        assertTrue(report.contains("\"allowEntryCandidate\":{\"baseSha\":\"base-sha-allow\",\"deltaIds\":[\"BOUNDARY_EXPANDING|ALLOWED_DEPS|ADDED|.:_shared:com.example:demo@1.0.0\"]}"), report);
        String summary = readSummary(repoRoot);
        assertTrue(summary.contains("## Boundary Deltas"), summary);
        assertTrue(summary.contains("## Allow Entry Candidate"), summary);
        assertTrue(summary.contains("\"baseSha\": \"base-sha-allow\""), summary);
    }

    @Test
    void powerShellWrapperFailsClosedWhenBoundaryTelemetryMissing(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");
        writePrFixture(
            repoRoot,
            CliCodes.EXIT_BOUNDARY_EXPANSION,
            AgentDiagnostics.toJson(AgentDiagnostics.payload(
                AgentCommandContext.minimal("pr-check", "all", "all", true),
                CliCodes.EXIT_BOUNDARY_EXPANSION,
                List.of(),
                true
            )),
            footer(CliCodes.BOUNDARY_EXPANSION, "bear.blocks.yaml", "Review boundary-expanding deltas and route through explicit boundary review.")
        );

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "enforce", "--base-sha", "base-sha-missing");

        assertEquals(1, run.exitCode());
        assertTrue(run.stdout().contains("ALLOW_ENTRY_CANDIDATE: UNAVAILABLE"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"decision\":\"fail\""), report);
        assertTrue(report.contains("\"allowEvaluation\":{\"status\":\"unavailable\",\"reason\":\"PR_GOVERNANCE_UNAVAILABLE\""), report);
        assertTrue(report.contains("\"classes\":[\"CI_BOUNDARY_EXPANSION\"]"), report);
        assertTrue(report.contains("\"allowEntryCandidate\":null"), report);
        String summary = readSummary(repoRoot);
        assertTrue(summary.contains("## Allow Entry Candidate"), summary);
        assertTrue(summary.contains("Unavailable: PR governance telemetry was unusable"), summary);
    }

    @Test
    void powerShellWrapperMarksPrCheckNotRunWhenBaseUnresolved(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "enforce");

        assertEquals(1, run.exitCode());
        assertTrue(run.stdout().contains("PR-CHECK NOT_RUN: BASE_UNRESOLVED"), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"prCheck\":{\"status\":\"not-run\",\"reason\":\"BASE_UNRESOLVED\""), report);
        assertTrue(report.contains("\"resolvedBaseSha\":null"), report);
    }

    @Test
    void powerShellWrapperReportIsByteStableAcrossIdenticalInputs(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");
        writePrFixture(repoRoot, CliCodes.EXIT_OK, prJson(CliCodes.EXIT_OK, PrGovernanceTelemetry.all(CliCodes.EXIT_OK, List.of(), List.of(), List.of())), "");

        assertEquals(0, runPowerShellWrapper(repoRoot, Map.of(), "--mode", "observe", "--base-sha", "base-sha-stable").exitCode());
        String first = readReport(repoRoot);
        String firstSummary = readSummary(repoRoot);
        assertEquals(0, runPowerShellWrapper(repoRoot, Map.of(), "--mode", "observe", "--base-sha", "base-sha-stable").exitCode());
        String second = readReport(repoRoot);
        String secondSummary = readSummary(repoRoot);

        assertEquals(first, second);
        assertEquals(firstSummary, secondSummary);
    }

    @Test
    void bashWrapperForwardsToPowerShellAndHandlesSpacesInPath(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("demo repo"));
        installFakePwsh(repoRoot);

        ScriptRunResult run = runBashWrapper(repoRoot, Map.of("PATH_PREFIX", toWslPath(repoRoot.resolve("test-bin"))), "--mode", "observe", "--base-sha", "base-sha-bash");

        assertEquals(0, run.exitCode(), run.stderr());
        assertTrue(run.stderr().isBlank(), run.stderr());
    }
    @Test
    void powerShellWrapperBuildsAllowEntryCandidateFromAllModeBlockBoundaryDeltas(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");
        PrGovernanceTelemetry.Snapshot snapshot = PrGovernanceTelemetry.all(
            CliCodes.EXIT_BOUNDARY_EXPANSION,
            List.of(),
            List.of(),
            List.of(
                PrGovernanceTelemetry.block(
                    "accounts",
                    "spec/accounts.bear.yaml",
                    CliCodes.EXIT_BOUNDARY_EXPANSION,
                    List.of(
                        new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.CONTRACT, PrChange.ADDED, "accounts#create"),
                        new PrDelta(PrClass.BOUNDARY_EXPANDING, PrCategory.PORTS, PrChange.CHANGED, "accounts#port")
                    ),
                    List.of()
                )
            )
        );
        writePrFixture(repoRoot, CliCodes.EXIT_BOUNDARY_EXPANSION, prJson(CliCodes.EXIT_BOUNDARY_EXPANSION, snapshot), footer(CliCodes.BOUNDARY_EXPANSION, "bear.blocks.yaml", "Review boundary-expanding deltas and route through explicit boundary review."));
        Files.writeString(
            repoRoot.resolve(".bear/ci/baseline-allow.json"),
            "{\"schemaVersion\":\"bear.ci.allow.v1\",\"entries\":[{\"baseSha\":\"base-sha-blocks\",\"deltaIds\":[\"BOUNDARY_EXPANDING|CONTRACT|ADDED|accounts#create\",\"BOUNDARY_EXPANDING|PORTS|CHANGED|accounts#port\"]}]}",
            StandardCharsets.UTF_8
        );

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of(), "--mode", "enforce", "--base-sha", "base-sha-blocks");

        assertEquals(0, run.exitCode(), run.stdout());
        String report = readReport(repoRoot);
        assertTrue(report.contains("\"allowEntryCandidate\":{\"baseSha\":\"base-sha-blocks\",\"deltaIds\":[\"BOUNDARY_EXPANDING|CONTRACT|ADDED|accounts#create\",\"BOUNDARY_EXPANDING|PORTS|CHANGED|accounts#port\"]}"), report);
        String summary = readSummary(repoRoot);
        assertTrue(summary.contains("## Boundary Deltas"), summary);
        assertTrue(summary.contains("`BOUNDARY_EXPANDING | CONTRACT | ADDED | accounts#create`"), summary);
        assertTrue(summary.contains("`BOUNDARY_EXPANDING | PORTS | CHANGED | accounts#port`"), summary);
    }

    @Test
    void powerShellWrapperWritesMarkdownSummaryAndAppendsGithubStepSummary(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));
        writeCheckFixture(repoRoot, CliCodes.EXIT_OK, checkJson(CliCodes.EXIT_OK), "");
        writePrFixture(repoRoot, CliCodes.EXIT_OK, prJson(CliCodes.EXIT_OK, PrGovernanceTelemetry.all(CliCodes.EXIT_OK, List.of(), List.of(), List.of())), "");
        Path stepSummary = tempDir.resolve("step-summary.md");
        Files.writeString(stepSummary, "", StandardCharsets.UTF_8);

        ScriptRunResult run = runPowerShellWrapper(repoRoot, Map.of("GITHUB_STEP_SUMMARY", stepSummary.toString()), "--mode", "observe", "--base-sha", "base-sha-summary");

        assertEquals(0, run.exitCode(), run.stderr());
        String summary = readSummary(repoRoot);
        assertTrue(summary.contains("# BEAR CI Governance"), summary);
        assertTrue(summary.contains("- Mode: observe"), summary);
        assertTrue(summary.contains("- Decision: pass"), summary);
        assertTrue(summary.contains("- Base SHA: base-sha-summary"), summary);
        assertFalse(summary.contains("## Allow Entry Candidate"), summary);
        assertEquals(summary, Files.readString(stepSummary, StandardCharsets.UTF_8));
    }

    @Test
    void bashWrapperFailsWithPwshRemediationWhenUnavailable(@TempDir Path tempDir) throws Exception {
        Path repoRoot = createFixtureRepo(tempDir.resolve("repo"));

        ScriptRunResult run = runBashWrapper(repoRoot, Map.of("PATH_PREFIX", toWslPath(repoRoot.resolve("missing-bin")), "PATH", ""), "--mode", "observe", "--base-sha", "base-sha-bash");

        assertEquals(1, run.exitCode());
        assertTrue(run.stderr().contains("missing 'pwsh'"), run.stderr());
        assertTrue(run.stderr().contains("run bear-gates.ps1 directly"), run.stderr());
    }

    private static Path createFixtureRepo(Path repoRoot) throws Exception {
        Files.createDirectories(repoRoot.resolve(".bear/ci"));
        Files.createDirectories(repoRoot.resolve(".bear/ci-fixtures"));
        Files.createDirectories(repoRoot.resolve(".bear/tools/bear-cli/bin"));
        copyPackageFile("docs/bear-package/.bear/ci/bear-gates.ps1", repoRoot.resolve(".bear/ci/bear-gates.ps1"));
        copyPackageFile("docs/bear-package/.bear/ci/bear-gates.sh", repoRoot.resolve(".bear/ci/bear-gates.sh"));
        copyPackageFile("docs/bear-package/.bear/ci/README.md", repoRoot.resolve(".bear/ci/README.md"));
        copyPackageFile("docs/bear-package/.bear/ci/baseline-allow.json", repoRoot.resolve(".bear/ci/baseline-allow.json"));
        Files.writeString(repoRoot.resolve("bear.blocks.yaml"), "version: v0\nblocks: []\n", StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve(".bear/tools/bear-cli/bin/bear.bat"), fakeBearBat(), StandardCharsets.UTF_8);
        Files.writeString(repoRoot.resolve(".bear/tools/bear-cli/bin/bear"), fakeBearSh(), StandardCharsets.UTF_8);
        repoRoot.resolve(".bear/tools/bear-cli/bin/bear").toFile().setExecutable(true, false);
        repoRoot.resolve(".bear/ci/bear-gates.sh").toFile().setExecutable(true, false);
        return repoRoot;
    }

    private static void writeCheckFixture(Path repoRoot, int exitCode, String stdout, String stderr) throws Exception {
        writeFixture(repoRoot, "check", exitCode, stdout, stderr);
    }

    private static void writePrFixture(Path repoRoot, int exitCode, String stdout, String stderr) throws Exception {
        writeFixture(repoRoot, "pr-check", exitCode, stdout, stderr);
    }

    private static void writeFixture(Path repoRoot, String label, int exitCode, String stdout, String stderr) throws Exception {
        Path root = repoRoot.resolve(".bear/ci-fixtures").resolve(label);
        Files.writeString(Path.of(root.toString() + ".stdout"), stdout == null ? "" : stdout, StandardCharsets.UTF_8);
        Files.writeString(Path.of(root.toString() + ".stderr"), stderr == null ? "" : stderr, StandardCharsets.UTF_8);
        Files.writeString(Path.of(root.toString() + ".exit"), Integer.toString(exitCode) + "\n", StandardCharsets.UTF_8);
    }

    private static void installFakePwsh(Path repoRoot) throws Exception {
        Path testBin = repoRoot.resolve("test-bin");
        Files.createDirectories(testBin);
        String script = "#!/usr/bin/env bash\n"
            + "set -eu\n"
            + "base_sha=\"\"\n"
            + "ps_file=\"\"\n"
            + "while [ \"$#\" -gt 0 ]; do\n"
            + "  case \"$1\" in\n"
            + "    -File)\n"
            + "      ps_file=\"$2\"\n"
            + "      shift 2\n"
            + "      ;;\n"
            + "    --base-sha)\n"
            + "      base_sha=\"$2\"\n"
            + "      shift 2\n"
            + "      ;;\n"
            + "    *)\n"
            + "      shift\n"
            + "      ;;\n"
            + "  esac\n"
            + "done\n"
            + "case \"$ps_file\" in\n"
            + "  *bear-gates.ps1) ;;\n"
            + "  *) echo \"unexpected -File target: $ps_file\" >&2; exit 1 ;;\n"
            + "esac\n"
            + "mkdir -p build/bear/ci\n"
            + "cat > build/bear/ci/bear-ci-report.json <<JSON\n"
            + "{\"schemaVersion\":\"bear.ci.governance.v1\",\"mode\":\"observe\",\"resolvedBaseSha\":\"${base_sha}\",\"commands\":[],\"bearRaw\":{\"checkAgentJson\":null,\"prCheckAgentJson\":null,\"checkStdoutHash\":null,\"checkStderrHash\":null,\"prCheckStdoutHash\":null,\"prCheckStderrHash\":null},\"check\":{\"status\":\"ran\",\"exitCode\":0,\"code\":null,\"path\":null,\"remediation\":null,\"classes\":[\"CI_NO_STRUCTURAL_CHANGE\"]},\"prCheck\":{\"status\":\"ran\",\"reason\":null,\"exitCode\":0,\"code\":null,\"path\":null,\"remediation\":null,\"classes\":[\"CI_NO_STRUCTURAL_CHANGE\"],\"deltas\":[],\"governanceSignals\":[]},\"decision\":\"pass\"}\n"
            + "JSON\n"
            + "printf 'MODE=observe DECISION=pass BASE=%s\\n' \"$base_sha\"\n"
            + "printf 'CHECK exit=0 code=- classes=CI_NO_STRUCTURAL_CHANGE\\n'\n"
            + "printf 'PR-CHECK exit=0 code=- classes=CI_NO_STRUCTURAL_CHANGE\\n'\n";
        Path shim = testBin.resolve("pwsh");
        Files.writeString(shim, script, StandardCharsets.UTF_8);
        shim.toFile().setExecutable(true, false);
    }
    private static AgentDiagnostics.AgentProblem problem(
        AgentDiagnostics.AgentCategory category,
        String failureCode,
        String ruleId,
        String reasonKey,
        String file
    ) {
        java.util.Map<String, String> evidence = RepeatableRuleRegistry.requiresIdentityKey(ruleId)
            ? java.util.Map.of("identityKey", (file == null ? "" : file) + "|" + ruleId)
            : java.util.Map.of();
        return AgentDiagnostics.problem(
            category,
            failureCode,
            ruleId,
            reasonKey,
            AgentDiagnostics.AgentSeverity.ERROR,
            "alpha",
            file,
            null,
            ruleId != null ? ruleId : reasonKey,
            failureCode,
            evidence
        );
    }
    private static String checkJson(int exitCode) {
        return AgentDiagnostics.toJson(AgentDiagnostics.payload(
            AgentCommandContext.minimal("check", "all", "all", true),
            exitCode,
            List.of(),
            true
        ));
    }

    private static String prJson(int exitCode, PrGovernanceTelemetry.Snapshot snapshot) {
        return AgentDiagnostics.toJson(AgentDiagnostics.payloadForPrCheckAll(
            AgentCommandContext.minimal("pr-check", "all", "all", true),
            exitCode,
            List.of(),
            snapshot
        ));
    }

    private static String footer(String code, String path, String remediation) {
        return "CODE=" + code + "\r\nPATH=" + path + "\r\nREMEDIATION=" + remediation + "\r\n";
    }

    private static String readReport(Path repoRoot) throws Exception {
        return Files.readString(repoRoot.resolve("build/bear/ci/bear-ci-report.json"), StandardCharsets.UTF_8).trim();
    }

    private static String readSummary(Path repoRoot) throws Exception {
        return Files.readString(repoRoot.resolve("build/bear/ci/bear-ci-summary.md"), StandardCharsets.UTF_8);
    }

    private static void dumpRun(String label, Path repoRoot, ScriptRunResult run) throws Exception {
        System.err.println("=== " + label + " ===");
        System.err.println("exit=" + run.exitCode());
        System.err.println("--- stdout ---");
        System.err.println(run.stdout());
        System.err.println("--- stderr ---");
        System.err.println(run.stderr());
        Path reportPath = repoRoot.resolve("build/bear/ci/bear-ci-report.json");
        if (Files.exists(reportPath)) {
            System.err.println("--- report ---");
            System.err.println(Files.readString(reportPath, StandardCharsets.UTF_8));
        }
        Path summaryPath = repoRoot.resolve("build/bear/ci/bear-ci-summary.md");
        if (Files.exists(summaryPath)) {
            System.err.println("--- summary ---");
            System.err.println(Files.readString(summaryPath, StandardCharsets.UTF_8));
        }
    }

    private static void copyPackageFile(String source, Path destination) throws Exception {
        Files.createDirectories(destination.getParent());
        Files.copy(TestRepoPaths.repoRoot().resolve(source), destination);
    }
    private static ScriptRunResult runPowerShellWrapper(Path repoRoot, Map<String, String> env, String... args) throws Exception {
        assumePowerShellAvailable();
        ArrayList<String> command = new ArrayList<>();
        command.add(powerShellCommand());
        if (!isWindows()) {
            command.add("-NoProfile");
        }
        command.add("-ExecutionPolicy");
        command.add("Bypass");
        command.add("-File");
        command.add(repoRoot.resolve(".bear/ci/bear-gates.ps1").toString());
        command.add("--");
        command.addAll(Arrays.asList(args));
        return runProcess(command, repoRoot, env);
    }

    private static ScriptRunResult runBashWrapper(Path repoRoot, Map<String, String> env, String... args) throws Exception {
        StringBuilder script = new StringBuilder();
        String pathPrefix = env.get("PATH_PREFIX");
        if (pathPrefix != null && !pathPrefix.isBlank()) {
            script.append("PATH=").append(shellQuote(pathPrefix)).append(":\"$PATH\"; export PATH; ");
        }
        script.append("cd ").append(shellQuote(toWslPath(repoRoot))).append(" && ./.bear/ci/bear-gates.sh");
        for (String arg : args) {
            script.append(' ').append(shellQuote(arg));
        }
        Map<String, String> processEnv = new java.util.HashMap<>(env);
        processEnv.remove("PATH_PREFIX");
        return runProcess(List.of("bash", "-lc", script.toString()), repoRoot, processEnv);
    }

    private static ScriptRunResult runProcess(List<String> command, Path workDir, Map<String, String> env) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(false);
        ArrayList<String> inheritedKeys = new ArrayList<>(pb.environment().keySet());
        for (String key : inheritedKeys) {
            if (key.startsWith("GITHUB_")) {
                pb.environment().remove(key);
            }
        }
        pb.environment().putAll(env);
        Process process = pb.start();
        byte[] stdoutBytes = process.getInputStream().readAllBytes();
        byte[] stderrBytes = process.getErrorStream().readAllBytes();
        int exitCode = process.waitFor();
        return new ScriptRunResult(exitCode, new String(stdoutBytes, StandardCharsets.UTF_8), new String(stderrBytes, StandardCharsets.UTF_8));
    }

    private static String toWslPath(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        if (normalized.length() >= 3 && normalized.charAt(1) == ':') {
            return "/mnt/" + Character.toLowerCase(normalized.charAt(0)) + normalized.substring(2);
        }
        return normalized;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String powerShellCommand() {
        return isWindows() ? "powershell" : "pwsh";
    }

    private static String expectedBearCommandPrefix() {
        return isWindows() ? ".bear/tools/bear-cli/bin/bear.bat" : ".bear/tools/bear-cli/bin/bear";
    }

    private static void assumePowerShellAvailable() {
        try {
            Process process = new ProcessBuilder(powerShellCommand(), "-Version")
                .redirectErrorStream(true)
                .start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            Assumptions.assumeTrue(exitCode == 0, "PowerShell runtime unavailable in this environment");
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "PowerShell runtime unavailable in this environment");
        }
    }
    private static String fakeBearBat() {
        return "@echo off\r\n"
            + "setlocal\r\n"
            + "set CMD=%1\r\n"
            + "set FIXTURE=.bear\\ci-fixtures\\%CMD%\r\n"
            + "if exist \"%FIXTURE%.stdout\" type \"%FIXTURE%.stdout\"\r\n"
            + "if exist \"%FIXTURE%.stderr\" type \"%FIXTURE%.stderr\" 1>&2\r\n"
            + "set EXIT_CODE=0\r\n"
            + "if exist \"%FIXTURE%.exit\" set /p EXIT_CODE=<\"%FIXTURE%.exit\"\r\n"
            + "exit /b %EXIT_CODE%\r\n";
    }

    private static String fakeBearSh() {
        return "#!/usr/bin/env bash\n"
            + "set -eu\n"
            + "cmd=\"$1\"\n"
            + "fixture=\".bear/ci-fixtures/$cmd\"\n"
            + "if [ -f \"$fixture.stdout\" ]; then cat \"$fixture.stdout\"; fi\n"
            + "if [ -f \"$fixture.stderr\" ]; then cat \"$fixture.stderr\" >&2; fi\n"
            + "exit_code=0\n"
            + "if [ -f \"$fixture.exit\" ]; then read -r exit_code < \"$fixture.exit\"; fi\n"
            + "exit \"$exit_code\"\n";
    }

    private record ScriptRunResult(int exitCode, String stdout, String stderr) {
    }
}













