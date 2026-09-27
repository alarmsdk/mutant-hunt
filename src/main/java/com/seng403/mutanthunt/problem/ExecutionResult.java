package com.seng403.mutanthunt.problem;

/**
 * Result of executing a mutant implementation.
 * Either a value is returned or an exception was thrown.
 */
public record ExecutionResult(String value, String exception) {
    public static ExecutionResult of(String value) {
        return new ExecutionResult(value, null);
    }

    public static ExecutionResult error(String exceptionMessage) {
        return new ExecutionResult(null, exceptionMessage);
    }

    public boolean isException() {
        return exception != null;
    }

    /** The effective output: the value, or "EXCEPTION: ..." */
    public String display() {
        return isException() ? "EXCEPTION: " + exception : value;
    }
}
