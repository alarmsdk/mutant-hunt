package com.seng403.mutanthunt.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round")
public class Round {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String problemId;

    /** null or 0 = unlimited */
    private Integer testLimit;

    /** null or 0 = no time limit */
    private Integer timeLimitSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoundStatus status = RoundStatus.DRAFT;

    private Instant openedAt;
    private Instant closedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Round() {}

    public Round(Long sessionId, String name, String problemId) {
        this.sessionId = sessionId;
        this.name = name;
        this.problemId = problemId;
    }

    // ── Getters & Setters ─────────────────────────────

    public Long getId() { return id; }

    public Long getSessionId() { return sessionId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProblemId() { return problemId; }

    public Integer getTestLimit() { return testLimit; }
    public void setTestLimit(Integer testLimit) { this.testLimit = testLimit; }

    public boolean isUnlimited() { return testLimit == null || testLimit <= 0; }

    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }

    /** When an OPEN round with a time limit runs out, or null if it has no deadline. */
    public Instant getDeadline() {
        if (openedAt == null || timeLimitSeconds == null || timeLimitSeconds <= 0) return null;
        return openedAt.plusSeconds(timeLimitSeconds);
    }
    public void setTimeLimitSeconds(Integer timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }

    public RoundStatus getStatus() { return status; }
    public void setStatus(RoundStatus status) { this.status = status; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public Instant getCreatedAt() { return createdAt; }
}
