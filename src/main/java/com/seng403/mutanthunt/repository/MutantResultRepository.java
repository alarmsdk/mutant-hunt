package com.seng403.mutanthunt.repository;

import com.seng403.mutanthunt.entity.MutantResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;

public interface MutantResultRepository extends JpaRepository<MutantResult, Long> {
    List<MutantResult> findBySubmissionId(Long submissionId);
    List<MutantResult> findBySubmissionIdIn(Collection<Long> submissionIds);

    @Query("SELECT mr FROM MutantResult mr WHERE mr.submissionId IN " +
           "(SELECT s.id FROM Submission s WHERE s.roundId = :roundId AND s.validity = 'VALID') " +
           "AND mr.killed = true")
    List<MutantResult> findKilledByRound(Long roundId);

    @Query("SELECT mr FROM MutantResult mr WHERE mr.submissionId IN " +
           "(SELECT s.id FROM Submission s WHERE s.roundId IN :roundIds)")
    List<MutantResult> findByRoundIds(Collection<Long> roundIds);

    @Modifying
    @Query("DELETE FROM MutantResult mr WHERE mr.submissionId IN " +
           "(SELECT s.id FROM Submission s WHERE s.roundId = :roundId)")
    void deleteByRoundId(Long roundId);
}
