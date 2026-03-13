package com.bear.kernel.target.node;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeDynamicImportDetectorTest {

    private final NodeDynamicImportDetector detector = new NodeDynamicImportDetector();

    @Test
    void detectsDynamicImport() {
        String source = "const module = import(\"./my-module\");";
        List<NodeDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(source);

        assertEquals(1, imports.size());
        assertEquals("./my-module", imports.get(0).specifier());
    }

    @Test
    void detectsDynamicImportWithNew() {
        String source = "const module = new import('./my-module');";
        List<NodeDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(source);

        assertEquals(1, imports.size());
        assertEquals("./my-module", imports.get(0).specifier());
    }

    @Test
    void returnsCorrectLineNumbers() {
        String source = """
            const a = import("./a");
            const b = import("./b");
            """;
        List<NodeDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(source);

        assertEquals(2, imports.size());
        assertEquals(1, imports.get(0).lineNumber());
        assertEquals(2, imports.get(1).lineNumber());
    }

    @Test
    void handlesEmptySource() {
        String source = "";
        List<NodeDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(source);

        assertEquals(0, imports.size());
    }

    @Test
    void distinguishesFromStaticImports() {
        String source = """
            import { x } from './static';
            const dynamic = import("./dynamic");
            """;
        List<NodeDynamicImportDetector.DynamicImport> imports = detector.detectDynamicImports(source);

        assertEquals(1, imports.size());
        assertEquals("./dynamic", imports.get(0).specifier());
    }
}
