package com.seng403.mutanthunt.dto;

public record SubmitResponse(
    boolean success,
    String validity,
    String message,
    int killed,
    int totalMutants,
    long testsRemaining,
    Long submissionId
) {
    public static SubmitResponse error(String message) {
        return new SubmitResponse(false, null, message, 0, 0, 0, null);
    }
}
