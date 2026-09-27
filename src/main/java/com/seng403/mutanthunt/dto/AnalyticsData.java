package com.seng403.mutanthunt.dto;

import java.util.Map;

/**
 * @param participantCount        participants who joined the session (not removed)
 * @param activeParticipantCount  participants who submitted at least one test in the round
 * @param averageScore            mean score over active participants
 * @param invalidRate             invalidSubmissions / totalSubmissions (0..1)
 * @param mutantKillCounts        mutant id → number of distinct students who killed it
 */
public record AnalyticsData(
    long totalSubmissions,
    long invalidSubmissions,
    double invalidRate,
    long participantCount,
    long activeParticipantCount,
    double averageScore,
    Map<String, Long> mutantKillCounts,
    Map<String, String> mutantNames
) {}
