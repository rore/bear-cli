package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
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


