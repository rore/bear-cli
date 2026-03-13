package com.bear.kernel.target.python.properties;

import com.bear.kernel.target.BoundaryBypassFinding;
import com.bear.kernel.target.WiringManifest;
import com.bear.kernel.target.node.BoundaryDecision;
import com.bear.kernel.target.python.ImportStatement;
import com.bear.kernel.target.python.PythonDynamicImportDetector;
import com.bear.kernel.target.python.PythonImportExtractor;
import com.bear.kernel.target.python.PythonImportBoundaryResolver;
import com.bear.kernel.target.python.PythonImportContainmentScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for import containment.
 * Feature: phase-p-python-scan-only
 */
class ImportContainmentProperties {

    private final PythonImportExtractor extractor = new PythonImportExtractor();
    private final PythonDynamicImportDetector dynamicDetector = new PythonDynamicImportDetector();
    private final PythonImportBoundaryResolver resolver = new PythonImportBoundaryResolver();

    /**
     * Property 11: Relative import resolving within same block root -> no findings.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void sameBlockRelativeImport_noFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path servicesDir = Files.createDirectories(blockRoot.resolve("services"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(servicesDir.resolve("__init__.py"), "");
        Files.writeString(servicesDir.resolve("auth.py"), "from . import helper\n");
        Files.writeString(servicesDir.resolve("helper.py"), "# helper\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "Same block relative import should produce no findings");
    }

    /**
     * Property 12: Relative import resolving to src/blocks/_shared/ -> no findings.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void sharedRelativeImport_noFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(sharedRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("service.py"), "from .._shared import utils\n");
        Files.writeString(sharedRoot.resolve("utils.py"), "# utils\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "Import from _shared should produce no findings");
    }

    /**
     * Property 13: Relative import resolving to build/generated/bear/ -> no findings.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void bearGeneratedRelativeImport_noFindings(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path implDir = Files.createDirectories(blockRoot.resolve("impl"));
        Path generatedDir = Files.createDirectories(tempDir.resolve("build/generated/bear/user-auth"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(implDir.resolve("__init__.py"), "");
        Files.writeString(generatedDir.resolve("user_auth_ports.py"), "# ports\n");
        
        // Use absolute import for BEAR-generated modules (more realistic)
        Files.writeString(implDir.resolve("user_auth_impl.py"), 
            "from build.generated.bear.user_auth.user_auth_ports import DatabasePort\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertTrue(findings.isEmpty(), "BEAR-generated import should produce no findings");
    }

    /**
     * Property 14: Relative import escaping block root -> finding, exit 7, CODE=BOUNDARY_BYPASS.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void escapingBlockRootImport_finding(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path servicesDir = Files.createDirectories(blockRoot.resolve("services"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(servicesDir.resolve("__init__.py"), "");
        
        // Escape to nongoverned source
        Files.writeString(servicesDir.resolve("bad.py"), "from ....utils import helper\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty(), "Escaping block root should produce finding");
        assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
    }

    /**
     * Property 15: Import resolving to sibling block -> finding, exit 7, CODE=BOUNDARY_BYPASS.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void siblingBlockImport_finding(@TempDir Path tempDir) throws IOException {
        Path userAuthRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path paymentRoot = Files.createDirectories(tempDir.resolve("src/blocks/payment"));
        
        Files.writeString(userAuthRoot.resolve("__init__.py"), "");
        Files.writeString(paymentRoot.resolve("__init__.py"), "");
        
        // Import from sibling block
        Files.writeString(userAuthRoot.resolve("bad.py"), "from ..payment import processor\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty(), "Sibling block import should produce finding");
        assertEquals("BOUNDARY_BYPASS", findings.get(0).rule());
    }

    /**
     * Property 16: Third-party package import from governed root -> finding, exit 7, CODE=THIRD_PARTY_IMPORT.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void thirdPartyImport_finding(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("bad.py"), "import requests\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty(), "Third-party import should produce finding");
        assertEquals("THIRD_PARTY_IMPORT", findings.get(0).rule());
    }

    /**
     * Property 17: Any BOUNDARY_BYPASS finding -> includes repo-relative path and import module name.
     * Validates: Requirements - Import Containment Enforcement (Finding Locator Completeness)
     */
    @Test
    void findingIncludesPathAndModule(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(blockRoot.resolve("bad_service.py"), "import requests\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty());
        BoundaryBypassFinding finding = findings.get(0);
        
        // Check path is repo-relative
        assertTrue(finding.path().contains("src/blocks/user-auth/bad_service.py"), 
            "Finding should include repo-relative path");
        
        // Check detail includes module name
        assertTrue(finding.detail().contains("requests"), 
            "Finding detail should include import module name");
        
        // Check detail includes line number
        assertTrue(finding.detail().contains("line"), 
            "Finding detail should include line number");
    }

    /**
     * Property 18: _shared file importing a block root -> finding, exit 7, CODE=BOUNDARY_BYPASS.
     * Validates: Requirements - Import Containment Enforcement
     */
    @Test
    void sharedImportsBlock_finding(@TempDir Path tempDir) throws IOException {
        Path blockRoot = Files.createDirectories(tempDir.resolve("src/blocks/user-auth"));
        Path sharedRoot = Files.createDirectories(tempDir.resolve("src/blocks/_shared"));
        
        Files.writeString(blockRoot.resolve("__init__.py"), "");
        Files.writeString(sharedRoot.resolve("__init__.py"), "");
        
        // _shared trying to import from block
        Files.writeString(sharedRoot.resolve("bad_util.py"), "from ..user_auth import service\n");

        List<WiringManifest> manifests = List.of(makeManifest("user-auth"));
        List<BoundaryBypassFinding> findings = PythonImportContainmentScanner.scan(tempDir, manifests);

        assertFalse(findings.isEmpty(), "_shared importing block should produce finding");
        assertEquals("SHARED_IMPORTS_BLOCK", findings.get(0).rule());
    }

    /**
     * Property 19: Any Python source -> all static import statements extracted with locations via AST.
     * Validates: Requirements - AST-First Analysis
     */
    @Test
    void extractsAllImportsWithLocations_simpleImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys
            from typing import List
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(3, imports.size());
        // All imports should have valid line numbers (1-indexed)
        assertTrue(imports.stream().allMatch(i -> i.lineNumber() >= 1));
        // All imports should have valid column numbers (0-indexed)
        assertTrue(imports.stream().allMatch(i -> i.columnNumber() >= 0));
        // All imports should have non-empty module names
        assertTrue(imports.stream().allMatch(i -> !i.moduleName().isEmpty()));
    }

