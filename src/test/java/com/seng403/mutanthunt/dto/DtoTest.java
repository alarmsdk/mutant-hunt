package com.seng403.mutanthunt.dto;

import com.seng403.mutanthunt.problem.InputFieldDef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    @Test
    void joinResponse() {
        JoinResponse jr = new JoinResponse(true, "token123", "Alice");
        assertTrue(jr.success());
        assertEquals("token123", jr.token());
        assertEquals("Alice", jr.name());
    }

    @Test
    void submitResponse_success() {
        SubmitResponse sr = new SubmitResponse(true, "VALID", null, 3, 7, 5, 1L);
        assertTrue(sr.success());
        assertEquals("VALID", sr.validity());
        assertNull(sr.message());
        assertEquals(3, sr.killed());
        assertEquals(7, sr.totalMutants());
        assertEquals(5, sr.testsRemaining());
        assertEquals(1L, sr.submissionId());
    }

    @Test
    void submitResponse_error() {
        SubmitResponse sr = SubmitResponse.error("test.rate.limited");
        assertFalse(sr.success());
        assertEquals("test.rate.limited", sr.message());
        assertNull(sr.validity());
        assertEquals(0, sr.killed());
    }

    @Test
    void playerStatus_waiting() {
        PlayerStatus ps = PlayerStatus.waiting("Alice", 5);
        assertEquals("Alice", ps.playerName());
        assertEquals("WAITING", ps.roundStatus());
        assertNull(ps.roundName());
        assertEquals(5, ps.participantCount());
        assertFalse(ps.nicknameMode());
    }

    @Test
    void playerStatus_full() {
        PlayerStatus ps = new PlayerStatus(
            "Alice", "OPEN", "Round 1", "<p>Spec</p>",
            List.of(new InputFieldDef("list", "l", "p", "text")),
            new InputFieldDef("expected", "e", "p", "numeric"),
            3, 7, 2, 8, 120, 1000L,
            List.of(), null, 10, false
        );
        assertEquals("Alice", ps.playerName());
        assertEquals("OPEN", ps.roundStatus());
        assertEquals("Round 1", ps.roundName());
        assertEquals(3, ps.killed());
        assertEquals(7, ps.totalMutants());
        assertEquals(120, ps.timeLimitSeconds());
    }

    @Test
    void scoreboardEntry() {
        Instant now = Instant.now();
        ScoreboardEntry se = new ScoreboardEntry(1L, "Alice", 5, 7, 3, now);
        assertEquals(1L, se.participantId());
        assertEquals("Alice", se.displayName());
        assertEquals(5, se.score());
        assertEquals(7, se.totalMutants());
        assertEquals(3, se.validTestCount());
        assertEquals(now, se.lastScoreTime());
    }

    @Test
    void submissionInfo() {
        SubmissionInfo si = new SubmissionInfo(
            1L, "1,2,3", "5", "-1", "VALID", "-1",
            List.of(new MutantKillInfo("M1", true)), 12345L
        );
        assertEquals(1L, si.id());
        assertEquals("1,2,3", si.list());
        assertEquals("5", si.target());
        assertEquals("-1", si.expected());
        assertEquals("VALID", si.validity());
    }

    @Test
    void mutantInfo() {
        MutantInfo mi = new MutantInfo("M1", "start-at-one", "Starts at 1");
        assertEquals("M1", mi.id());
        assertEquals("start-at-one", mi.shortName());
        assertEquals("Starts at 1", mi.description());
    }

    @Test
    void mutantKillInfo() {
        MutantKillInfo mki = new MutantKillInfo("M1", true);
        assertEquals("M1", mki.mutantId());
        assertTrue(mki.killed());

        MutantKillInfo mki2 = new MutantKillInfo("M2", false);
        assertFalse(mki2.killed());
    }

    @Test
    void analyticsData() {
        AnalyticsData ad = new AnalyticsData(
            100, 10, 20, 4.5,
            Map.of("M1", 15L, "M2", 10L),
            Map.of("M1", "start-at-one", "M2", "skip-last")
        );
        assertEquals(100, ad.totalSubmissions());
        assertEquals(10, ad.invalidSubmissions());
        assertEquals(20, ad.participantCount());
        assertEquals(4.5, ad.averageScore());
        assertEquals(2, ad.mutantKillCounts().size());
        assertEquals(2, ad.mutantNames().size());
    }

    @Test
    void inputFieldDef() {
        InputFieldDef ifd = new InputFieldDef("list", "label", "placeholder", "text");
        assertEquals("list", ifd.id());
        assertEquals("label", ifd.label());
        assertEquals("placeholder", ifd.placeholder());
        assertEquals("text", ifd.inputMode());
    }

    @Test
    void mutantDefinition() {
        var md = new com.seng403.mutanthunt.problem.MutantDefinition("M1", "short", "desc");
        assertEquals("M1", md.id());
        assertEquals("short", md.shortName());
        assertEquals("desc", md.description());
    }
}
