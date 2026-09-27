package com.seng403.mutanthunt.repository;

import com.seng403.mutanthunt.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByActiveTrue();
    List<GameSession> findByArchivedFalseOrderByCreatedAtDesc();
    List<GameSession> findByArchivedTrueOrderByCreatedAtDesc();
}
