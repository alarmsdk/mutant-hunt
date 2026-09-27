package com.seng403.mutanthunt.dto;

import com.seng403.mutanthunt.problem.InputFieldDef;
import java.util.List;

/**
 * Everything the student page needs. Mutant details ({@code mutantInfos}, {@code referenceCode})
 * are only filled in once the round is REVEALED.
 *
 * @param remainingMs milliseconds left on the round timer (server clock), null if no time limit
 */
public record PlayerStatus(
    String playerName,
    String displayName,
    String roundStatus,
    String roundName,
    String specification,
    List<InputFieldDef> functionInputFields,
    InputFieldDef expectedField,
    int killed,
    int totalMutants,
    long testsUsed,
    long testsRemaining,
    Integer timeLimitSeconds,
    Long remainingMs,
    List<SubmissionInfo> submissions,
    List<MutantInfo> mutantInfos,
    String referenceCode,
    long participantCount,
    boolean nicknameMode
) {
    public static PlayerStatus waiting(String name, String displayName, long participantCount) {
        return new PlayerStatus(name, displayName, "WAITING", null, null, null, null,
            0, 0, 0, 0, null, null, null, null, null, participantCount, false);
    }
}
