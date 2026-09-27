package com.seng403.mutanthunt.problem;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry of all available problems.
 * Add new Problem implementations here.
 */
@Component
public class ProblemRegistry {

    private final Map<String, Problem> problems = new LinkedHashMap<>();

    public ProblemRegistry() {
        register(new IndexOfProblem());
    }

    private void register(Problem p) {
        problems.put(p.getId(), p);
    }

    public Problem get(String id) {
        return problems.get(id);
    }

    public List<Problem> getAll() {
        return List.copyOf(problems.values());
    }

    public Problem getDefault() {
        return problems.values().iterator().next();
    }
}
