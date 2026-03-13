package com.bear.kernel.target.python;

import com.bear.kernel.ir.BearIr;

import java.util.Map;

/**
 * Maps BearIr field types to Python type annotations.
 */
public class PythonTypeMapper {

    private static final Map<BearIr.FieldType, String> TYPE_MAPPING = Map.of(
        BearIr.FieldType.STRING, "str",
        BearIr.FieldType.INT, "int",
        BearIr.FieldType.DECIMAL, "Decimal",
        BearIr.FieldType.BOOL, "bool",
        BearIr.FieldType.ENUM, "str" // Enums mapped to str in Phase P
    );

    /**
     * Maps a BearIr FieldType to a Python type annotation.
     */
    public static String mapType(BearIr.FieldType fieldType) {
        return TYPE_MAPPING.getOrDefault(fieldType, "Any");
    }
}