    @Test
    void extractsAllImportsWithLocations_relativeImports(@TempDir Path tempDir) throws IOException {
        String source = """
            from . import utils
            from .. import parent
            from .submodule import helper
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(3, imports.size());
        // All relative imports should be marked as relative
        assertTrue(imports.stream().allMatch(ImportStatement::isRelative));
        // All should have valid locations
        assertTrue(imports.stream().allMatch(i -> i.lineNumber() >= 1));
        assertTrue(imports.stream().allMatch(i -> i.columnNumber() >= 0));
    }

    @Test
    void extractsAllImportsWithLocations_mixedImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            from typing import List, Dict
            from . import local
            import sys as system
            from ..parent import module
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(5, imports.size());
        // Check that line numbers are sequential
        assertEquals(1, imports.get(0).lineNumber());
        assertEquals(2, imports.get(1).lineNumber());
        assertEquals(3, imports.get(2).lineNumber());
        assertEquals(4, imports.get(3).lineNumber());
        assertEquals(5, imports.get(4).lineNumber());
        
        // Check relative vs absolute
        assertFalse(imports.get(0).isRelative()); // import os
        assertFalse(imports.get(1).isRelative()); // from typing
        assertTrue(imports.get(2).isRelative());  // from .
        assertFalse(imports.get(3).isRelative()); // import sys
        assertTrue(imports.get(4).isRelative());  // from ..
    }

    @Test
    void extractsAllImportsWithLocations_complexFile(@TempDir Path tempDir) throws IOException {
        String source = """
            #!/usr/bin/env python3
            \"\"\"Module docstring.\"\"\"
            
            import os
            import sys
            from pathlib import Path
            
            # Some code
            def foo():
                pass
            
            from typing import Optional
            from . import utils
            
            class MyClass:
                pass
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        // Note: "from pathlib import Path" creates ONE import for module "pathlib"
        assertEquals(5, imports.size());
        // All imports should have valid locations
        assertTrue(imports.stream().allMatch(i -> i.lineNumber() >= 1));
        assertTrue(imports.stream().allMatch(i -> i.columnNumber() >= 0));
        // Module names should not be empty
        assertTrue(imports.stream().allMatch(i -> !i.moduleName().isEmpty()));
    }

