package com.bear.kernel.target;

public final class ManifestParseException extends Exception {
    private final String reasonCode;

    public ManifestParseException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}

