package org.benjamincharles.camposocial.entity;

public enum PostCategory {
    INSIDE_JOKE("#InsideJoke"),
    ANNOUNCEMENT("#Announcement"),
    GENERAL_DISCUSSION("#GeneralDiscussion"),
    ACADEMIC("#Academic");

    private final String label;

    PostCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Parses a request parameter into a category, returning {@code null} when blank or unknown. */
    public static PostCategory parse(String value) {
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

