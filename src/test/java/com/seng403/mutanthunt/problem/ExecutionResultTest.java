package com.seng403.mutanthunt.problem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionResultTest {

    @Test
    void of_createsValueResult() {
        ExecutionResult r = ExecutionResult.of("42");
        assertEquals("42", r.value());
        assertNull(r.exception());
        assertFalse(r.isException());
    }

    @Test
    void error_createsExceptionResult() {
        ExecutionResult r = ExecutionResult.error("NPE");
        assertNull(r.value());
        assertEquals("NPE", r.exception());
        assertTrue(r.isException());
    }

    @Test
    void display_normalValue() {
        ExecutionResult r = ExecutionResult.of("0");
        assertEquals("0", r.display());
    }

    @Test
    void display_exceptionValue() {
        ExecutionResult r = ExecutionResult.error("IndexOutOfBoundsException");
        assertEquals("EXCEPTION: IndexOutOfBoundsException", r.display());
    }

    @Test
    void of_nullValue() {
        ExecutionResult r = ExecutionResult.of(null);
        assertNull(r.value());
        assertFalse(r.isException());
    }
}
