package com.seng403.mutanthunt.problem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Parsing, normalisation and server-side validation rules for student input. */
class InputParsingTest {

    private final IndexOfProblem problem = new IndexOfProblem();

    // ── list ──────────────────────────────────────────

    @Test
    void list_acceptsCommonNotations() {
        assertEquals(List.of(1, 2, 3), IndexOfProblem.parseList("1,2,3"));
        assertEquals(List.of(1, 2, 3), IndexOfProblem.parseList(" 1 , 2 ,3 "));
        assertEquals(List.of(1, 2, 3), IndexOfProblem.parseList("[1, 2, 3]"));
        assertEquals(List.of(1, 2, 3), IndexOfProblem.parseList("1 2 3"));
        assertEquals(List.of(-5, 0, 7), IndexOfProblem.parseList("-5,+0,7"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "[]", "[ ]"})
    void list_emptyForms(String raw) {
        assertEquals(List.of(), IndexOfProblem.parseList(raw));
    }

    @Test
    void list_unicodeMinusFromMobileKeyboards() {
        assertEquals(List.of(-3, -4), IndexOfProblem.parseList("−3,–4"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,,2", "1,2,", ",1", "1;2", "1.5", "a", "1,2]", "--1", "0x10"})
    void list_rejectsMalformed(String raw) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> IndexOfProblem.parseList(raw));
        assertEquals("test.error.list.format", e.getMessage());
    }

    @Test
    void list_atMost20Elements() {
        String twenty = String.join(",", java.util.Collections.nCopies(20, "1"));
        assertEquals(20, IndexOfProblem.parseList(twenty).size());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> IndexOfProblem.parseList(twenty + ",1"));
        assertEquals("test.error.list.tooMany", e.getMessage());
    }

    @Test
    void list_intRangeIsEnforced() {
        assertEquals(List.of(Integer.MIN_VALUE, Integer.MAX_VALUE),
            IndexOfProblem.parseList("-2147483648,2147483647"));
        assertEquals("test.error.value.range", assertThrows(IllegalArgumentException.class,
            () -> IndexOfProblem.parseList("2147483648")).getMessage());
        assertEquals("test.error.value.range", assertThrows(IllegalArgumentException.class,
            () -> IndexOfProblem.parseList("99999999999999999999999")).getMessage());
    }

    // ── target / expected ─────────────────────────────

    @ParameterizedTest
    @CsvSource({"0,0", "01,1", "+1,1", "-0,0", "' 7 ',7", "-2147483648,-2147483648"})
    void expected_isNormalisedBeforeComparingWithTheReference(String raw, String canonical) {
        assertEquals(canonical, problem.getExpected(Map.of("expected", raw)));
    }

    @Test
    void expected_withLeadingZeroIsStillValid() {
        // [9,4] target 4 → 1 ; "01" and "+1" mean the same integer
        Map<String, String> in = Map.of("list", "9,4", "target", "4", "expected", "01");
        problem.validate(in);
        assertEquals(problem.executeReference(in), problem.getExpected(in));
    }

    @Test
    void target_rangeError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> problem.validate(Map.of("list", "1", "target", "-2147483649", "expected", "0")));
        assertEquals("test.error.value.range", e.getMessage());
    }

    @Test
    void missingFields_areFormatErrors() {
        assertEquals("test.error.target.format", assertThrows(IllegalArgumentException.class,
            () -> problem.validate(Map.of("list", "1"))).getMessage());
        assertEquals("test.error.expected.format", assertThrows(IllegalArgumentException.class,
            () -> problem.validate(Map.of("list", "1", "target", "1"))).getMessage());
    }

    // ── sanitisation ──────────────────────────────────

    @Test
    void sanitize_keepsOnlyDeclaredFields() {
        Map<String, String> body = new HashMap<>(Map.of(
            "list", " 1,2 ", "target", "2", "expected", "1", "evil", "x".repeat(10_000)));
        Map<String, String> clean = problem.sanitizeInputs(body);
        assertEquals(Map.of("list", "1,2", "target", "2", "expected", "1"), clean);
    }

    @Test
    void sanitize_missingFieldsBecomeEmpty() {
        Map<String, String> clean = problem.sanitizeInputs(Map.of());
        assertEquals(Map.of("list", "", "target", "", "expected", ""), clean);
    }

    @Test
    void sanitize_rejectsOversizedValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> problem.sanitizeInputs(Map.of("list", "1,".repeat(1000), "target", "1", "expected", "0")));
        assertEquals("test.error.input.tooLong", e.getMessage());
    }

    // ── problem definition ────────────────────────────

    @Test
    void everyMutantHasCodeAndDescription() {
        assertEquals(7, problem.getMutants().size());
        problem.getMutants().forEach(m -> {
            assertNotNull(m.code(), m.id());
            assertFalse(m.description().isBlank(), m.id());
        });
        assertNotNull(problem.getReferenceCode());
    }
}
