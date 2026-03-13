package com.bear.kernel.target.node;

import com.bear.kernel.ir.BearIr;

import java.util.Map;

public class TypeScriptTypeMapper {

    private static final Map<BearIr.FieldType, String> TYPE_MAPPING = Map.of(
        BearIr.FieldType.STRING, "string",
        BearIr.FieldType.INT, "number",
        BearIr.FieldType.DECIMAL, "string",
        BearIr.FieldType.BOOL, "boolean",
        BearIr.FieldType.ENUM, "string" // Enums mapped to string in Phase B
    );

    /**
     * Maps a BearIr FieldType to a TypeScript type.
     */
    public static String mapType(BearIr.FieldType fieldType) {
        return TYPE_MAPPING.getOrDefault(fieldType, "unknown");
    }
}
