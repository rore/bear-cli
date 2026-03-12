package com.bear.kernel.target.locator;

public record CanonicalLocator(
        String repository,
        String project,
        String module,
        LocatorSymbol symbol,
        LocatorSpan span) {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(repository).append("/").append(project).append(":").append(module);
        if (symbol != null && symbol.name() != null) {
            sb.append(":").append(symbol.kind()).append(":").append(symbol.name());
        }
        if (span != null && span.startLine() != null) {
            sb.append("@").append(span.startLine());
        }
        return sb.toString();
    }

    public String identityKey() {
        if (symbol != null && symbol.name() != null) {
            return module + ":" + symbol.kind() + ":" + symbol.name();
        }
        if (span != null && span.startLine() != null) {
            return module + ":" + span.startLine();
        }
        return module;
    }

    public static String anonymousName(String module, int startLine) {
        return "<anonymous@" + module + ":" + startLine + ">";
    }

    public static String defaultExportName(String module) {
        return "<default@" + module + ">";
    }
}