    @Test
    void extractsAllImportsWithLocations_emptyFile(@TempDir Path tempDir) throws IOException {
        String source = "";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertTrue(imports.isEmpty(), "empty file should have no imports");
    }

    @Test
    void extractsAllImportsWithLocations_noImports(@TempDir Path tempDir) throws IOException {
        String source = """
            def main():
                print("Hello")
            
            if __name__ == "__main__":
                main()
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertTrue(imports.isEmpty(), "file with no imports should return empty list");
    }

    @Test
    void extractsAllImportsWithLocations_allPatterns(@TempDir Path tempDir) throws IOException {
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
        
        // Verify all have valid locations
        for (int i = 0; i < imports.size(); i++) {
            ImportStatement imp = imports.get(i);
            assertEquals(i + 1, imp.lineNumber(), "Import " + i + " should be on line " + (i + 1));
            assertTrue(imp.columnNumber() >= 0, "Import " + i + " should have valid column");
            assertFalse(imp.moduleName().isEmpty(), "Import " + i + " should have module name");
        }
        
        // Verify absolute vs relative
        assertFalse(imports.get(0).isRelative()); // import os
        assertFalse(imports.get(1).isRelative()); // import sys as system
        assertFalse(imports.get(2).isRelative()); // import xml.etree.ElementTree
        assertFalse(imports.get(3).isRelative()); // from typing
        assertFalse(imports.get(4).isRelative()); // from collections
        assertFalse(imports.get(5).isRelative()); // from json
        assertTrue(imports.get(6).isRelative());  // from .
        assertTrue(imports.get(7).isRelative());  // from ..
        assertTrue(imports.get(8).isRelative());  // from .submodule
        assertTrue(imports.get(9).isRelative());  // from ..sibling.module
    }

    @Test
    void extractsAllImportsWithLocations_multipleBlockScenario(@TempDir Path tempDir) throws IOException {
        // Simulate a real block file with typical imports
        String source = """
            from typing import Protocol
            from dataclasses import dataclass
            
            from .user_auth_ports import DatabasePort
            from ..._shared.utils import logger
            
            @dataclass
            class LoginRequest:
                username: str
                password: str
            """;
        Path file = tempDir.resolve("user_auth_logic.py");
        Files.writeString(file, source);

        List<ImportStatement> imports = extractor.extractImports(file, source);

        assertEquals(4, imports.size());
        
        // Check absolute imports
        assertEquals("typing", imports.get(0).moduleName());
        assertFalse(imports.get(0).isRelative());
        
        assertEquals("dataclasses", imports.get(1).moduleName());
        assertFalse(imports.get(1).isRelative());
        
        // Check relative imports
        assertEquals(".user_auth_ports", imports.get(2).moduleName());
        assertTrue(imports.get(2).isRelative());
        
        assertEquals("..._shared.utils", imports.get(3).moduleName());
        assertTrue(imports.get(3).isRelative());
        
        // All should have valid locations
        assertTrue(imports.stream().allMatch(i -> i.lineNumber() >= 1));
        assertTrue(imports.stream().allMatch(i -> i.columnNumber() >= 0));
    }

    /**
     * Property 20: Any Python source with importlib.import_module() -> all dynamic imports identified.
     * Validates: Requirements - AST-First Analysis (Dynamic Import Detection)
     */
    @Test
    void identifiesAllDynamicImports_importlibImportModule(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            
            module = importlib.import_module("my_module")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(1, dynamicImports.size());
        assertEquals("importlib.import_module", dynamicImports.get(0).pattern());
        assertTrue(dynamicImports.get(0).lineNumber() >= 1);
        assertTrue(dynamicImports.get(0).columnNumber() >= 0);
    }

    @Test
    void identifiesAllDynamicImports_dunderImport(@TempDir Path tempDir) throws IOException {
        String source = """
            module = __import__("os")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(1, dynamicImports.size());
        assertEquals("__import__", dynamicImports.get(0).pattern());
        assertTrue(dynamicImports.get(0).lineNumber() >= 1);
        assertTrue(dynamicImports.get(0).columnNumber() >= 0);
    }

