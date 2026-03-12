package com.bear.kernel.target.locator;

import java.util.Objects;

public record LocatorSymbol(SymbolKind kind, String name) {
    public LocatorSymbol {
        Objects.requireNonNull(kind, "LocatorSymbol.kind must not be null");
    }
}
