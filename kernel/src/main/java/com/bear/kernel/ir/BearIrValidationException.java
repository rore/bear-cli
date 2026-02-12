package com.bear.kernel.ir;

public final class BearIrValidationException extends RuntimeException {
    public enum Category {
        SCHEMA("schema"),
        SEMANTIC("semantic");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Code {
        MISSING_FIELD,
        UNKNOWN_KEY,
        INVALID_ENUM,
        INVALID_TYPE,
        INVALID_VALUE,
        DUPLICATE,
        UNKNOWN_REFERENCE,
        MULTI_DOCUMENT,
        INVALID_YAML
    }

    private final Category category;
    private final String path;
    private final Code code;

    public BearIrValidationException(Category category, String path, Code code, String message) {
        super(message);
        this.category = category;
        this.path = path;
        this.code = code;
    }

    public Category category() {
        return category;
    }

    public String path() {
        return path;
    }

    public Code code() {
        return code;
    }

    public String formatLine() {
        return category.label() + " at " + path + ": " + code + ": " + getMessage();
    }
}

