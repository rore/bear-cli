package com.bear.kernel;

import com.bear.kernel.target.analyzer.*;
import com.bear.kernel.target.locator.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerProviderTypesTest {

    @Test
    void evidenceBundleEmptyReturnsAllEmptyLists() {
        EvidenceBundle bundle = EvidenceBundle.empty();
        assertTrue(bundle.imports().isEmpty());
        assertTrue(bundle.dependencies().isEmpty());
        assertTrue(bundle.ownership().isEmpty());
        assertTrue(bundle.references().isEmpty());
        assertTrue(bundle.findings().isEmpty());
    }

    @Test
    void evidenceBundleWithPopulatedLists() {
        CanonicalLocator locator = new CanonicalLocator(
            "repo", "project", "src/main.ts",
            new LocatorSymbol(SymbolKind.FUNCTION, "myFunc"), null
        );

        ImportEdge importEdge = new ImportEdge("moduleA", "moduleB", "./moduleB", locator);
        DependencyEdge depEdge = new DependencyEdge("lodash", "4.17.21", "runtime");
        OwnershipFact ownership = new OwnershipFact("moduleA", "team-alpha", locator);

        EvidenceBundle bundle = new EvidenceBundle(
            List.of(importEdge),
            List.of(depEdge),
            List.of(ownership),
            List.of(),
            List.of()
        );

        assertEquals(1, bundle.imports().size());
        assertEquals(importEdge, bundle.imports().get(0));
        assertEquals(1, bundle.dependencies().size());
        assertEquals(depEdge, bundle.dependencies().get(0));
        assertEquals(1, bundle.ownership().size());
        assertEquals(ownership, bundle.ownership().get(0));
        assertTrue(bundle.references().isEmpty());
        assertTrue(bundle.findings().isEmpty());
    }

    @Test
    void analyzerIdEquality() {
        AnalyzerId id1 = new AnalyzerId("test");
        AnalyzerId id2 = new AnalyzerId("test");
        AnalyzerId id3 = new AnalyzerId("other");

        assertEquals(id1, id2);
        assertNotEquals(id1, id3);
    }

    @Test
    void analyzerCapabilitiesAll() {
        AnalyzerCapabilities caps = AnalyzerCapabilities.all();
        assertTrue(caps.supportsImports());
        assertTrue(caps.supportsDependencies());
        assertTrue(caps.supportsOwnership());
        assertTrue(caps.supportsSymbols());
        assertTrue(caps.supportsSpans());
        assertTrue(caps.supportsReferences());
    }

    @Test
    void analyzerCapabilitiesNone() {
        AnalyzerCapabilities caps = AnalyzerCapabilities.none();
        assertFalse(caps.supportsImports());
        assertFalse(caps.supportsDependencies());
        assertFalse(caps.supportsOwnership());
        assertFalse(caps.supportsSymbols());
        assertFalse(caps.supportsSpans());
        assertFalse(caps.supportsReferences());
    }

    @Test
    void analysisOptionsDefaults() {
        AnalysisOptions options = AnalysisOptions.defaults();
        assertFalse(options.includeReferences());
        assertEquals(1, options.maxDepth());
    }

    @Test
    void importEdgeConstruction() {
        CanonicalLocator locator = new CanonicalLocator(
            "repo", "project", "src/main.ts",
            new LocatorSymbol(SymbolKind.FUNCTION, "myFunc"), null
        );
        ImportEdge edge = new ImportEdge("src/app", "src/lib", "./lib", locator);
        assertEquals("src/app", edge.sourceModule());
        assertEquals("src/lib", edge.targetModule());
        assertEquals("./lib", edge.specifier());
        assertEquals(locator, edge.locator());
    }

    @Test
    void analyzerFindingConstruction() {
        CanonicalLocator locator = new CanonicalLocator(
            "repo", "project", "src/main.ts",
            new LocatorSymbol(SymbolKind.FUNCTION, "myFunc"), null
        );
        AnalyzerFinding finding = new AnalyzerFinding("E001", "something went wrong", locator);
        assertEquals("E001", finding.code());
        assertEquals("something went wrong", finding.message());
        assertEquals(locator, finding.locator());
    }
}
