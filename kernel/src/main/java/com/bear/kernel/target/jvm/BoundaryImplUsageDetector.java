package com.bear.kernel.target.jvm;

import com.bear.kernel.target.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BoundaryImplUsageDetector {
    private static final Pattern REFLECTION_CLASS_FORNAME_PATTERN = Pattern.compile("\\bClass\\s*\\.\\s*forName\\s*\\(");
    private static final Pattern REFLECTION_LOAD_CLASS_PATTERN = Pattern.compile("\\bloadClass\\s*\\(");
    private static final Pattern REFLECT_FORNAME_PATTERN = Pattern.compile(
        "\\bClass\\s*\\.\\s*forName\\s*\\(\\s*\"blocks\\.[A-Za-z0-9_$.]+\\.impl\\.[A-Za-z0-9_]+Impl\"\\s*\\)"
    );
    private static final Pattern REFLECT_LOADCLASS_PATTERN = Pattern.compile(
        "\\bloadClass\\s*\\(\\s*\"blocks\\.[A-Za-z0-9_$.]+\\.impl\\.[A-Za-z0-9_]+Impl\"\\s*\\)"
    );

    private BoundaryImplUsageDetector() {
    }

    static String firstDirectImplUsageToken(String source) {
        Matcher importMatcher = PolicyPatterns.DIRECT_IMPL_IMPORT_PATTERN.matcher(source);
        if (importMatcher.find()) {
            return importMatcher.group();
        }
        Matcher newMatcher = PolicyPatterns.DIRECT_IMPL_NEW_PATTERN.matcher(source);
        if (newMatcher.find()) {
            return newMatcher.group();
        }
        Matcher castMatcher = PolicyPatterns.DIRECT_IMPL_TYPE_CAST_PATTERN.matcher(source);
        if (castMatcher.find()) {
            return castMatcher.group();
        }
        Matcher varMatcher = PolicyPatterns.DIRECT_IMPL_VAR_DECL_PATTERN.matcher(source);
        if (varMatcher.find()) {
            return varMatcher.group();
        }
        Matcher extendsMatcher = PolicyPatterns.DIRECT_IMPL_EXTENDS_IMPL_PATTERN.matcher(source);
        if (extendsMatcher.find()) {
            return extendsMatcher.group();
        }
        Matcher implementsMatcher = PolicyPatterns.DIRECT_IMPL_IMPLEMENTS_IMPL_PATTERN.matcher(source);
        if (implementsMatcher.find()) {
            return implementsMatcher.group();
        }
        return null;
    }

    static String firstReflectionClassloadingToken(String source) {
        Matcher m = REFLECTION_CLASS_FORNAME_PATTERN.matcher(source);
        if (m.find()) {
            return "Class.forName(...)";
        }
        Matcher loadClassMatcher = REFLECTION_LOAD_CLASS_PATTERN.matcher(source);
        if (loadClassMatcher.find()) {
            return "loadClass(...)";
        }
        return null;
    }

    static String firstReflectiveImplUsageToken(String source) {
        Matcher forName = REFLECT_FORNAME_PATTERN.matcher(source);
        if (forName.find()) {
            return forName.group();
        }
        Matcher loadClass = REFLECT_LOADCLASS_PATTERN.matcher(source);
        if (loadClass.find()) {
            return loadClass.group();
        }
        return null;
    }
}


