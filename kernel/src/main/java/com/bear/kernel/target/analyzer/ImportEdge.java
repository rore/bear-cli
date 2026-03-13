package com.bear.kernel.target.analyzer;

import com.bear.kernel.target.locator.CanonicalLocator;

public record ImportEdge(String sourceModule, String targetModule, String specifier, CanonicalLocator locator) {
}
