package com.seng403.mutanthunt.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/**
 * One row of the live scoreboard. Ranking: score DESC, testsUsed ASC, lastScoreTime ASC.
 *
 * @param testsUsed      all tests submitted in the round (VALID and INVALID) – tie-breaker 1
 * @param validTestCount VALID tests only (informational)
 * @param lastScoreTime  when the current score was first reached – tie-breaker 2
 */
public record ScoreboardEntry(
    @JsonIgnore long participantId,
    String displayName,
    int score,
    int totalMutants,
    long testsUsed,
    long validTestCount,
    Instant lastScoreTime
) {}
