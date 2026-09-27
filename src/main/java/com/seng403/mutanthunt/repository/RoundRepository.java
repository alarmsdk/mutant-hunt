package com.seng403.mutanthunt.repository;

import com.seng403.mutanthunt.entity.Round;
import com.seng403.mutanthunt.entity.RoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {
    List<Round> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    Optional<Round> findBySessionIdAndStatus(Long sessionId, RoundStatus status);
    List<Round> findBySessionIdAndStatusIn(Long sessionId, List<RoundStatus> statuses);
    List<Round> findByStatus(RoundStatus status);
}
