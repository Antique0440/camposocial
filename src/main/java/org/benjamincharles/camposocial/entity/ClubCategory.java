package org.benjamincharles.camposocial.entity;

public enum ClubCategory {
    TECH("Tech"),
    ARTS("Arts"),
    SPORTS("Sports"),
    ACADEMIC("Academic");

    private final String label;

    ClubCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Parses a request parameter into a category, returning {@code null} when blank or unknown. */
    public static ClubCategory parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

