package com.bear.kernel.target.analyzer;

import com.bear.kernel.target.locator.CanonicalLocator;

public record OwnershipFact(String module, String ownerBlock, CanonicalLocator locator) {
}
