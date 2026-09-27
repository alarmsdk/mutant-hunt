package com.seng403.mutanthunt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService();
    }

    // ── Submission rate limiting ───────────────────────

    @Test
    void submission_firstAlwaysAllowed() {
        assertTrue(service.isSubmissionAllowed(1L, 5000));
    }

    @Test
    void submission_blockedWithinWindow() {
        assertTrue(service.isSubmissionAllowed(1L, 5000));
        assertFalse(service.isSubmissionAllowed(1L, 5000));
    }

    @Test
    void submission_zeroRateLimitAlwaysAllowed() {
        assertTrue(service.isSubmissionAllowed(1L, 0));
        assertTrue(service.isSubmissionAllowed(1L, 0));
    }

    @Test
    void submission_differentParticipantsIndependent() {
        assertTrue(service.isSubmissionAllowed(1L, 5000));
        assertTrue(service.isSubmissionAllowed(2L, 5000));
    }

    @Test
    void submission_allowedAfterWindow() throws InterruptedException {
        assertTrue(service.isSubmissionAllowed(1L, 50));
        Thread.sleep(60);
        assertTrue(service.isSubmissionAllowed(1L, 50));
    }

    // ── Login brute-force protection ──────────────────

    @Test
    void login_notBlockedByDefault() {
        assertFalse(service.isLoginBlocked("1.2.3.4"));
    }

    @Test
    void login_blockedAfterMaxAttempts() {
        String ip = "10.0.0.1";
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure(ip);
        }
        assertTrue(service.isLoginBlocked(ip));
    }

    @Test
    void login_notBlockedBeforeMaxAttempts() {
        String ip = "10.0.0.2";
        for (int i = 0; i < 4; i++) {
            service.recordLoginFailure(ip);
        }
        assertFalse(service.isLoginBlocked(ip));
    }

    @Test
    void login_blockRemainingSeconds() {
        String ip = "10.0.0.3";
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure(ip);
        }
        long remaining = service.getBlockRemainingSeconds(ip);
        assertTrue(remaining > 0, "Should have block time remaining");
    }

    @Test
    void login_blockRemainingZeroWhenNotBlocked() {
        assertEquals(0, service.getBlockRemainingSeconds("unknown"));
    }

    @Test
    void login_resetClearsFailures() {
        String ip = "10.0.0.4";
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure(ip);
        }
        assertTrue(service.isLoginBlocked(ip));
        service.resetLoginFailures(ip);
        assertFalse(service.isLoginBlocked(ip));
    }

    @Test
    void login_differentIpsIndependent() {
        for (int i = 0; i < 5; i++) {
            service.recordLoginFailure("10.0.0.5");
        }
        assertTrue(service.isLoginBlocked("10.0.0.5"));
        assertFalse(service.isLoginBlocked("10.0.0.6"));
    }
}
