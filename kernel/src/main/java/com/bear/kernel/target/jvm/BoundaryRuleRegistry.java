package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BoundaryRuleRegistry {
    private final Map<String, BoundaryRule> rulesById;

    BoundaryRuleRegistry(List<BoundaryRule> rules) {
        HashMap<String, BoundaryRule> map = new HashMap<>();
        for (BoundaryRule rule : rules) {
            map.put(rule.id(), rule);
        }
        this.rulesById = Map.copyOf(map);
    }

    boolean appliesToPath(String ruleId, String relPath) {
        if (ruleId == null || relPath == null || !relPath.endsWith(".java")) {
            return false;
        }
        BoundaryRule rule = rulesById.get(ruleId);
        return rule != null && rule.applies(relPath);
    }
}


