package org.benjamincharles.camposocial.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Defensive utility to sanitize user text inputs and neutralize raw script / HTML tags
 * before persisting them to the database.
 */
@Component
public class InputSanitizer {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<\\s*(script|iframe|object|embed|applet|style)[^>]*>.*?</\\s*\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern SELF_CLOSING_DANGEROUS_TAGS = Pattern.compile(
            "<\\s*(script|iframe|object|embed|applet|link|meta)[^>]*/>",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ON_EVENT_ATTRIBUTES = Pattern.compile(
            "\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JAVASCRIPT_SCHEME = Pattern.compile(
            "javascript:",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Sanitizes user input string by stripping malicious script tags and inline handlers.
     * Returns null if input is null.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String clean = SCRIPT_PATTERN.matcher(input).replaceAll("");
        clean = SELF_CLOSING_DANGEROUS_TAGS.matcher(clean).replaceAll("");
        clean = ON_EVENT_ATTRIBUTES.matcher(clean).replaceAll("");
        clean = JAVASCRIPT_SCHEME.matcher(clean).replaceAll("");
        return clean.trim();
    }
}

