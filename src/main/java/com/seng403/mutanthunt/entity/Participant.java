package com.seng403.mutanthunt.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "participant",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sessionId", "studentNumber"}))
public class Participant {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private String studentNumber;

    @Column(nullable = false)
    private String fullName;

    private String nickname;

    /** Auto-generated anonymous display name, e.g. "Blue Tiger #12" */
    private String anonymousName;

    /** Cookie auth token (UUID) */
    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    private boolean kicked;

    /** How many times the same student number joined again (e.g. from another device). */
    private Integer rejoinCount;

    protected Participant() {}

    public Participant(Long sessionId, String studentNumber, String fullName, String token) {
        this.sessionId = sessionId;
        this.studentNumber = studentNumber;
        this.fullName = fullName;
        this.token = token;
    }

    // ── Getters & Setters ─────────────────────────────

    public Long getId() { return id; }

    public Long getSessionId() { return sessionId; }

    public String getStudentNumber() { return studentNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAnonymousName() { return anonymousName; }
    public void setAnonymousName(String anonymousName) { this.anonymousName = anonymousName; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Instant getJoinedAt() { return joinedAt; }

    public int getRejoinCount() { return rejoinCount == null ? 0 : rejoinCount; }
    public void incrementRejoinCount() { rejoinCount = getRejoinCount() + 1; }

    public boolean isKicked() { return kicked; }
    public void setKicked(boolean kicked) { this.kicked = kicked; }

    /** Masked student number, e.g. "2021****34" */
    public String getMaskedNumber() {
        if (studentNumber.length() <= 4) return "****";
        return studentNumber.substring(0, 4) + "****"
             + studentNumber.substring(studentNumber.length() - 2);
    }
}
