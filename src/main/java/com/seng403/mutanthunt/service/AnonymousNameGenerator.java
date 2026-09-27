package com.seng403.mutanthunt.service;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates fun anonymous display names like "Blue Tiger #12".
 */
@Component
public class AnonymousNameGenerator {

    private static final List<String> COLORS = List.of(
        "Red", "Blue", "Green", "Purple", "Orange", "Silver", "Gold",
        "Crimson", "Cyan", "Amber", "Violet", "Teal", "Coral", "Indigo",
        "Scarlet", "Emerald", "Cobalt", "Ruby", "Jade", "Bronze"
    );

    private static final List<String> ANIMALS = List.of(
        "Tiger", "Falcon", "Wolf", "Hawk", "Panther", "Eagle", "Fox",
        "Lynx", "Shark", "Cobra", "Phoenix", "Dragon", "Bear", "Raven",
        "Jaguar", "Viper", "Owl", "Lion", "Puma", "Mantis"
    );

    private final AtomicInteger counter = new AtomicInteger(0);

    public String generate() {
        return generate(counter.incrementAndGet());
    }

    /** Deterministic name for a (persistent) number, so names survive restarts without clashing. */
    public String generate(long number) {
        int n = (int) Math.max(1, number);
        String color = COLORS.get((n - 1) % COLORS.size());
        String animal = ANIMALS.get(((n - 1) / COLORS.size()) % ANIMALS.size());
        return color + " " + animal + " #" + n;
    }
}
