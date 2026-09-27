package com.seng403.mutanthunt.repository;

import com.seng403.mutanthunt.entity.Submission;
import com.seng403.mutanthunt.entity.Validity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    List<Submission> findByRoundIdAndParticipantIdOrderByCreatedAt(Long roundId, Long participantId);
    List<Submission> findByRoundIdOrderByCreatedAtDesc(Long roundId);
    List<Submission> findByRoundIdOrderByCreatedAtAscIdAsc(Long roundId);
    List<Submission> findByRoundIdInOrderByCreatedAtDesc(Collection<Long> roundIds);
    long countByRoundIdAndParticipantIdAndValidity(Long roundId, Long participantId, Validity validity);
    long countByRoundIdAndParticipantId(Long roundId, Long participantId);
    long countByRoundId(Long roundId);
    long countByRoundIdAndValidity(Long roundId, Validity validity);
    List<Submission> findByRoundIdAndValidity(Long roundId, Validity validity);
}
