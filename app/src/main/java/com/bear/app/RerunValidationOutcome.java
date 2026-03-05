package com.bear.app;

record RerunValidationOutcome(
    Status status,
    String renderedCommand,
    String correctedCommand,
    String parseFailureSummary,
    String equivalenceFailureSummary
) {
    enum Status {
        VALID,
        REPAIRED_VALID,
        HARD_INVALID
    }

    static RerunValidationOutcome valid(String command) {
        return new RerunValidationOutcome(Status.VALID, command, command, "", "");
    }

    static RerunValidationOutcome repairedValid(
        String renderedCommand,
        String correctedCommand,
        String parseFailureSummary,
        String equivalenceFailureSummary
    ) {
        return new RerunValidationOutcome(
            Status.REPAIRED_VALID,
            renderedCommand,
            correctedCommand,
            nullToEmpty(parseFailureSummary),
            nullToEmpty(equivalenceFailureSummary)
        );
    }

    static RerunValidationOutcome hardInvalid(
        String renderedCommand,
        String correctedCommand,
        String parseFailureSummary,
        String equivalenceFailureSummary
    ) {
        return new RerunValidationOutcome(
            Status.HARD_INVALID,
            renderedCommand,
            correctedCommand,
            nullToEmpty(parseFailureSummary),
            nullToEmpty(equivalenceFailureSummary)
        );
    }

    String effectiveCommand() {
        if (status == Status.REPAIRED_VALID || status == Status.HARD_INVALID) {
            return correctedCommand == null || correctedCommand.isBlank() ? renderedCommand : correctedCommand;
        }
        return renderedCommand;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
