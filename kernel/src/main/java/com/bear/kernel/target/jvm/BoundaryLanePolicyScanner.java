package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BoundaryLanePolicyScanner {
    private static final String SHARED_PURITY_RULE = "SHARED_PURITY_VIOLATION";
    private static final String IMPL_PURITY_RULE = "IMPL_PURITY_VIOLATION";
    private static final String IMPL_STATE_DEPENDENCY_RULE = "IMPL_STATE_DEPENDENCY_BYPASS";
    private static final String SCOPED_IMPORT_POLICY_RULE = "SCOPED_IMPORT_POLICY_BYPASS";
    private static final String SHARED_LAYOUT_POLICY_RULE = "SHARED_LAYOUT_POLICY_VIOLATION";
    private static final String STATE_STORE_OP_MISUSE_RULE = "STATE_STORE_OP_MISUSE";
    private static final String STATE_STORE_NOOP_UPDATE_RULE = "STATE_STORE_NOOP_UPDATE";
    private static final String SHARED_ROOT_PREFIX = "src/main/java/blocks/_shared/";
    private static final String SHARED_PURE_ROOT_PREFIX = "src/main/java/blocks/_shared/pure/";
    private static final String SHARED_STATE_ROOT_PREFIX = "src/main/java/blocks/_shared/state/";
    private static final String BLOCKS_ROOT_PREFIX = "src/main/java/blocks/";
    private static final String ADAPTER_SEGMENT = "/adapter/";
    private static final BoundaryRuleRegistry RULE_REGISTRY = new BoundaryRuleRegistry(List.of(
        new BoundaryRule(SHARED_PURITY_RULE, BoundaryLanePolicyScanner::isSharedPureSourcePath),
        new BoundaryRule(SCOPED_IMPORT_POLICY_RULE, relPath -> isImplSourcePath(relPath) || isSharedPureSourcePath(relPath)),
        new BoundaryRule(IMPL_PURITY_RULE, BoundaryLanePolicyScanner::isImplSourcePath),
        new BoundaryRule(IMPL_STATE_DEPENDENCY_RULE, BoundaryLanePolicyScanner::isImplSourcePath),
        new BoundaryRule(STATE_STORE_NOOP_UPDATE_RULE, relPath -> relPath.startsWith(SHARED_STATE_ROOT_PREFIX)),
        new BoundaryRule(STATE_STORE_OP_MISUSE_RULE, BoundaryLanePolicyScanner::isAdapterSourcePath),
        new BoundaryRule(SHARED_LAYOUT_POLICY_RULE, relPath -> relPath.startsWith(SHARED_ROOT_PREFIX))
    ));
    private static final Pattern STATIC_FIELD_STATEMENT_PATTERN = Pattern.compile(
        "(?m)^(?!\\s*import\\b)\\s*[^\\n{};]*\\bstatic\\b[^\\n{};]*;"
    );
    private static final Pattern MODIFIER_PATTERN = Pattern.compile("\\b(?:public|protected|private|static|final|transient|volatile)\\b");
    private static final Pattern LOCAL_ENUM_PATTERN = Pattern.compile("\\benum\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern SYNCHRONIZED_PATTERN = Pattern.compile("\\bsynchronized\\b");
    private static final Pattern NEW_TYPE_PATTERN_FOR_FIELDS = Pattern.compile("\\bnew\\s+([A-Za-z_][A-Za-z0-9_\\.]*)");
    private static final Pattern METHOD_SIGNATURE_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:public|protected|private)?\\s*(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
            + "([A-Za-z_][A-Za-z0-9_\\.<>,\\[\\]\\s]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;{}]*\\)"
            + "\\s*(?:throws\\s+[A-Za-z0-9_\\.,\\s]+)?\\s*\\{"
    );
    private static final Pattern UPDATE_METHOD_NAME_PATTERN = Pattern.compile("^(?:update.*|set.*|put.*Balance)$");
    private static final Pattern CREATE_CALL_PATTERN = Pattern.compile("\\.\\s*create(?:Wallet|[A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern NOOP_MISSING_RETURN_PATTERN = Pattern.compile(
        "if\\s*\\([^\\)]*==\\s*null[^\\)]*\\)\\s*\\{\\s*return\\s*;\\s*\\}",
        Pattern.DOTALL
    );
    private static final Pattern NOOP_INLINE_MISSING_RETURN_PATTERN = Pattern.compile(
        "if\\s*\\([^\\)]*==\\s*null[^\\)]*\\)\\s*return\\s*;",
        Pattern.DOTALL
    );
    private static final Pattern LOOKUP_BINDING_PATTERN = Pattern.compile(
        "(?:final\\s+)?[A-Za-z_][A-Za-z0-9_\\.<>,\\[\\]]*\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[^;]*\\.(?:get|find|lookup|fetch)\\s*\\([^;]*\\)\\s*;",
        Pattern.DOTALL
    );
    private static final Pattern ENUM_CONSTANT_INIT_PATTERN = Pattern.compile(
        "^([A-Za-z_][A-Za-z0-9_\\.]*)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)$"
    );
    private static final Set<String> PURE_FORBIDDEN_PREFIX_TOKENS = Set.of(
        "java.io.",
        "java.net.",
        "java.nio.file."
    );
    private static final Set<String> IMPL_FORBIDDEN_PREFIX_TOKENS = Set.of(
        "java.io.",
        "java.net.",
        "java.nio.file.",
        "java.util.concurrent."
    );
    private static final Set<String> ALLOWED_PURE_CONSTANT_TYPES = Set.of(
        "byte",
        "short",
        "int",
        "long",
        "float",
        "double",
        "boolean",
        "char",
        "java.lang.Byte",
        "java.lang.Short",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Float",
        "java.lang.Double",
        "java.lang.Boolean",
        "java.lang.Character",
        "java.lang.String"
    );
    private static final Set<String> MUTABLE_TYPE_TOKENS = Set.of(
        "Map",
        "List",
        "Set",
        "Collection",
        "Queue",
        "Deque",
        "ConcurrentMap",
        "ConcurrentHashMap",
        "CopyOnWriteArrayList",
        "CopyOnWriteArraySet",
        "Atomic",
        "ThreadLocal"
    );

    private BoundaryLanePolicyScanner() {
    }

    static List<BoundaryBypassFinding> scanLanePolicyViolations(
        String relPath,
        String source,
        String sanitized,
        Set<String> pureImmutableAllowlist
    ) {
        ArrayList<BoundaryBypassFinding> findings = new ArrayList<>();
        if (!relPath.endsWith(".java")) {
            return findings;
        }

        if (ruleAppliesToPath(SHARED_LAYOUT_POLICY_RULE, relPath) && isSharedJavaOutsidePureOrState(relPath)) {
            findings.add(new BoundaryBypassFinding(
                SHARED_LAYOUT_POLICY_RULE,
                relPath,
                "shared java source must be under blocks/_shared/pure or blocks/_shared/state"
            ));
        }

        boolean implLane = ruleAppliesToPath(IMPL_PURITY_RULE, relPath);
        boolean pureLane = ruleAppliesToPath(SHARED_PURITY_RULE, relPath);
        boolean implStateDependencyLane = ruleAppliesToPath(IMPL_STATE_DEPENDENCY_RULE, relPath);
        boolean scopedImportLane = ruleAppliesToPath(SCOPED_IMPORT_POLICY_RULE, relPath);
        boolean stateLane = ruleAppliesToPath(STATE_STORE_NOOP_UPDATE_RULE, relPath);
        boolean adapterLane = ruleAppliesToPath(STATE_STORE_OP_MISUSE_RULE, relPath);
        if (!implLane && !pureLane && !implStateDependencyLane && !scopedImportLane && !stateLane && !adapterLane) {
            return findings;
        }

        if (implStateDependencyLane && source.contains("blocks._shared.state.")) {
            findings.add(new BoundaryBypassFinding(
                IMPL_STATE_DEPENDENCY_RULE,
                relPath,
                "impl lane references blocks._shared.state.*"
            ));
        }

        if (scopedImportLane) {
            String forbiddenToken = firstForbiddenPrefixToken(
                source,
                implLane ? IMPL_FORBIDDEN_PREFIX_TOKENS : PURE_FORBIDDEN_PREFIX_TOKENS
            );
            if (forbiddenToken != null) {
                findings.add(new BoundaryBypassFinding(
                    SCOPED_IMPORT_POLICY_RULE,
                    relPath,
                    "forbidden package token in " + (implLane ? "impl" : "_shared.pure") + " lane: " + forbiddenToken
                ));
            }
        }

        if (implLane || pureLane) {
            String purityRule = implLane ? IMPL_PURITY_RULE : SHARED_PURITY_RULE;
            String laneLabel = implLane ? "impl" : "_shared.pure";
            String synchronizedToken = firstSynchronizedToken(sanitized);
            if (synchronizedToken != null) {
                findings.add(new BoundaryBypassFinding(
                    purityRule,
                    relPath,
                    "synchronized usage is forbidden in " + laneLabel + " lane"
                ));
            }

            String packageName = BoundaryBypassScanner.parsePackageName(sanitized);
            Map<String, String> explicitImports = BoundaryBypassScanner.parseExplicitImports(sanitized);
            Set<String> localEnums = parseLocalEnumNames(sanitized);
            for (StaticFieldStatement statement : parseStaticFieldStatements(sanitized)) {
                if (!statement.isFinalField()) {
                    findings.add(new BoundaryBypassFinding(
                        purityRule,
                        relPath,
                        "mutable static field is forbidden: " + BoundaryBypassScanner.normalizeToken(statement.raw())
                    ));
                    continue;
                }

                if (containsKnownMutableTypeToken(statement.typeRaw())) {
                    findings.add(new BoundaryBypassFinding(
                        purityRule,
                        relPath,
                        "static final mutable container/atomic/threadlocal is forbidden: " + BoundaryBypassScanner.normalizeToken(statement.raw())
                    ));
                    continue;
                }

                if (!pureLane) {
                    continue;
                }

                String declaredTypeFqcn = BoundaryBypassScanner.resolveTypeTokenFqcn(
                    statement.typeRaw(),
                    packageName,
                    explicitImports
                );
                if (isAllowedPureConstantType(declaredTypeFqcn)
                    || isAllowlistedImmutableType(declaredTypeFqcn, pureImmutableAllowlist)
                    || isEnumConstantDeclaration(statement, declaredTypeFqcn, localEnums, packageName, explicitImports)) {
                    continue;
                }

                String newTypeToken = extractNewTypeToken(statement.initializerRaw());
                if (newTypeToken != null) {
                    String newTypeFqcn = BoundaryBypassScanner.resolveTypeTokenFqcn(newTypeToken, packageName, explicitImports);
                    if (!isAllowlistedImmutableType(newTypeFqcn, pureImmutableAllowlist)) {
                        findings.add(new BoundaryBypassFinding(
                            SHARED_PURITY_RULE,
                            relPath,
                            "static final `new` initializer is forbidden in _shared.pure unless type is allowlisted immutable: "
                                + BoundaryBypassScanner.normalizeToken(statement.raw())
                        ));
                    }
                    continue;
                }

                findings.add(new BoundaryBypassFinding(
                    SHARED_PURITY_RULE,
                    relPath,
                    "static final type is not an allowed pure constant type: " + BoundaryBypassScanner.normalizeToken(statement.raw())
                ));
            }
        }

        if (stateLane) {
            String detail = firstStateStoreNoopUpdateDetail(sanitized);
            if (detail != null) {
                findings.add(new BoundaryBypassFinding(
                    STATE_STORE_NOOP_UPDATE_RULE,
                    relPath,
                    detail
                ));
            }
        }

        if (adapterLane) {
            String detail = firstStateStoreOpMisuseDetail(sanitized);
            if (detail != null) {
                findings.add(new BoundaryBypassFinding(
                    STATE_STORE_OP_MISUSE_RULE,
                    relPath,
                    detail
                ));
            }
        }

        return findings;
    }

    static boolean ruleAppliesToPath(String ruleId, String relPath) {
        return RULE_REGISTRY.appliesToPath(ruleId, relPath);
    }

    private static boolean isImplSourcePath(String relPath) {
        return relPath.startsWith(BLOCKS_ROOT_PREFIX) && relPath.contains("/impl/");
    }

    private static boolean isAdapterSourcePath(String relPath) {
        return relPath.startsWith(BLOCKS_ROOT_PREFIX) && relPath.contains(ADAPTER_SEGMENT);
    }

    private static boolean isSharedPureSourcePath(String relPath) {
        return relPath.startsWith(SHARED_PURE_ROOT_PREFIX);
    }

    private static boolean isSharedJavaOutsidePureOrState(String relPath) {
        return relPath.startsWith(SHARED_ROOT_PREFIX)
            && !relPath.startsWith(SHARED_PURE_ROOT_PREFIX)
            && !relPath.startsWith(SHARED_STATE_ROOT_PREFIX);
    }

    private static String firstStateStoreNoopUpdateDetail(String source) {
        for (MethodSlice method : extractMethodSlices(source)) {
            if (!UPDATE_METHOD_NAME_PATTERN.matcher(method.name()).matches()) {
                continue;
            }
            if (NOOP_MISSING_RETURN_PATTERN.matcher(method.body()).find()
                || NOOP_INLINE_MISSING_RETURN_PATTERN.matcher(method.body()).find()) {
                return stateStoreNoopDetail("EARLY_RETURN_NOOP", method.name());
            }
            if (hasNullGuardNoopPattern(method)) {
                return stateStoreNoopDetail("NULL_GUARD_NOOP", method.name());
            }
        }
        return null;
    }

    private static boolean hasNullGuardNoopPattern(MethodSlice method) {
        Set<String> candidates = lookupBindingVariables(method.body());
        if (candidates.isEmpty()) {
            return false;
        }
        for (String variable : candidates) {
            String guardBody = firstNullGuardMutationBody(method.body(), variable);
            if (guardBody == null) {
                continue;
            }
            if (!hasUpdateMutationSignal(guardBody)) {
                continue;
            }
            if (hasExplicitMissingStateSignal(method.body(), variable, method.returnType())) {
                continue;
            }
            if (hasMutationSignalOutsideNullGuard(method.body(), variable)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static Set<String> lookupBindingVariables(String body) {
        HashSet<String> variables = new HashSet<>();
        Matcher matcher = LOOKUP_BINDING_PATTERN.matcher(body);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private static String firstNullGuardMutationBody(String body, String variable) {
        Pattern blockGuard = Pattern.compile(
            "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*!=\\s*null\\s*\\)\\s*\\{([\\s\\S]*?)\\}",
            Pattern.DOTALL
        );
        Matcher blockMatcher = blockGuard.matcher(body);
        if (blockMatcher.find()) {
            return blockMatcher.group(1);
        }
        Pattern inlineGuard = Pattern.compile(
            "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*!=\\s*null\\s*\\)\\s*([^\\n;]*;)",
            Pattern.DOTALL
        );
        Matcher inlineMatcher = inlineGuard.matcher(body);
        if (inlineMatcher.find()) {
            return inlineMatcher.group(1);
        }
        return null;
    }

    private static boolean hasExplicitMissingStateSignal(String body, String variable, String returnTypeRaw) {
        Pattern nullBlock = Pattern.compile(
            "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*==\\s*null\\s*\\)\\s*\\{([\\s\\S]*?)\\}",
            Pattern.DOTALL
        );
        Matcher nullBlockMatcher = nullBlock.matcher(body);
        if (nullBlockMatcher.find() && hasAcceptedMissingStateSignal(nullBlockMatcher.group(1), returnTypeRaw)) {
            return true;
        }

        Pattern nullInline = Pattern.compile(
            "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*==\\s*null\\s*\\)\\s*([^\\n;]*;)",
            Pattern.DOTALL
        );
        Matcher nullInlineMatcher = nullInline.matcher(body);
        if (nullInlineMatcher.find() && hasAcceptedMissingStateSignal(nullInlineMatcher.group(1), returnTypeRaw)) {
            return true;
        }

        Pattern elseBranch = Pattern.compile(
            "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*!=\\s*null\\s*\\)\\s*\\{?[\\s\\S]*?\\}?\\s*else\\s*\\{([\\s\\S]*?)\\}",
            Pattern.DOTALL
        );
        Matcher elseMatcher = elseBranch.matcher(body);
        if (elseMatcher.find() && hasAcceptedMissingStateSignal(elseMatcher.group(1), returnTypeRaw)) {
            return true;
        }

        Pattern explicitSignalCall = Pattern.compile("\\b(?:notFound|requirePresent)\\s*\\(");
        if (explicitSignalCall.matcher(body).find()) {
            return true;
        }

        String compactReturnType = returnTypeRaw == null ? "" : returnTypeRaw.replaceAll("\\s+", "");
        if ("boolean".equals(compactReturnType) && body.contains("return false;")) {
            return true;
        }
        if ((compactReturnType.endsWith("Optional") || compactReturnType.contains(".Optional"))
            && body.contains("return Optional.empty();")) {
            return true;
        }
        return allowsNullReturn(compactReturnType) && body.contains("return null;");
    }

    private static boolean hasAcceptedMissingStateSignal(String branchBody, String returnTypeRaw) {
        if (branchBody.contains("throw ")) {
            return true;
        }
        String compactReturnType = returnTypeRaw == null ? "" : returnTypeRaw.replaceAll("\\s+", "");
        if ("boolean".equals(compactReturnType) && branchBody.contains("return false;")) {
            return true;
        }
        if ((compactReturnType.endsWith("Optional") || compactReturnType.contains(".Optional"))
            && branchBody.contains("return Optional.empty();")) {
            return true;
        }
        return allowsNullReturn(compactReturnType) && branchBody.contains("return null;");
    }

    private static boolean allowsNullReturn(String compactReturnType) {
        if (compactReturnType.isBlank()) {
            return false;
        }
        return !Set.of("void", "boolean", "byte", "short", "int", "long", "float", "double", "char")
            .contains(compactReturnType);
    }

    private static boolean hasMutationSignalOutsideNullGuard(String body, String variable) {
        String remaining = body
            .replaceAll(
                "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*!=\\s*null\\s*\\)\\s*\\{[\\s\\S]*?\\}",
                " "
            )
            .replaceAll(
                "if\\s*\\(\\s*" + Pattern.quote(variable) + "\\s*!=\\s*null\\s*\\)\\s*[^\\n;]*;",
                " "
            );
        return hasUpdateMutationSignal(remaining);
    }

    private static boolean hasUpdateMutationSignal(String source) {
        return source.contains("balanceCents")
            || source.contains("setBalance")
            || source.contains("updateBalance")
            || Pattern.compile("\\b(?:set[A-Z][A-Za-z0-9_]*|put[A-Z]?[A-Za-z0-9_]*|update[A-Z]?[A-Za-z0-9_]*)\\s*\\(")
            .matcher(source)
            .find();
    }

    private static String stateStoreNoopDetail(String patternId, String methodName) {
        return "KIND=STATE_STORE_NOOP_UPDATE|PATTERN=" + patternId
            + "|method=" + methodName
            + "|detail=silent missing-state handling in _shared/state update path";
    }

    private static String firstStateStoreOpMisuseDetail(String source) {
        for (MethodSlice method : extractMethodSlices(source)) {
            if (!CREATE_CALL_PATTERN.matcher(method.body()).find()) {
                continue;
            }
            if (!hasUpdatePathSignal(method)) {
                continue;
            }
            return "KIND=STATE_STORE_OP_MISUSE: method=" + method.name()
                + ": adapter update-path signals co-occur with state create-call signals";
        }
        return null;
    }

    private static boolean hasUpdatePathSignal(MethodSlice method) {
        if (UPDATE_METHOD_NAME_PATTERN.matcher(method.name()).matches()) {
            return true;
        }
        if (method.body().contains("updateBalance(")) {
            return true;
        }
        return hasBalanceMutationToken(method.body());
    }

    private static boolean hasBalanceMutationToken(String body) {
        return body.contains("balanceCents")
            || body.contains("setBalanceCents(")
            || body.contains("\"balanceCents\"");
    }

    private static List<MethodSlice> extractMethodSlices(String source) {
        ArrayList<MethodSlice> methods = new ArrayList<>();
        Matcher matcher = METHOD_SIGNATURE_PATTERN.matcher(source);
        while (matcher.find()) {
            String returnType = matcher.group(1).trim();
            String methodName = matcher.group(2);
            int openBrace = source.indexOf('{', matcher.end() - 1);
            if (openBrace < 0) {
                continue;
            }
            int closeBrace = findClosingBrace(source, openBrace);
            if (closeBrace < 0) {
                continue;
            }
            String body = source.substring(openBrace + 1, closeBrace);
            methods.add(new MethodSlice(methodName, body, returnType));
        }
        return methods;
    }

    private static int findClosingBrace(String source, int openBraceIndex) {
        if (openBraceIndex < 0 || openBraceIndex >= source.length() || source.charAt(openBraceIndex) != '{') {
            return -1;
        }
        int depth = 1;
        for (int i = openBraceIndex + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String firstForbiddenPrefixToken(String source, Set<String> forbiddenPrefixes) {
        int bestIndex = Integer.MAX_VALUE;
        String bestToken = null;
        for (String token : forbiddenPrefixes) {
            int idx = source.indexOf(token);
            if (idx >= 0 && idx < bestIndex) {
                bestIndex = idx;
                bestToken = token;
            }
        }
        return bestToken;
    }

    private static String firstSynchronizedToken(String source) {
        Matcher matcher = SYNCHRONIZED_PATTERN.matcher(source);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static Set<String> parseLocalEnumNames(String source) {
        TreeSet<String> enums = new TreeSet<>();
        Matcher matcher = LOCAL_ENUM_PATTERN.matcher(source);
        while (matcher.find()) {
            enums.add(matcher.group(1));
        }
        return enums;
    }

    private static List<StaticFieldStatement> parseStaticFieldStatements(String source) {
        ArrayList<StaticFieldStatement> statements = new ArrayList<>();
        Matcher matcher = STATIC_FIELD_STATEMENT_PATTERN.matcher(source);
        while (matcher.find()) {
            String raw = matcher.group().trim();
            if (raw.isEmpty()) {
                continue;
            }
            int eqIndex = raw.indexOf('=');
            int parenIndex = raw.indexOf('(');
            if (parenIndex >= 0 && (eqIndex < 0 || parenIndex < eqIndex)) {
                continue;
            }
            String noSemi = raw.endsWith(";") ? raw.substring(0, raw.length() - 1).trim() : raw;
            String initializer = "";
            String left = noSemi;
            if (eqIndex >= 0) {
                left = noSemi.substring(0, eqIndex).trim();
                initializer = noSemi.substring(eqIndex + 1).trim();
            }
            String leftNoModifiers = MODIFIER_PATTERN.matcher(left).replaceAll(" ");
            leftNoModifiers = leftNoModifiers.replaceAll("\\s+", " ").trim();
            Matcher fieldMatcher = Pattern.compile("^(.*\\S)\\s+([A-Za-z_][A-Za-z0-9_]*)$").matcher(leftNoModifiers);
            if (!fieldMatcher.find()) {
                continue;
            }
            String typeRaw = normalizeDeclaredType(fieldMatcher.group(1).trim());
            boolean isFinalField = Pattern.compile("\\bfinal\\b").matcher(raw).find();
            statements.add(new StaticFieldStatement(raw, typeRaw, initializer, isFinalField));
        }
        return statements;
    }

    private static String normalizeDeclaredType(String typeRaw) {
        String normalized = typeRaw.replaceAll("\\s+", "");
        normalized = stripGenerics(normalized);
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        if (normalized.endsWith("...")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    private static String stripGenerics(String token) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '<') {
                depth++;
                continue;
            }
            if (c == '>') {
                if (depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean containsKnownMutableTypeToken(String typeRaw) {
        String typeLower = typeRaw.toLowerCase();
        for (String token : MUTABLE_TYPE_TOKENS) {
            String tokenLower = token.toLowerCase();
            if (typeLower.contains(tokenLower)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedPureConstantType(String fqcn) {
        if (fqcn == null || fqcn.isBlank()) {
            return false;
        }
        if (ALLOWED_PURE_CONSTANT_TYPES.contains(fqcn)) {
            return true;
        }
        String simple = BoundaryBypassScanner.simpleName(fqcn);
        return ALLOWED_PURE_CONSTANT_TYPES.contains("java.lang." + simple);
    }

    private static boolean isAllowlistedImmutableType(String fqcn, Set<String> allowlist) {
        if (fqcn == null || fqcn.isBlank()) {
            return false;
        }
        return allowlist.contains(fqcn);
    }

    private static boolean isEnumConstantDeclaration(
        StaticFieldStatement statement,
        String declaredTypeFqcn,
        Set<String> localEnums,
        String packageName,
        Map<String, String> explicitImports
    ) {
        if (declaredTypeFqcn == null || declaredTypeFqcn.isBlank()) {
            return false;
        }
        String declaredSimple = BoundaryBypassScanner.simpleName(declaredTypeFqcn);
        if (localEnums.contains(declaredSimple)) {
            return true;
        }
        String initializer = statement.initializerRaw().trim();
        Matcher matcher = ENUM_CONSTANT_INIT_PATTERN.matcher(initializer);
        if (!matcher.find()) {
            return false;
        }
        String initializerTypeToken = matcher.group(1);
        String initializerTypeFqcn = BoundaryBypassScanner.resolveTypeTokenFqcn(initializerTypeToken, packageName, explicitImports);
        if (initializerTypeFqcn == null || initializerTypeFqcn.isBlank()) {
            return false;
        }
        return declaredTypeFqcn.equals(initializerTypeFqcn)
            || declaredSimple.equals(BoundaryBypassScanner.simpleName(initializerTypeFqcn));
    }

    private static String extractNewTypeToken(String initializerRaw) {
        Matcher matcher = NEW_TYPE_PATTERN_FOR_FIELDS.matcher(initializerRaw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private record StaticFieldStatement(
        String raw,
        String typeRaw,
        String initializerRaw,
        boolean isFinalField
    ) {
    }

    private record MethodSlice(
        String name,
        String body,
        String returnType
    ) {
    }
}


