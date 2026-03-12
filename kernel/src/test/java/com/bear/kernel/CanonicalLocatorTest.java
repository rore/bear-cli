package com.bear.kernel;

import com.bear.kernel.target.locator.CanonicalLocator;
import com.bear.kernel.target.locator.LocatorSpan;
import com.bear.kernel.target.locator.LocatorSymbol;
import com.bear.kernel.target.locator.SymbolKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CanonicalLocatorTest {

    @Test
    void fullConstruction() {
        LocatorSymbol symbol = new LocatorSymbol(SymbolKind.FUNCTION, "doStuff");
        LocatorSpan span = new LocatorSpan(10, 1, 20, 5);
        CanonicalLocator locator = new CanonicalLocator("myRepo", "myProject", "myModule", symbol, span);

        assertEquals("myRepo/myProject:myModule:FUNCTION:doStuff@10", locator.toString());
        assertEquals("myModule:FUNCTION:doStuff", locator.identityKey());
    }

    @Test
    void nullSymbolAndSpan() {
        CanonicalLocator locator = new CanonicalLocator("repo", "proj", "mod", null, null);

        assertEquals("repo/proj:mod", locator.toString());
        assertEquals("mod", locator.identityKey());
    }

    @Test
    void symbolWithNullName() {
        LocatorSymbol symbol = new LocatorSymbol(SymbolKind.CLASS, null);
        CanonicalLocator locator = new CanonicalLocator("repo", "proj", "mod", symbol, null);

        assertEquals("repo/proj:mod", locator.toString());
        assertEquals("mod", locator.identityKey());
    }

    @Test
    void spanWithAllNulls() {
        LocatorSpan span = new LocatorSpan(null, null, null, null);
        CanonicalLocator locator = new CanonicalLocator("repo", "proj", "mod", null, span);

        assertEquals("repo/proj:mod", locator.toString());
        assertEquals("mod", locator.identityKey());
    }

    @Test
    void identityKeyWithSymbolName() {
        LocatorSymbol symbol = new LocatorSymbol(SymbolKind.METHOD, "calculate");
        CanonicalLocator locator = new CanonicalLocator("repo", "proj", "mod", symbol, null);

        assertEquals("mod:METHOD:calculate", locator.identityKey());
    }

    @Test
    void identityKeyFallbackToLine() {
        LocatorSymbol symbol = new LocatorSymbol(SymbolKind.FUNCTION, null);
        LocatorSpan span = new LocatorSpan(42, null, null, null);
        CanonicalLocator locator = new CanonicalLocator("repo", "proj", "mod", symbol, span);

        assertEquals("mod:42", locator.identityKey());
    }

    @Test
    void anonymousNameFormat() {
        String result = CanonicalLocator.anonymousName("utils.js", 15);

        assertEquals("<anonymous@utils.js:15>", result);
    }

    @Test
    void defaultExportNameFormat() {
        String result = CanonicalLocator.defaultExportName("index.js");

        assertEquals("<default@index.js>", result);
    }
}
