package com.seng403.mutanthunt.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiting for submissions and login attempts.
 */
@Service
public class RateLimitService {

    /** participantId → last submission timestamp (ms) */
    private final Map<Long, Long> lastSubmission = new ConcurrentHashMap<>();

    /** IP → LoginTracker */
    private final Map<String, LoginTracker> loginAttempts = new ConcurrentHashMap<>();

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOGIN_BLOCK_MS = 15 * 60 * 1000L;
    private static final long LOGIN_WINDOW_MS = 5 * 60 * 1000L;

    // ── Submission rate limit ─────────────────────────

    public boolean isSubmissionAllowed(Long participantId, int rateLimitMs) {
        long now = System.currentTimeMillis();
        Long last = lastSubmission.get(participantId);
        if (last != null && (now - last) < rateLimitMs) {
            return false;
        }
        lastSubmission.put(participantId, now);
        return true;
    }

    // ── Login brute-force protection ──────────────────

    public boolean isLoginBlocked(String ip) {
        LoginTracker tracker = loginAttempts.get(ip);
        if (tracker == null) return false;
        if (tracker.blocked && System.currentTimeMillis() < tracker.blockedUntil) {
            return true;
        }
        if (tracker.blocked && System.currentTimeMillis() >= tracker.blockedUntil) {
            loginAttempts.remove(ip);
            return false;
        }
        return false;
    }

    public long getBlockRemainingSeconds(String ip) {
        LoginTracker tracker = loginAttempts.get(ip);
        if (tracker == null || !tracker.blocked) return 0;
        long remaining = (tracker.blockedUntil - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void recordLoginFailure(String ip) {
        LoginTracker tracker = loginAttempts.computeIfAbsent(ip, k -> new LoginTracker());
        long now = System.currentTimeMillis();
        if (now - tracker.windowStart > LOGIN_WINDOW_MS) {
            tracker.count.set(0);
            tracker.windowStart = now;
        }
        if (tracker.count.incrementAndGet() >= MAX_LOGIN_ATTEMPTS) {
            tracker.blocked = true;
            tracker.blockedUntil = now + LOGIN_BLOCK_MS;
        }
    }

    public void resetLoginFailures(String ip) {
        loginAttempts.remove(ip);
    }

    private static class LoginTracker {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long windowStart = System.currentTimeMillis();
        volatile boolean blocked = false;
        volatile long blockedUntil = 0;
    }
}
