package com.bear.kernel.target.analyzer;

import com.bear.kernel.target.locator.CanonicalLocator;

public record ReferenceEdge(String sourceSymbol, String targetSymbol, CanonicalLocator locator) {
}
