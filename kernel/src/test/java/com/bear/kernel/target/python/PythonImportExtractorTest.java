package com.bear.kernel.target.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PythonImportExtractor.
 * Verifies AST-based extraction of all Python import patterns with correct line/column numbers.
 */
class PythonImportExtractorTest {

    private final PythonImportExtractor extractor = new PythonImportExtractor();

    @Test
    void extractsSimpleImport(@TempDir Path tempDir) throws IOException {
        String source = "import os\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals("os", imp.moduleName());
        assertFalse(imp.isRelative());
        assertEquals(1, imp.lineNumber());
        assertEquals(0, imp.columnNumber());
    }

    @Test
    void extractsImportWithAlias(@TempDir Path tempDir) throws IOException {
        String source = "import numpy as np\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals("numpy", imp.moduleName());
        assertFalse(imp.isRelative());
        assertEquals(1, imp.lineNumber());
    }

    @Test
    void extractsMultipleImportsOnSameLine(@TempDir Path tempDir) throws IOException {
        String source = "import os, sys, json\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(3, imports.size());
        assertEquals("os", imports.get(0).moduleName());
        assertEquals("sys", imports.get(1).moduleName());
        assertEquals("json", imports.get(2).moduleName());
        // All should be on line 1
        assertTrue(imports.stream().allMatch(i -> i.lineNumber() == 1));
    }

    @Test
    void extractsDottedImport(@TempDir Path tempDir) throws IOException {
        String source = "import xml.etree.ElementTree\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        assertEquals("xml.etree.ElementTree", imports.get(0).moduleName());
        assertFalse(imports.get(0).isRelative());
    }

    @Test
    void extractsFromImport(@TempDir Path tempDir) throws IOException {
        String source = "from os import path\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals("os", imp.moduleName());
        assertFalse(imp.isRelative());
        assertEquals(1, imp.lineNumber());
    }

    @Test
    void extractsFromImportWithAlias(@TempDir Path tempDir) throws IOException {
        String source = "from collections import OrderedDict as OD\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        assertEquals("collections", imports.get(0).moduleName());
        assertFalse(imports.get(0).isRelative());
    }

    @Test
    void extractsFromImportStar(@TempDir Path tempDir) throws IOException {
        String source = "from typing import *\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        assertEquals("typing", imports.get(0).moduleName());
        assertFalse(imports.get(0).isRelative());
    }

    @Test
    void extractsRelativeImportSingleDot(@TempDir Path tempDir) throws IOException {
        String source = "from . import utils\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals(".", imp.moduleName());
        assertTrue(imp.isRelative());
        assertEquals(1, imp.lineNumber());
    }

    @Test
    void extractsRelativeImportDoubleDot(@TempDir Path tempDir) throws IOException {
        String source = "from .. import parent_module\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals("..", imp.moduleName());
        assertTrue(imp.isRelative());
    }

    @Test
    void extractsRelativeImportWithModule(@TempDir Path tempDir) throws IOException {
        String source = "from .submodule import function\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals(".submodule", imp.moduleName());
        assertTrue(imp.isRelative());
    }

    @Test
    void extractsRelativeImportTwoLevelsWithModule(@TempDir Path tempDir) throws IOException {
        String source = "from ..parent.sibling import Class\n";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(1, imports.size());
        ImportStatement imp = imports.get(0);
        assertEquals("..parent.sibling", imp.moduleName());
        assertTrue(imp.isRelative());
    }

    @Test
    void extractsMultipleImportsWithCorrectLineNumbers(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys
            from typing import List
            from . import utils
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(4, imports.size());
        assertEquals(1, imports.get(0).lineNumber());
        assertEquals(2, imports.get(1).lineNumber());
        assertEquals(3, imports.get(2).lineNumber());
        assertEquals(4, imports.get(3).lineNumber());
    }

