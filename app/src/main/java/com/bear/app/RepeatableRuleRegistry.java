package com.bear.app;

import java.util.Set;

final class RepeatableRuleRegistry {
    static final Set<String> RULE_IDS = Set.of(
        CliCodes.UNDECLARED_REACH,
        CliCodes.REFLECTION_DISPATCH_FORBIDDEN,
        "DIRECT_IMPL_USAGE",
        "IMPL_CONTAINMENT_BYPASS",
        "PORT_IMPL_OUTSIDE_GOVERNED_ROOT",
        "MULTI_BLOCK_PORT_IMPL_FORBIDDEN",
        "BLOCK_PORT_IMPL_INVALID",
        "BLOCK_PORT_REFERENCE_FORBIDDEN",
        "BLOCK_PORT_INBOUND_EXECUTE_FORBIDDEN"
    );

    private RepeatableRuleRegistry() {
    }

    static boolean requiresIdentityKey(String ruleId) {
        return ruleId != null && RULE_IDS.contains(ruleId);
    }
}
