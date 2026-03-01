package com.bear.app;

import java.util.regex.Pattern;

final class PolicyPatterns {
    static final Pattern DIRECT_IMPL_IMPORT_PATTERN = Pattern.compile(
        "\\bimport\\s+blocks(?:\\.[A-Za-z_][A-Za-z0-9_]*)*\\.impl\\.[A-Za-z_][A-Za-z0-9_]*Impl\\s*;"
    );
    static final Pattern DIRECT_IMPL_NEW_PATTERN = Pattern.compile(
        "\\bnew\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\("
    );
    static final Pattern DIRECT_IMPL_TYPE_CAST_PATTERN = Pattern.compile(
        "\\(\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\s*\\)"
    );
    static final Pattern DIRECT_IMPL_VAR_DECL_PATTERN = Pattern.compile(
        "(?m)\\b(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:[=;,)])"
    );
    static final Pattern DIRECT_IMPL_EXTENDS_IMPL_PATTERN = Pattern.compile(
        "\\bextends\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    static final Pattern DIRECT_IMPL_IMPLEMENTS_IMPL_PATTERN = Pattern.compile(
        "\\bimplements\\s+(?:[A-Za-z_][A-Za-z0-9_]*\\s*\\.\\s*)*[A-Za-z_][A-Za-z0-9_]*Impl\\b"
    );
    static final Pattern PORT_USED_SUPPRESSION_PATTERN = Pattern.compile(
        "(?m)^\\s*//\\s*BEAR:PORT_USED\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$"
    );

    private PolicyPatterns() {
    }
}
