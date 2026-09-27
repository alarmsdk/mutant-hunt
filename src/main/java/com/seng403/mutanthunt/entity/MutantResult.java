package com.seng403.mutanthunt.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "mutant_result")
public class MutantResult {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long submissionId;

    @Column(nullable = false)
    private String mutantId;

    /** The value the mutant returned (null if exception) */
    private String mutantOutput;

    /** Exception message if the mutant threw (null otherwise) */
    private String exceptionMessage;

    private boolean killed;

    protected MutantResult() {}

    public MutantResult(Long submissionId, String mutantId,
                        String mutantOutput, String exceptionMessage, boolean killed) {
        this.submissionId = submissionId;
        this.mutantId = mutantId;
        this.mutantOutput = mutantOutput;
        this.exceptionMessage = exceptionMessage;
        this.killed = killed;
    }

    // ── Getters ───────────────────────────────────────

    public Long getId() { return id; }
    public Long getSubmissionId() { return submissionId; }
    public String getMutantId() { return mutantId; }
    public String getMutantOutput() { return mutantOutput; }
    public String getExceptionMessage() { return exceptionMessage; }
    public boolean isKilled() { return killed; }

    public String getDisplayOutput() {
        return exceptionMessage != null ? "EXCEPTION: " + exceptionMessage : mutantOutput;
    }
}
