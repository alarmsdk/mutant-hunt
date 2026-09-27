package com.seng403.mutanthunt.problem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a problem that students write test cases for.
 * Implement this interface and register it in {@link ProblemRegistry} to add new problems.
 */
public interface Problem {

    /** Maximum length of a single raw input value (defence against oversized payloads). */
    int MAX_RAW_INPUT_LENGTH = 400;

    /** Unique identifier, e.g. "indexOf". */
    String getId();

    /** Human-readable title. */
    String getTitle();

    /** Specification text shown to students (may contain HTML). */
    String getSpecification();

    /** Source of the reference implementation (shown to students only after reveal). */
    default String getReferenceCode() { return null; }

    /** Input fields for the test form (excluding the expected-value field). */
    List<InputFieldDef> getFunctionInputFields();

    /** The expected-value input field definition. */
    InputFieldDef getExpectedField();

    /** All mutant definitions for this problem. */
    List<MutantDefinition> getMutants();

    /**
     * Validate raw string inputs (including expected).
     * @throws IllegalArgumentException with a message key
     */
    void validate(Map<String, String> rawInputs);

    /**
     * Execute the reference (correct) implementation.
     * @return the result in canonical string form
     */
    String executeReference(Map<String, String> rawInputs);

    /**
     * Execute a specific mutant.
     * @return an {@link ExecutionResult} (canonical value or exception)
     */
    ExecutionResult executeMutant(String mutantId, Map<String, String> rawInputs);

    /**
     * The expected value in the same canonical form that {@link #executeReference} returns,
     * so that e.g. "01" or "+1" compare equal to "1". Call only after {@link #validate}.
     */
    default String getExpected(Map<String, String> rawInputs) {
        return rawInputs.get(getExpectedField().id()).trim();
    }

    /**
     * Keep only the fields this problem declares (trimmed, missing → ""), so that
     * arbitrary extra JSON keys are never stored.
     * @throws IllegalArgumentException if a value exceeds {@link #MAX_RAW_INPUT_LENGTH}
     */
    default Map<String, String> sanitizeInputs(Map<String, String> body) {
        Map<String, String> clean = new LinkedHashMap<>();
        for (InputFieldDef f : getFunctionInputFields()) clean.put(f.id(), cleanValue(body.get(f.id())));
        clean.put(getExpectedField().id(), cleanValue(body.get(getExpectedField().id())));
        return clean;
    }

    private static String cleanValue(String v) {
        if (v == null) return "";
        if (v.length() > MAX_RAW_INPUT_LENGTH) throw new IllegalArgumentException("test.error.input.tooLong");
        return v.trim();
    }
}
