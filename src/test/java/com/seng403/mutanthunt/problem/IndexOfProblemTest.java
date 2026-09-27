package com.seng403.mutanthunt.problem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the reference implementation, all 7 mutants,
 * and the exact kill matrix from the specification.
 */
class IndexOfProblemTest {

    private IndexOfProblem problem;

    @BeforeEach
    void setUp() {
        problem = new IndexOfProblem();
    }

    // ── Reference implementation tests ────────────────

    @Test
    void reference_findsFirstOccurrence() {
        assertEquals(1, IndexOfProblem.indexOfReference(List.of(3, 7, 3), 7));
    }

    @Test
    void reference_returnsMinusOneWhenAbsent() {
        assertEquals(-1, IndexOfProblem.indexOfReference(List.of(1, 2, 3), 5));
    }

    @Test
    void reference_emptyList() {
        assertEquals(-1, IndexOfProblem.indexOfReference(List.of(), 1));
    }

    @Test
    void reference_firstElement() {
        assertEquals(0, IndexOfProblem.indexOfReference(List.of(5), 5));
    }

    @Test
    void reference_lastElement() {
        assertEquals(1, IndexOfProblem.indexOfReference(List.of(9, 4), 4));
    }

    @Test
    void reference_duplicates_returnsFirst() {
        assertEquals(1, IndexOfProblem.indexOfReference(List.of(1, 2, 2), 2));
    }

    @Test
    void reference_largeValue() {
        assertEquals(0, IndexOfProblem.indexOfReference(List.of(1000), 1000));
    }

    // ── Kill matrix tests ─────────────────────────────

    /**
     * Test: [5], target=5, expected=0
     * Should KILL: M1, M2, M4
     */
    @Test
    void killMatrix_singleElement_targetPresent() {
        Map<String, String> inputs = Map.of("list", "5", "target", "5", "expected", "0");

        assertEquals("0", problem.executeReference(inputs));

        assertKilled("M1", inputs, "0");
        assertKilled("M2", inputs, "0");
        assertSurvived("M3", inputs, "0");
        assertKilled("M4", inputs, "0");
        assertSurvived("M5", inputs, "0");
        assertSurvived("M6", inputs, "0");
        assertSurvived("M7", inputs, "0");
    }

    /**
     * Test: [], target=3, expected=-1
     * Should KILL: M5
     */
    @Test
    void killMatrix_emptyList() {
        Map<String, String> inputs = Map.of("list", "", "target", "3", "expected", "-1");

        assertEquals("-1", problem.executeReference(inputs));

        assertSurvived("M1", inputs, "-1");
        assertSurvived("M2", inputs, "-1");
        assertSurvived("M3", inputs, "-1");
        assertSurvived("M4", inputs, "-1");
        assertKilled("M5", inputs, "-1");
        assertSurvived("M6", inputs, "-1");
        assertSurvived("M7", inputs, "-1");
    }

    /**
     * Test: [1,2,2], target=2, expected=1
     * Should KILL: M3, M4
     */
    @Test
    void killMatrix_duplicates() {
        Map<String, String> inputs = Map.of("list", "1,2,2", "target", "2", "expected", "1");

        assertEquals("1", problem.executeReference(inputs));

        assertSurvived("M1", inputs, "1");
        assertSurvived("M2", inputs, "1");
        assertKilled("M3", inputs, "1");
        assertKilled("M4", inputs, "1");
        assertSurvived("M5", inputs, "1");
        assertSurvived("M6", inputs, "1");
        assertSurvived("M7", inputs, "1");
    }

    /**
     * Test: [9,4], target=4, expected=1
     * Should KILL: M2, M4, M6
     */
    @Test
    void killMatrix_twoElements_targetLast() {
        Map<String, String> inputs = Map.of("list", "9,4", "target", "4", "expected", "1");

        assertEquals("1", problem.executeReference(inputs));

        assertSurvived("M1", inputs, "1");
        assertKilled("M2", inputs, "1");
        assertSurvived("M3", inputs, "1");
        assertKilled("M4", inputs, "1");
        assertSurvived("M5", inputs, "1");
        assertKilled("M6", inputs, "1");
        assertSurvived("M7", inputs, "1");
    }

