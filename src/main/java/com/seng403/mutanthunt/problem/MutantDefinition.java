package com.seng403.mutanthunt.problem;

/**
 * Describes one mutant (faulty version) of a problem's reference implementation.
 * The executable implementation lives in the owning {@link Problem} and is invoked via
 * {@link Problem#executeMutant(String, java.util.Map)}.
 *
 * @param id          unique identifier, e.g. "M1"
 * @param shortName   short label, e.g. "start-at-one"
 * @param description human-readable explanation of the mutation
 * @param code        source of the mutated implementation (shown only after reveal)
 */
public record MutantDefinition(String id, String shortName, String description, String code) {

    public MutantDefinition(String id, String shortName, String description) {
        this(id, shortName, description, null);
    }
}
