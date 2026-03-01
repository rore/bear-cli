package com.bear.app;

import java.util.Objects;
import java.util.function.Predicate;

record BoundaryRule(String id, Predicate<String> appliesToPath) {
    BoundaryRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appliesToPath, "appliesToPath");
    }

    boolean applies(String relPath) {
        return appliesToPath.test(relPath);
    }
}
