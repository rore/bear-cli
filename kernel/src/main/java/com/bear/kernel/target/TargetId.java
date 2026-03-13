package com.bear.kernel.target;

public enum TargetId {
    JVM("jvm"),
    NODE("node"),
    PYTHON("python"),
    REACT("react");

    private final String value;

    TargetId(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static TargetId fromValue(String value) {
        for (TargetId id : values()) {
            if (id.value.equals(value)) {
                return id;
            }
        }
        throw new IllegalArgumentException("Unknown target id: " + value);
    }

    /**
     * Returns the ecosystem family for this target.
     * Targets in the same ecosystem family share detection concerns and can block each other.
     * For example, Node and React both belong to the JavaScript ecosystem family.
     */
    public String ecosystemFamily() {
        return switch (this) {
            case JVM -> "jvm";
            case NODE, REACT -> "javascript";
            case PYTHON -> "python";
        };
    }
}