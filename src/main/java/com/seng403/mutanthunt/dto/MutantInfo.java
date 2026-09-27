package com.seng403.mutanthunt.dto;

public record MutantInfo(String id, String shortName, String description, String code) {
    public MutantInfo(String id, String shortName, String description) {
        this(id, shortName, description, null);
    }
}