    @Test
    void identifiesAllDynamicImports_specFromFileLocation(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib.util
            
            spec = importlib.util.spec_from_file_location("module", "/path/to/module.py")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(1, dynamicImports.size());
        assertEquals("importlib.util.spec_from_file_location", dynamicImports.get(0).pattern());
        assertTrue(dynamicImports.get(0).lineNumber() >= 1);
        assertTrue(dynamicImports.get(0).columnNumber() >= 0);
    }

    @Test
    void identifiesAllDynamicImports_multipleDynamicImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            import importlib.util
            
            mod1 = importlib.import_module("module1")
            mod2 = __import__("module2")
            spec = importlib.util.spec_from_file_location("module3", "/path/to/module3.py")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(3, dynamicImports.size());
        // All should have valid locations
        assertTrue(dynamicImports.stream().allMatch(d -> d.lineNumber() >= 1));
        assertTrue(dynamicImports.stream().allMatch(d -> d.columnNumber() >= 0));
        // All should have non-empty pattern names
        assertTrue(dynamicImports.stream().allMatch(d -> !d.pattern().isEmpty()));
    }

    @Test
    void identifiesAllDynamicImports_mixedStaticAndDynamic(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys
            from typing import List
            
            # Dynamic imports
            mod1 = importlib.import_module("dynamic1")
            mod2 = __import__("dynamic2")
            
            # More static imports
            from pathlib import Path
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        // Should only detect dynamic imports, not static ones
        assertEquals(2, dynamicImports.size());
        assertEquals("importlib.import_module", dynamicImports.get(0).pattern());
        assertEquals("__import__", dynamicImports.get(1).pattern());
    }

    @Test
    void identifiesAllDynamicImports_inFunctionBody(@TempDir Path tempDir) throws IOException {
        String source = """
            def load_plugin(name):
                import importlib
                return importlib.import_module(name)
            
            def load_builtin(name):
                return __import__(name)
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(2, dynamicImports.size());
        assertEquals("importlib.import_module", dynamicImports.get(0).pattern());
        assertEquals("__import__", dynamicImports.get(1).pattern());
        // Both should have valid locations
        assertTrue(dynamicImports.stream().allMatch(d -> d.lineNumber() >= 1));
    }

    @Test
    void identifiesAllDynamicImports_emptyFile(@TempDir Path tempDir) throws IOException {
        String source = "";
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertTrue(dynamicImports.isEmpty(), "empty file should have no dynamic imports");
    }

    @Test
    void identifiesAllDynamicImports_noDynamicImports(@TempDir Path tempDir) throws IOException {
        String source = """
            import os
            import sys
            from typing import List
            
            def main():
                print("Hello")
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertTrue(dynamicImports.isEmpty(), "file with only static imports should have no dynamic imports");
    }

    @Test
    void identifiesAllDynamicImports_allPatterns(@TempDir Path tempDir) throws IOException {
        String source = """
            import importlib
            import importlib.util
            
            # Pattern 1: importlib.import_module
            mod1 = importlib.import_module("package.module")
            
            # Pattern 2: __import__
            mod2 = __import__("os.path")
            
            # Pattern 3: importlib.util.spec_from_file_location
            spec = importlib.util.spec_from_file_location("mymodule", "/path/to/mymodule.py")
            
            # Static imports should not be detected
            from typing import List
            import sys
            """;
        Path file = tempDir.resolve("test.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(3, dynamicImports.size());
        
        // Verify all patterns detected
        assertEquals("importlib.import_module", dynamicImports.get(0).pattern());
        assertEquals("__import__", dynamicImports.get(1).pattern());
        assertEquals("importlib.util.spec_from_file_location", dynamicImports.get(2).pattern());
        
        // Verify all have valid locations
        assertTrue(dynamicImports.stream().allMatch(d -> d.lineNumber() >= 1));
        assertTrue(dynamicImports.stream().allMatch(d -> d.columnNumber() >= 0));
    }

    @Test
    void identifiesAllDynamicImports_realWorldScenario(@TempDir Path tempDir) throws IOException {
        String source = """
            #!/usr/bin/env python3
            \"\"\"Plugin loader module.\"\"\"
            
            import importlib
            from typing import Dict, Any
            from pathlib import Path
            
            class PluginLoader:
                def __init__(self):
                    self.plugins: Dict[str, Any] = {}
                
                def load_plugin(self, name: str):
                    # Dynamic import for plugin loading
                    module = importlib.import_module(f"plugins.{name}")
                    self.plugins[name] = module
                    return module
                
                def load_builtin(self, name: str):
                    # Alternative dynamic import
                    return __import__(name)
            """;
        Path file = tempDir.resolve("plugin_loader.py");
        Files.writeString(file, source);

        List<PythonDynamicImportDetector.DynamicImport> dynamicImports = dynamicDetector.detectDynamicImports(file, source);

        assertEquals(2, dynamicImports.size());
        
        // Check patterns
        assertEquals("importlib.import_module", dynamicImports.get(0).pattern());
        assertEquals("__import__", dynamicImports.get(1).pattern());
        
        // All should have valid locations
        assertTrue(dynamicImports.stream().allMatch(d -> d.lineNumber() >= 1));
        assertTrue(dynamicImports.stream().allMatch(d -> d.columnNumber() >= 0));
    }

    /**
     * Property 21: Resolved path within same block root -> PythonImportBoundaryResolver returns ALLOWED.
     * Validates: Requirements - AST-First Analysis (Boundary Resolution)
     */
    @Test
    void sameBlockRootImport_allowed(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, ".utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass(), "Same block root import should be allowed");
    }

    /**
     * Property 22: Resolved path within _shared -> PythonImportBoundaryResolver returns ALLOWED.
     * Validates: Requirements - AST-First Analysis (Boundary Resolution)
     */
    @Test
    void sharedRootImport_allowed(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot, sharedRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, ".._shared.utils", true, governedRoots, projectRoot);

        assertTrue(decision.pass(), "Import from _shared should be allowed");
    }

    /**
     * Property 23: Resolved path within build/generated/bear/ -> PythonImportBoundaryResolver returns ALLOWED.
     * Validates: Requirements - AST-First Analysis (Boundary Resolution)
     */
    @Test
    void bearGeneratedImport_allowed(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("impl/user_auth_impl.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import to generated artifact
        // From src/blocks/user-auth/impl/ we need 5 dots to go up 4 levels to project root
        BoundaryDecision decision = resolver.resolve(importingFile, ".....build.generated.bear.user_auth.user_auth_ports", true, governedRoots, projectRoot);

        assertTrue(decision.pass(), "BEAR-generated import should be allowed");
    }

    @Test
    void bearGeneratedAbsoluteImport_allowed(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("impl/user_auth_impl.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of BEAR-generated module
        BoundaryDecision decision = resolver.resolve(importingFile, "build.generated.bear.user_auth.user_auth_ports", false, governedRoots, projectRoot);

        assertTrue(decision.pass(), "BEAR-generated absolute import should be allowed");
    }

    /**
     * Property 24: Resolved path in sibling block -> PythonImportBoundaryResolver returns FAIL.
     * Validates: Requirements - AST-First Analysis (Boundary Resolution)
     */
    @Test
    void siblingBlockImport_fail(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path siblingRoot = projectRoot.resolve("src/blocks/payment");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot, siblingRoot);

        BoundaryDecision decision = resolver.resolve(importingFile, "..payment.processor", true, governedRoots, projectRoot);

        assertTrue(decision.isFail(), "Sibling block import should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    /**
     * Property 25: Resolved path in nongoverned source -> PythonImportBoundaryResolver returns FAIL.
     * Validates: Requirements - AST-First Analysis (Boundary Resolution)
     */
    @Test
    void nongovernedSourceImport_fail(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import escaping to nongoverned source
        BoundaryDecision decision = resolver.resolve(importingFile, "...utils", true, governedRoots, projectRoot);

        assertTrue(decision.isFail(), "Nongoverned source import should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    /**
     * Property 26: Resolved path escaping block root -> PythonImportBoundaryResolver returns FAIL.
     * Validates: Requirements - AST-First Analysis (Boundary Resolution)
     */
    @Test
    void escapingBlockRootImport_fail(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("handlers/login.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Relative import escaping block root
        BoundaryDecision decision = resolver.resolve(importingFile, "....nongoverned.module", true, governedRoots, projectRoot);

        assertTrue(decision.isFail(), "Escaping block root should fail");
        assertEquals("BOUNDARY_BYPASS", decision.failureReason());
    }

    @Test
    void sharedImportsBlock_fail(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = sharedRoot.resolve("utils.py");
        Set<Path> governedRoots = Set.of(sharedRoot, blockRoot);

        // _shared trying to import from a block
        BoundaryDecision decision = resolver.resolve(importingFile, "..user_auth.service", true, governedRoots, projectRoot);

        assertTrue(decision.isFail(), "_shared importing block should fail");
        assertEquals("SHARED_IMPORTS_BLOCK", decision.failureReason());
    }

    @Test
    void thirdPartyImport_fail(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Absolute import of third-party package
        BoundaryDecision decision = resolver.resolve(importingFile, "requests", false, governedRoots, projectRoot);

        assertTrue(decision.isFail(), "Third-party import should fail");
        assertEquals("THIRD_PARTY_IMPORT", decision.failureReason());
    }

    /**
     * Property 27: Any FAIL from PythonImportBoundaryResolver -> uses CanonicalLocator for structured locator.
     * Validates: Requirements - AST-First Analysis (CanonicalLocator usage)
     * 
     * Note: This property is validated by the scanner integration tests which create
     * BoundaryBypassFinding objects with CanonicalLocator. The resolver itself returns
     * BoundaryDecision with failure reasons, and the scanner uses those to construct
     * findings with CanonicalLocator.
     */
    @Test
    void failureReasonProvided_forStructuredLocator(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Test various failure scenarios provide structured reasons
        BoundaryDecision siblingFail = resolver.resolve(importingFile, "..payment.processor", true, governedRoots, projectRoot);
        assertTrue(siblingFail.isFail());
        assertNotNull(siblingFail.failureReason(), "Failure reason should be provided for CanonicalLocator");
        assertEquals("BOUNDARY_BYPASS", siblingFail.failureReason());

        BoundaryDecision thirdPartyFail = resolver.resolve(importingFile, "requests", false, governedRoots, projectRoot);
        assertTrue(thirdPartyFail.isFail());
        assertNotNull(thirdPartyFail.failureReason(), "Failure reason should be provided for CanonicalLocator");
        assertEquals("THIRD_PARTY_IMPORT", thirdPartyFail.failureReason());

        Path sharedRoot = projectRoot.resolve("src/blocks/_shared");
        Path sharedFile = sharedRoot.resolve("utils.py");
        Set<Path> governedRootsWithShared = Set.of(blockRoot, sharedRoot);
        BoundaryDecision sharedFail = resolver.resolve(sharedFile, "..user_auth.service", true, governedRootsWithShared, projectRoot);
        assertTrue(sharedFail.isFail());
        assertNotNull(sharedFail.failureReason(), "Failure reason should be provided for CanonicalLocator");
        assertEquals("SHARED_IMPORTS_BLOCK", sharedFail.failureReason());
    }

    @Test
    void stdlibImport_allowed(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Test various stdlib modules
        assertTrue(resolver.resolve(importingFile, "os", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "sys", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "typing", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "dataclasses", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "pathlib", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "json", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "collections", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "itertools", false, governedRoots, projectRoot).pass());
    }

    @Test
    void stdlibSubmoduleImport_allowed(@TempDir Path tempDir) {
        Path projectRoot = tempDir;
        Path blockRoot = projectRoot.resolve("src/blocks/user-auth");
        Path importingFile = blockRoot.resolve("service.py");
        Set<Path> governedRoots = Set.of(blockRoot);

        // Test stdlib submodules
        assertTrue(resolver.resolve(importingFile, "os.path", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "collections.abc", false, governedRoots, projectRoot).pass());
        assertTrue(resolver.resolve(importingFile, "xml.etree.ElementTree", false, governedRoots, projectRoot).pass());
    }

    // Helper method for creating test manifests
    private WiringManifest makeManifest(String blockKey) {
        return new WiringManifest(
            "1", blockKey, blockKey, blockKey + "Logic", blockKey + "Impl",
            "src/blocks/" + blockKey + "/impl/" + blockKey + "_impl.py",
            "src/blocks/" + blockKey,
            List.of("src/blocks/" + blockKey),
            List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }
}
