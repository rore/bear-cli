package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TypeScriptManifestGenerator {

    /**
     * Generates wiring.json manifest file.
     */
    public void generateWiringManifest(BearIr ir, Path outputDir, String blockKey) throws IOException {
        String content = renderWiringManifest(ir, blockKey);
        Path manifestFile = outputDir.resolve(blockKey + ".wiring.json");
        Files.createDirectories(outputDir);
        Files.writeString(manifestFile, content);
    }

    private String renderWiringManifest(BearIr ir, String blockKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": \"1\",\n");
        sb.append("  \"blockKey\": \"").append(blockKey).append("\",\n");
        sb.append("  \"targetId\": \"node\",\n");

        String blockName = TypeScriptLexicalSupport.deriveBlockName(blockKey);

        sb.append("  \"generatedPackage\": \"build/generated/bear/types/").append(blockKey).append("\",\n");
        sb.append("  \"implPackage\": \"src/blocks/").append(blockKey).append("/impl\",\n");

        // Wrappers
        sb.append("  \"wrappers\": [\n");
        List<String> wrapperList = new ArrayList<>();
        for (BearIr.Operation op : ir.block().operations()) {
            String opName = op.name();
            String wrapperName = blockName + "_" + opName;
            String wrapperPath = blockKey + "/" + blockName + "_" + opName + ".ts";
            wrapperList.add("    { \"operation\": \"" + opName + "\", \"wrapperClass\": \"" + wrapperName + "\", \"wrapperPath\": \"" + wrapperPath + "\" }");
        }
        sb.append(String.join(",\n", wrapperList));
        sb.append("\n  ],\n");

        // Ports
        sb.append("  \"ports\": [\n");
        List<String> portList = new ArrayList<>();
        if (ir.block().effects() != null && !ir.block().effects().allow().isEmpty()) {
            for (BearIr.EffectPort port : ir.block().effects().allow()) {
                String portName = port.port();
                String portInterface = TypeScriptLexicalSupport.kebabToPascal(portName) + "Port";
                String kind = port.kind() == BearIr.EffectPortKind.EXTERNAL ? "EXTERNAL" : "BLOCK";
                portList.add("    { \"name\": \"" + portName + "\", \"kind\": \"" + kind + "\", \"interface\": \"" + portInterface + "\" }");
            }
        }
        sb.append(String.join(",\n", portList));
        sb.append("\n  ]\n");

        sb.append("}\n");

        return sb.toString();
    }
}
