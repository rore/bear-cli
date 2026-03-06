package com.bear.app;

import java.util.List;
import java.util.regex.Pattern;

final class ContainmentFailureClassifier {
    private static final List<Pattern> EXPLICIT_CONTAINMENT_PATTERNS = List.of(
        Pattern.compile("compileBearImpl__shared"),
        Pattern.compile("build/generated/bear/gradle/bear-containment\\.gradle"),
        Pattern.compile("bear-containment\\.gradle"),
        Pattern.compile("CONTAINMENT_REQUIRED:")
    );

    private static final List<Pattern> CONTAINMENT_CLASSPATH_PATTERNS = List.of(
        Pattern.compile("package\\s+blocks\\.[A-Za-z0-9_.]+\\.impl\\s+does\\s+not\\s+exist"),
        Pattern.compile("cannot\\s+find\\s+symbol.*blocks\\.[A-Za-z0-9_.]+\\.impl", Pattern.DOTALL)
    );

    private ContainmentFailureClassifier() {
    }

    static boolean hasContainmentSignal(ProjectTestResult testResult, String primaryLine) {
        String output = testResult == null ? null : testResult.output();
        String task = testResult == null ? null : testResult.lastObservedTask();
        return containsExplicitContainmentSignal(primaryLine)
            || containsExplicitContainmentSignal(output)
            || containsExplicitContainmentSignal(task)
            || (containsContainmentClasspathSignal(primaryLine) && containsExplicitContainmentSignal(output))
            || (containsContainmentClasspathSignal(output) && containsExplicitContainmentSignal(task));
    }

    private static boolean containsExplicitContainmentSignal(String text) {
        return containsAny(text, EXPLICIT_CONTAINMENT_PATTERNS);
    }

    private static boolean containsContainmentClasspathSignal(String text) {
        return containsAny(text, CONTAINMENT_CLASSPATH_PATTERNS);
    }

    private static boolean containsAny(String text, List<Pattern> patterns) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
