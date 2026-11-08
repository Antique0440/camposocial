package org.benjamincharles.camposocial.controller;

import java.util.regex.Pattern;

/** Builds redirects to a page the user came from, restricted to a whitelist of local paths. */
final class Redirects {

    private static final Pattern SAFE_PATH =
            Pattern.compile("^/(feed|clubs(/\\d+)?)(\\?[A-Za-z0-9=&_%-]*)?$");

    private Redirects() {
    }

    /** Redirects to {@code returnTo} when it is a known local path, otherwise to {@code /feed}. */
    static String to(String returnTo, Long postId) {
        String path = returnTo != null && SAFE_PATH.matcher(returnTo).matches() ? returnTo : "/feed";
        return "redirect:" + path + (postId == null ? "" : "#post-" + postId);
    }
}

