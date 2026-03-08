package com.bear.kernel.target.jvm;

import com.bear.kernel.ir.BearIr;
import com.bear.kernel.ir.BearIrYamlEmitter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class JvmManifestRenderUnits {
    private JvmManifestRenderUnits() {
    }

    static String renderSurfaceManifest(BearIr ir) {
        String irHash = sha256Hex(canonicalIrBytes(ir));
        List<BearIr.EffectPort> ports = new ArrayList<>(ir.block().effects().allow());
        ports.sort(Comparator.comparing(BearIr.EffectPort::port));
        List<BearIr.Invariant> invariants = new ArrayList<>();
        if (ir.block().invariants() != null) {
            invariants.addAll(ir.block().invariants());
        }
        List<BearIr.AllowedDep> allowedDeps = new ArrayList<>();
        if (ir.block().impl() != null && ir.block().impl().allowedDeps() != null) {
            allowedDeps.addAll(ir.block().impl().allowedDeps());
        }
        allowedDeps.sort(Comparator.comparing(BearIr.AllowedDep::maven));

        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"schemaVersion\":\"v1\",");
        out.append("\"surfaceVersion\":3,");
        out.append("\"target\":\"jvm\",");
        out.append("\"block\":\"").append(jsonEscape(ir.block().name())).append("\",");
        out.append("\"irHash\":\"").append(irHash).append("\",");
        out.append("\"generatorVersion\":\"jvm-v1\",");
        out.append("\"capabilities\":[");
        for (int i = 0; i < ports.size(); i++) {
            BearIr.EffectPort port = ports.get(i);
            if (i > 0) {
                out.append(",");
            }
            List<String> ops = capabilityOps(port);
            out.append("{\"name\":\"").append(jsonEscape(port.port())).append("\",\"ops\":[");
            for (int j = 0; j < ops.size(); j++) {
                if (j > 0) {
                    out.append(",");
                }
                out.append("\"").append(jsonEscape(ops.get(j))).append("\"");
            }
            out.append("]}");
        }
        out.append("],");
        out.append("\"allowedDeps\":[");
        for (int i = 0; i < allowedDeps.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            BearIr.AllowedDep dep = allowedDeps.get(i);
            out.append("{\"ga\":\"").append(jsonEscape(dep.maven())).append("\",\"version\":\"")
                .append(jsonEscape(dep.version())).append("\"}");
        }
        out.append("],");
        out.append("\"invariants\":[");
        boolean first = true;
        for (BearIr.Invariant invariant : invariants) {
            if (!first) {
                out.append(",");
            }
            first = false;
            out.append("{\"kind\":\"").append(jsonEscape(invariant.kind().name().toLowerCase(Locale.ROOT))).append("\",\"field\":\"")
                .append(jsonEscape(invariant.field()))
                .append("\"}");
        }
        out.append("]}");
        out.append("\n");
        return out.toString();
    }

    static String renderWiringManifest(
        String blockKey,
        String generatedPackageName,
        String blockName,
        String implPackageName,
        String implPackagePath,
        List<String> requiredEffectPorts,
        List<String> constructorPortParams,
        List<String> logicRequiredPorts,
        List<String> wrapperOwnedSemanticPorts,
        List<String> wrapperOwnedSemanticChecks,
        List<String> blockPortBindings
    ) {
        String entrypointFqcn = generatedPackageName + "." + blockName;
        String logicInterfaceFqcn = generatedPackageName + "." + blockName + "Logic";
        String implFqcn = implPackageName + "." + blockName + "Impl";
        String implSourcePath = "src/main/java/" + implPackagePath + "/" + blockName + "Impl.java";
        String blockRootSourceDir = Path.of("src/main/java/" + implPackagePath)
            .getParent()
            .toString()
            .replace('\\', '/');
        List<String> governedSourceRoots = new ArrayList<>();
        governedSourceRoots.add(blockRootSourceDir);
        governedSourceRoots.add("src/main/java/blocks/_shared");

        StringBuilder out = new StringBuilder();
        out.append("{");
        out.append("\"schemaVersion\":\"v3\",");
        out.append("\"blockKey\":\"").append(jsonEscape(blockKey)).append("\",");
        out.append("\"entrypointFqcn\":\"").append(jsonEscape(entrypointFqcn)).append("\",");
        out.append("\"logicInterfaceFqcn\":\"").append(jsonEscape(logicInterfaceFqcn)).append("\",");
        out.append("\"implFqcn\":\"").append(jsonEscape(implFqcn)).append("\",");
        out.append("\"implSourcePath\":\"").append(jsonEscape(implSourcePath)).append("\",");
        out.append("\"blockRootSourceDir\":\"").append(jsonEscape(blockRootSourceDir)).append("\",");
        out.append("\"governedSourceRoots\":[");
        for (int i = 0; i < governedSourceRoots.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("\"").append(jsonEscape(governedSourceRoots.get(i))).append("\"");
        }
        out.append("],");
        out.append("\"requiredEffectPorts\":[");
        for (int i = 0; i < requiredEffectPorts.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("\"").append(jsonEscape(requiredEffectPorts.get(i))).append("\"");
        }
        out.append("],");
        out.append("\"constructorPortParams\":[");
        for (int i = 0; i < constructorPortParams.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("\"").append(jsonEscape(constructorPortParams.get(i))).append("\"");
        }
        out.append("],");
        out.append("\"logicRequiredPorts\":[");
        for (int i = 0; i < logicRequiredPorts.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("\"").append(jsonEscape(logicRequiredPorts.get(i))).append("\"");
        }
        out.append("],");
        out.append("\"wrapperOwnedSemanticPorts\":[");
        for (int i = 0; i < wrapperOwnedSemanticPorts.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("\"").append(jsonEscape(wrapperOwnedSemanticPorts.get(i))).append("\"");
        }
        out.append("],");
        out.append("\"wrapperOwnedSemanticChecks\":[");
        for (int i = 0; i < wrapperOwnedSemanticChecks.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("\"").append(jsonEscape(wrapperOwnedSemanticChecks.get(i))).append("\"");
        }
        out.append("],");
        out.append("\"blockPortBindings\":[");
        for (int i = 0; i < blockPortBindings.size(); i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append(blockPortBindings.get(i));
        }
        out.append("]");
        out.append("}");
        out.append("\n");
        return out.toString();
    }

    private static List<String> capabilityOps(BearIr.EffectPort port) {
        List<String> ops = new ArrayList<>();
        BearIr.EffectPortKind kind = port.kind() == null ? BearIr.EffectPortKind.EXTERNAL : port.kind();
        if (kind == BearIr.EffectPortKind.BLOCK) {
            if (port.targetOps() != null) {
                ops.addAll(port.targetOps());
            }
        } else if (port.ops() != null) {
            ops.addAll(port.ops());
        }
        ops.sort(String::compareTo);
        return ops;
    }

    private static byte[] canonicalIrBytes(BearIr ir) {
        BearIrYamlEmitter emitter = new BearIrYamlEmitter();
        String yaml = emitter.toCanonicalYaml(ir);
        return yaml.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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


