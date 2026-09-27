package com.seng403.mutanthunt.repository;

import com.seng403.mutanthunt.entity.Participant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    Optional<Participant> findByToken(String token);
    Optional<Participant> findBySessionIdAndStudentNumber(Long sessionId, String studentNumber);
    List<Participant> findBySessionIdAndKickedFalseOrderByJoinedAt(Long sessionId);
    List<Participant> findBySessionId(Long sessionId);
    long countBySessionIdAndKickedFalse(Long sessionId);

    /** Row lock that serialises concurrent submissions of the same participant (test limit). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Participant p WHERE p.id = :id")
    Optional<Participant> findByIdForUpdate(Long id);
}