    /**
     * Test: [1000], target=1000, expected=0
     * Should KILL: M1, M2, M4, M7
     */
    @Test
    void killMatrix_largeValue_killsM7() {
        Map<String, String> inputs = Map.of("list", "1000", "target", "1000", "expected", "0");

        assertEquals("0", problem.executeReference(inputs));

        assertKilled("M1", inputs, "0");
        assertKilled("M2", inputs, "0");
        assertSurvived("M3", inputs, "0");
        assertKilled("M4", inputs, "0");
        assertSurvived("M5", inputs, "0");
        assertSurvived("M6", inputs, "0");
        assertKilled("M7", inputs, "0");
    }

    /**
     * Test: [1,2,3], target=5, expected=-1
     * Should KILL: M5
     */
    @Test
    void killMatrix_notFound() {
        Map<String, String> inputs = Map.of("list", "1,2,3", "target", "5", "expected", "-1");

        assertEquals("-1", problem.executeReference(inputs));

        assertSurvived("M1", inputs, "-1");
        assertSurvived("M2", inputs, "-1");
        assertSurvived("M3", inputs, "-1");
        assertSurvived("M4", inputs, "-1");
        assertKilled("M5", inputs, "-1");
        assertSurvived("M6", inputs, "-1");
        assertSurvived("M7", inputs, "-1");
    }

    /**
     * Test: [5], target=5, expected=1 → INVALID (reference returns 0)
     */
    @Test
    void killMatrix_invalidTest() {
        Map<String, String> inputs = Map.of("list", "5", "target", "5", "expected", "1");
        String ref = problem.executeReference(inputs);
        assertEquals("0", ref);
        assertNotEquals("1", ref, "Expected value is wrong → INVALID");
    }

    // ── Validation tests ──────────────────────────────

    @Test
    void validate_validInputs() {
        assertDoesNotThrow(() -> problem.validate(
            Map.of("list", "1,2,3", "target", "5", "expected", "-1")));
    }

    @Test
    void validate_emptyList() {
        assertDoesNotThrow(() -> problem.validate(
            Map.of("list", "", "target", "5", "expected", "-1")));
    }

    @Test
    void validate_invalidListFormat() {
        assertThrows(IllegalArgumentException.class, () -> problem.validate(
            Map.of("list", "a,b,c", "target", "5", "expected", "-1")));
    }

    @Test
    void validate_listTooLong() {
        String longList = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21";
        assertThrows(IllegalArgumentException.class, () -> problem.validate(
            Map.of("list", longList, "target", "5", "expected", "-1")));
    }

    @Test
    void validate_invalidTarget() {
        assertThrows(IllegalArgumentException.class, () -> problem.validate(
            Map.of("list", "1,2,3", "target", "abc", "expected", "-1")));
    }

    @Test
    void validate_invalidExpected() {
        assertThrows(IllegalArgumentException.class, () -> problem.validate(
            Map.of("list", "1,2,3", "target", "5", "expected", "xyz")));
    }

    @Test
    void validate_overflowValue() {
        assertThrows(IllegalArgumentException.class, () -> problem.validate(
            Map.of("list", "99999999999999", "target", "5", "expected", "-1")));
    }

    // ── M7 specific tests ─────────────────────────────

    @Test
    void m7_matchesInCacheRange() {
        assertEquals(0, IndexOfProblem.indexOfM7(List.of(5), 5));
        assertEquals(0, IndexOfProblem.indexOfM7(List.of(-128), -128));
        assertEquals(0, IndexOfProblem.indexOfM7(List.of(127), 127));
    }

    @Test
    void m7_failsOutsideCacheRange() {
        assertEquals(-1, IndexOfProblem.indexOfM7(List.of(128), 128));
        assertEquals(-1, IndexOfProblem.indexOfM7(List.of(-129), -129));
        assertEquals(-1, IndexOfProblem.indexOfM7(List.of(1000), 1000));
    }

    // ── Helpers ───────────────────────────────────────

    private void assertKilled(String mutantId, Map<String, String> inputs, String expected) {
        ExecutionResult result = problem.executeMutant(mutantId, inputs);
        boolean killed = result.isException() || !result.value().equals(expected);
        assertTrue(killed, mutantId + " should be KILLED but survived with output: " + result.display());
    }

    private void assertSurvived(String mutantId, Map<String, String> inputs, String expected) {
        ExecutionResult result = problem.executeMutant(mutantId, inputs);
        boolean killed = result.isException() || !result.value().equals(expected);
        assertFalse(killed, mutantId + " should SURVIVE but was killed with output: " + result.display());
    }
}