    @Test
    void extractsImportsWithCodeBetween(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            
            def foo():
                pass
            
            from typing import List
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(2, imports.size());
        assertEquals("os", imports.get(0).moduleName());
        assertEquals("typing", imports.get(1).moduleName());
        assertEquals(1, imports.get(0).lineNumber());
        assertEquals(6, imports.get(1).lineNumber());
    }

    @Test
    void handlesEmptyFile(@TempDir Path tempDir) throws IOException {
        String source = "";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertTrue(imports.isEmpty());
    }

    @Test
    void handlesFileWithNoImports(@TempDir Path tempDir) throws IOException {
        String source = """
            def main():
                print("Hello, world!")
            
            if __name__ == "__main__":
                main()
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertTrue(imports.isEmpty());
    }

    @Test
    void handlesFileWithOnlyComments(@TempDir Path tempDir) throws IOException {
        String source = """
            # This is a comment
            # Another comment
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertTrue(imports.isEmpty());
    }

    @Test
    void extractsAllImportPatterns(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys as system
            import xml.etree.ElementTree
            from typing import List
            from collections import OrderedDict as OD
            from json import *
            from . import utils
            from .. import parent
            from .submodule import helper
            from ..sibling.module import Class
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(10, imports.size());
        
        // Verify absolute imports
        assertEquals("os", imports.get(0).moduleName());
        assertFalse(imports.get(0).isRelative());
        
        assertEquals("sys", imports.get(1).moduleName());
        assertFalse(imports.get(1).isRelative());
        
        assertEquals("xml.etree.ElementTree", imports.get(2).moduleName());
        assertFalse(imports.get(2).isRelative());
        
        assertEquals("typing", imports.get(3).moduleName());
        assertFalse(imports.get(3).isRelative());
        
        assertEquals("collections", imports.get(4).moduleName());
        assertFalse(imports.get(4).isRelative());
        
        assertEquals("json", imports.get(5).moduleName());
        assertFalse(imports.get(5).isRelative());
        
        // Verify relative imports
        assertEquals(".", imports.get(6).moduleName());
        assertTrue(imports.get(6).isRelative());
        
        assertEquals("..", imports.get(7).moduleName());
        assertTrue(imports.get(7).isRelative());
        
        assertEquals(".submodule", imports.get(8).moduleName());
        assertTrue(imports.get(8).isRelative());
        
        assertEquals("..sibling.module", imports.get(9).moduleName());
        assertTrue(imports.get(9).isRelative());
    }

    @Test
    void handlesInvalidSyntaxGracefully(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            this is not valid python syntax
            from typing import List
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        // Should not throw, but return empty list due to syntax error
        List<ImportStatement> imports = extractor.extractImports(file, source);
        
        // Python AST parser will fail on syntax error, returning empty list
        assertTrue(imports.isEmpty());
    }

    @Test
    void extractsImportsFromRealWorldExample(@TempDir Path tempDir) throws IOException {
        String source = """
            #!/usr/bin/env python3
            \"\"\"Module docstring.\"\"\"
            
            import os
            import sys
            from typing import List, Dict, Optional
            from pathlib import Path
            
            from .models import User, Session
            from ..utils import logger
            from ...config import settings
            
            
            class MyClass:
                def __init__(self):
                    pass
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        // Note: "from typing import List, Dict, Optional" creates ONE import for module "typing"
        // We track the module being imported, not the individual names
        assertEquals(7, imports.size());
        
        // Check absolute imports
        assertEquals("os", imports.get(0).moduleName());
        assertEquals("sys", imports.get(1).moduleName());
        assertEquals("typing", imports.get(2).moduleName());
        assertEquals("pathlib", imports.get(3).moduleName());
        
        // Check relative imports
        assertEquals(".models", imports.get(4).moduleName());
        assertTrue(imports.get(4).isRelative());
        
        assertEquals("..utils", imports.get(5).moduleName());
        assertTrue(imports.get(5).isRelative());
        
        assertEquals("...config", imports.get(6).moduleName());
        assertTrue(imports.get(6).isRelative());
    }
}
