package com.seng403.mutanthunt.problem;

import java.util.*;
import java.util.function.BiFunction;

/**
 * indexOf problem: find the first occurrence of target in a list.
 * <p>
 * Seven mutants are provided, each with exactly one semantic change
 * from the reference implementation.
 */
public class IndexOfProblem implements Problem {

    private static final int MAX_LIST_SIZE = 20;
    private final List<MutantDefinition> mutants;
    private final Map<String, BiFunction<List<Integer>, Integer, Integer>> mutantImpls;

    public IndexOfProblem() {
        mutants = List.of(
            new MutantDefinition("M1", "start-at-one",
                "Loop starts at i = 1 instead of i = 0, so the first element is never checked.",
                "for (int i = 1; i < list.size(); i++)   // was: i = 0"),
            new MutantDefinition("M2", "skip-last",
                "Loop condition is i < list.size() - 1, so the last element is never checked.",
                "for (int i = 0; i < list.size() - 1; i++)   // was: i < list.size()"),
            new MutantDefinition("M3", "last-occurrence",
                "Returns the index of the last occurrence instead of the first.",
                "int result = -1;\nfor (int i = 0; i < list.size(); i++)\n    if (list.get(i) == target) result = i;   // keeps looping\nreturn result;"),
            new MutantDefinition("M4", "index-plus-one",
                "Returns i + 1 instead of i when the target is found.",
                "if (list.get(i) == target) return i + 1;   // was: return i"),
            new MutantDefinition("M5", "zero-not-found",
                "Returns 0 instead of -1 when the target is not found.",
                "return 0;   // was: return -1"),
            new MutantDefinition("M6", "greater-equal",
                "Uses >= comparison instead of == (equality).",
                "if (list.get(i) >= target) return i;   // was: =="),
            new MutantDefinition("M7", "reference-equality",
                "Simulates the Java Integer == bug: comparison only succeeds when both " +
                "values are equal AND in the Integer cache range [-128, 127].",
                "Integer t = target;\nif (list.get(i) == t) return i;   // compares references, not values")
        );

        mutantImpls = new LinkedHashMap<>();
        mutantImpls.put("M1", IndexOfProblem::indexOfM1);
        mutantImpls.put("M2", IndexOfProblem::indexOfM2);
        mutantImpls.put("M3", IndexOfProblem::indexOfM3);
        mutantImpls.put("M4", IndexOfProblem::indexOfM4);
        mutantImpls.put("M5", IndexOfProblem::indexOfM5);
        mutantImpls.put("M6", IndexOfProblem::indexOfM6);
        mutantImpls.put("M7", IndexOfProblem::indexOfM7);
    }

    // ── Problem metadata ──────────────────────────────

    @Override public String getId() { return "indexOf"; }

    @Override public String getTitle() { return "indexOf"; }

    @Override
    public String getSpecification() {
        return """
            <code>int indexOf(List&lt;Integer&gt; list, int target)</code>
            <p>Returns the index of the <strong>first occurrence</strong> of \
            <code>target</code> in <code>list</code>, \
            or <strong>-1</strong> if <code>target</code> is not present.</p>
            <p><em>Examples:</em></p>
            <ul>
              <li><code>indexOf([3, 7, 3], 7)</code> → <code>1</code></li>
              <li><code>indexOf([1, 2, 3], 5)</code> → <code>-1</code></li>
              <li><code>indexOf([], 1)</code> → <code>-1</code></li>
            </ul>""";
    }

    @Override
    public String getReferenceCode() {
        return """
            int indexOf(List<Integer> list, int target) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == target) return i;
                }
                return -1;
            }""";
    }

    @Override
    public List<InputFieldDef> getFunctionInputFields() {
        return List.of(
            new InputFieldDef("list",   "test.list.label",   "test.list.placeholder",   "numeric"),
            new InputFieldDef("target", "test.target.label", "test.target.placeholder", "numeric")
        );
    }

    @Override
    public InputFieldDef getExpectedField() {
        return new InputFieldDef("expected", "test.expected.label", "test.expected.placeholder", "numeric");
    }

    @Override
    public List<MutantDefinition> getMutants() {
        return mutants;
    }

    // ── Validation ────────────────────────────────────

