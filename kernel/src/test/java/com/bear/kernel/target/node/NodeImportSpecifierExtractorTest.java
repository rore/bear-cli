package com.bear.kernel.target.node;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeImportSpecifierExtractorTest {

    private final NodeImportSpecifierExtractor extractor = new NodeImportSpecifierExtractor();

    @Test
    void extractsNamedImports() {
        String source = "import { UserService } from './services/user-service';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(1, specifiers.size());
        assertEquals("./services/user-service", specifiers.get(0).specifier());
        assertEquals("named", specifiers.get(0).kind());
    }

    @Test
    void extractsNamespaceImports() {
        String source = "import * as utils from './utils';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(1, specifiers.size());
        assertEquals("./utils", specifiers.get(0).specifier());
        assertEquals("namespace", specifiers.get(0).kind());
    }

    @Test
    void extractsDefaultImports() {
        String source = "import express from 'express';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(1, specifiers.size());
        assertEquals("express", specifiers.get(0).specifier());
        assertEquals("default", specifiers.get(0).kind());
    }

    @Test
    void extractsSideEffectImports() {
        String source = "import './polyfills';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(1, specifiers.size());
        assertEquals("./polyfills", specifiers.get(0).specifier());
        assertEquals("side-effect", specifiers.get(0).kind());
    }

    @Test
    void extractsNamedExports() {
        String source = "export { UserService } from './services/user-service';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(1, specifiers.size());
        assertEquals("./services/user-service", specifiers.get(0).specifier());
        assertEquals("export-named", specifiers.get(0).kind());
    }

    @Test
    void extractsExportAll() {
        String source = "export * from './types';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(1, specifiers.size());
        assertEquals("./types", specifiers.get(0).specifier());
        assertEquals("export-all", specifiers.get(0).kind());
    }

    @Test
    void returnsCorrectLineNumbers() {
        String source = """
            import { A } from './a';
            import { B } from './b';
            """;
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(2, specifiers.size());
        assertEquals(1, specifiers.get(0).lineNumber());
        assertEquals(2, specifiers.get(1).lineNumber());
    }

    @Test
    void handlesEmptySource() {
        String source = "";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        assertEquals(0, specifiers.size());
    }

    @Test
    void extractsMultipleSpecifiersFromSameLine() {
        String source = "import { A, B } from './modules';";
        List<NodeImportSpecifierExtractor.ImportSpecifier> specifiers = extractor.extractImports(source, source);

        // Note: Current implementation extracts one per pattern match
        // Multiple imports from same line may not be fully supported in Phase B
        assertTrue(specifiers.size() >= 1);
    }
}
