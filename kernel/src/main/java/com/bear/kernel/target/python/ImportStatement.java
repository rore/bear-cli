package com.bear.kernel.target.python;

/**
 * Represents a Python import statement extracted from source code via AST.
 * 
 * @param moduleName The module being imported (e.g., "os", "sys", ".submodule", "..parent")
 * @param isRelative True if this is a relative import (starts with . or ..)
 * @param lineNumber Line number in source (1-indexed)
 * @param columnNumber Column number in source (0-indexed, as per Python AST)
 */
public record ImportStatement(String moduleName, boolean isRelative, int lineNumber, int columnNumber) {
}
