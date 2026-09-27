package com.seng403.mutanthunt.dto;

import java.util.List;

public record SubmissionInfo(
    Long id,
    String list,
    String target,
    String expected,
    String validity,
    String referenceResult,
    List<MutantKillInfo> mutantResults,
    long timestampMs
) {}
