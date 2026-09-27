package com.seng403.mutanthunt.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnonymousNameGeneratorTest {

    @Test
    void generate_returnsNonNull() {
        AnonymousNameGenerator gen = new AnonymousNameGenerator();
        assertNotNull(gen.generate());
    }

    @Test
    void generate_returnsUniqueNames() {
        AnonymousNameGenerator gen = new AnonymousNameGenerator();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            assertTrue(names.add(gen.generate()), "Name should be unique");
        }
    }

    @Test
    void generate_followsExpectedFormat() {
        AnonymousNameGenerator gen = new AnonymousNameGenerator();
        String name = gen.generate();
        assertTrue(name.matches("\\w+ \\w+ #\\d+"),
            "Name should match 'Color Animal #N' format, got: " + name);
    }

    @Test
    void generate_firstNameStartsWithRed() {
        AnonymousNameGenerator gen = new AnonymousNameGenerator();
        String name = gen.generate();
        assertTrue(name.startsWith("Red"), "First name should start with Red, got: " + name);
    }

    @Test
    void generate_sequentialNumbering() {
        AnonymousNameGenerator gen = new AnonymousNameGenerator();
        String first = gen.generate();
        String second = gen.generate();
        assertTrue(first.endsWith("#1"), "First name should end with #1, got: " + first);
        assertTrue(second.endsWith("#2"), "Second name should end with #2, got: " + second);
    }

    @Test
    void generate_wrapsAroundColors() {
        AnonymousNameGenerator gen = new AnonymousNameGenerator();
        for (int i = 0; i < 20; i++) {
            gen.generate();
        }
        String name21 = gen.generate();
        assertTrue(name21.startsWith("Red"), "After 20 names, color should wrap to Red, got: " + name21);
        assertTrue(name21.contains("Falcon"), "After 20 names, animal should be Falcon, got: " + name21);
    }
}
