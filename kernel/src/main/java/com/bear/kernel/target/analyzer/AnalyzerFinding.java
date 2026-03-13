package com.bear.kernel.target.analyzer;

import com.bear.kernel.target.locator.CanonicalLocator;

public record AnalyzerFinding(String code, String message, CanonicalLocator locator) {
}
