package com.seng403.mutanthunt.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_session")
public class GameSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private boolean active;
    private boolean archived;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnonymityMode anonymityMode = AnonymityMode.FULL_NAME;

    private boolean joinOpen = true;

    private int studentIdLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MutantPanelMode mutantPanelMode = MutantPanelMode.HIDDEN;

    private int submissionRateLimitMs = 2000;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected GameSession() {}

    public GameSession(String name) {
        this.name = name;
    }

    // ── Getters & Setters ─────────────────────────────

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public AnonymityMode getAnonymityMode() { return anonymityMode; }
    public void setAnonymityMode(AnonymityMode anonymityMode) { this.anonymityMode = anonymityMode; }

    public boolean isJoinOpen() { return joinOpen; }
    public void setJoinOpen(boolean joinOpen) { this.joinOpen = joinOpen; }

    public int getStudentIdLength() { return studentIdLength; }
    public void setStudentIdLength(int studentIdLength) { this.studentIdLength = studentIdLength; }

    public MutantPanelMode getMutantPanelMode() { return mutantPanelMode; }
    public void setMutantPanelMode(MutantPanelMode mutantPanelMode) { this.mutantPanelMode = mutantPanelMode; }

    public int getSubmissionRateLimitMs() { return submissionRateLimitMs; }
    public void setSubmissionRateLimitMs(int submissionRateLimitMs) { this.submissionRateLimitMs = submissionRateLimitMs; }

    public Instant getCreatedAt() { return createdAt; }
}
