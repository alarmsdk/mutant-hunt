package com.seng403.mutanthunt.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "submission")
public class Submission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roundId;

    @Column(nullable = false)
    private Long participantId;

    // Snapshot of who submitted and where, so exports/analysis stay correct even if
    // the participant is later renamed or removed. Nullable for databases created
    // before these columns existed.
    private Long sessionId;
    private String participantName;
    private String studentNumber;
    private String displayName;

    /** Raw inputs as JSON, e.g. {"list":"1,2","target":"3","expected":"-1"} */
    @Column(length = 4000, nullable = false)
    private String rawInputsJson;

    /** The student's expected value */
    @Column(nullable = false)
    private String expectedValue;

    /** The value returned by the reference implementation */
    @Column(nullable = false)
    private String referenceResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Validity validity;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Submission() {}

    public Submission(Long roundId, Long participantId, String rawInputsJson,
                      String expectedValue, String referenceResult, Validity validity) {
        this.roundId = roundId;
        this.participantId = participantId;
        this.rawInputsJson = rawInputsJson;
        this.expectedValue = expectedValue;
        this.referenceResult = referenceResult;
        this.validity = validity;
    }

    public Submission withSnapshot(Long sessionId, String participantName,
                                   String studentNumber, String displayName) {
        this.sessionId = sessionId;
        this.participantName = participantName;
        this.studentNumber = studentNumber;
        this.displayName = displayName;
        return this;
    }

    // ── Getters ───────────────────────────────────────

    public Long getId() { return id; }
    public Long getRoundId() { return roundId; }
    public Long getParticipantId() { return participantId; }
    public Long getSessionId() { return sessionId; }
    public String getParticipantName() { return participantName; }
    public String getStudentNumber() { return studentNumber; }
    public String getDisplayName() { return displayName; }
    public String getRawInputsJson() { return rawInputsJson; }
    public String getExpectedValue() { return expectedValue; }
    public String getReferenceResult() { return referenceResult; }
    public Validity getValidity() { return validity; }
    public Instant getCreatedAt() { return createdAt; }
}
