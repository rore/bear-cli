package com.bear.kernel.target.jvm;

import java.util.Map;

final class JvmContainmentRenderUnits {
    private JvmContainmentRenderUnits() {
    }

    static String renderContainmentRequired(Map<String, JvmTarget.ContainmentBlockSpec> index) {
        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"schemaVersion\":\"v1\",");
        out.append("\"target\":\"java-gradle\",");
        out.append("\"blocks\":[");
        boolean firstBlock = true;
        for (JvmTarget.ContainmentBlockSpec block : index.values()) {
            if (!firstBlock) {
                out.append(",");
            }
            firstBlock = false;
            out.append("{\"blockKey\":\"").append(jsonEscape(block.blockKey())).append("\",");
            out.append("\"implDir\":\"").append(jsonEscape(block.implDir())).append("\",");
            out.append("\"allowedDeps\":[");
            for (int i = 0; i < block.allowedDeps().size(); i++) {
                if (i > 0) {
                    out.append(",");
                }
                JvmTarget.ContainmentDep dep = block.allowedDeps().get(i);
                out.append("{\"ga\":\"").append(jsonEscape(dep.ga())).append("\",\"version\":\"")
                    .append(jsonEscape(dep.version())).append("\"}");
            }
            out.append("]}");
        }
        out.append("]}");
        out.append("\n");
        return out.toString();
    }

    static String renderAllowedDepsConfig(JvmTarget.ContainmentBlockSpec block) {
        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"blockKey\":\"").append(jsonEscape(block.blockKey())).append("\",");
        out.append("\"implDir\":\"").append(jsonEscape(block.implDir())).append("\",");
        out.append("\"allowedDeps\":[");
        for (int i = 0; i < block.allowedDeps().size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            JvmTarget.ContainmentDep dep = block.allowedDeps().get(i);
            out.append("{\"ga\":\"").append(jsonEscape(dep.ga())).append("\",\"version\":\"")
                .append(jsonEscape(dep.version())).append("\"}");
        }
        out.append("]}");
        out.append("\n");
        return out.toString();
    }

    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}


