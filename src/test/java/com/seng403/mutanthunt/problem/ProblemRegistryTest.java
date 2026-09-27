package com.seng403.mutanthunt.problem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProblemRegistryTest {

    private ProblemRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProblemRegistry();
    }

    @Test
    void get_existingProblem() {
        Problem p = registry.get("indexOf");
        assertNotNull(p);
        assertEquals("indexOf", p.getId());
    }

    @Test
    void get_nonExistingProblem() {
        assertNull(registry.get("nonExistent"));
    }

    @Test
    void getAll_notEmpty() {
        List<Problem> all = registry.getAll();
        assertFalse(all.isEmpty());
        assertTrue(all.stream().anyMatch(p -> "indexOf".equals(p.getId())));
    }

    @Test
    void getDefault_returnsFirstRegistered() {
        Problem p = registry.getDefault();
        assertNotNull(p);
        assertEquals("indexOf", p.getId());
    }

    @Test
    void getAll_isImmutable() {
        List<Problem> all = registry.getAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }
}