    @Override
    public void validate(Map<String, String> rawInputs) {
        parseList(rawInputs.getOrDefault("list", ""));
        parseInt(rawInputs.getOrDefault("target", ""),   "test.error.target.format");
        parseInt(rawInputs.getOrDefault("expected", ""), "test.error.expected.format");
    }

    // ── Execution ─────────────────────────────────────

    @Override
    public String executeReference(Map<String, String> rawInputs) {
        List<Integer> list = parseList(rawInputs.get("list"));
        int target = parseInt(rawInputs.get("target"), "test.error.target.format");
        return String.valueOf(indexOfReference(list, target));
    }

    /** Canonical form, so "+1", "01" and " 1 " all compare equal to the reference's "1". */
    @Override
    public String getExpected(Map<String, String> rawInputs) {
        return String.valueOf(parseInt(rawInputs.get("expected"), "test.error.expected.format"));
    }

    @Override
    public ExecutionResult executeMutant(String mutantId, Map<String, String> rawInputs) {
        BiFunction<List<Integer>, Integer, Integer> impl = mutantImpls.get(mutantId);
        if (impl == null) throw new IllegalArgumentException("Unknown mutant: " + mutantId);
        try {
            List<Integer> list = parseList(rawInputs.get("list"));
            int target = parseInt(rawInputs.get("target"), "test.error.target.format");
            return ExecutionResult.of(String.valueOf(impl.apply(list, target)));
        } catch (Exception e) {
            return ExecutionResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ── Parsing helpers ───────────────────────────────

    /**
     * Parses "1,2,3", "1, 2, 3", "[1,2,3]" or "1 2 3". Empty (or "[]") is the empty list.
     * Empty items such as "1,,2" or a trailing comma are rejected.
     */
    static List<Integer> parseList(String raw) {
        if (raw == null) raw = "";
        raw = raw.trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }
        if (raw.isEmpty()) return List.of();

        String[] parts = raw.contains(",") ? raw.split(",", -1) : raw.split("\\s+");
        if (parts.length > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("test.error.list.tooMany");
        }
        List<Integer> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String s = part.trim();
            if (s.isEmpty()) throw new IllegalArgumentException("test.error.list.format");
            result.add(parseInt(s, "test.error.list.format"));
        }
        return result;
    }

    static int parseInt(String raw, String errorKey) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(errorKey);
        }
        // mobile keyboards / autocorrect may produce a Unicode minus or en dash
        String s = raw.trim().replace('\u2212', '-').replace('\u2013', '-');
        if (!s.matches("[+-]?\\d+")) {
            throw new IllegalArgumentException(errorKey);
        }
        try {
            long v = Long.parseLong(s);
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("test.error.value.range");
            }
            return (int) v;
        } catch (NumberFormatException e) {
            // syntactically an integer but does not even fit in a long
            throw new IllegalArgumentException("test.error.value.range");
        }
    }

    // ── Reference implementation ──────────────────────

    public static int indexOfReference(List<Integer> list, int target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() == target) return i;
        }
        return -1;
    }

    // ── Mutant implementations ────────────────────────

    /** M1 – Loop starts at i = 1 */
    public static int indexOfM1(List<Integer> list, int target) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).intValue() == target) return i;
        }
        return -1;
    }

    /** M2 – Loop condition i < list.size() - 1 */
    public static int indexOfM2(List<Integer> list, int target) {
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i).intValue() == target) return i;
        }
        return -1;
    }

    /** M3 – Returns last occurrence */
    public static int indexOfM3(List<Integer> list, int target) {
        int result = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() == target) result = i;
        }
        return result;
    }

    /** M4 – Returns i + 1 */
    public static int indexOfM4(List<Integer> list, int target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() == target) return i + 1;
        }
        return -1;
    }

    /** M5 – Returns 0 instead of -1 */
    public static int indexOfM5(List<Integer> list, int target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() == target) return i;
        }
        return 0;
    }

    /** M6 – Uses >= instead of == */
    public static int indexOfM6(List<Integer> list, int target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).intValue() >= target) return i;
        }
        return -1;
    }

    /**
     * M7 – Simulates Integer reference-equality (==) bug.
     * Comparison only succeeds when values are equal AND in [-128, 127].
     */
    public static int indexOfM7(List<Integer> list, int target) {
        for (int i = 0; i < list.size(); i++) {
            int val = list.get(i).intValue();
            if (val == target && target >= -128 && target <= 127) return i;
        }
        return -1;
    }
}
