package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Comment;
import org.benjamincharles.camposocial.entity.Post;

import java.util.List;

/** Read model of a post prepared for rendering, tailored to the current viewer. */
public record PostView(
        Post post,
        long agreeCount,
        boolean agreedByMe,
        boolean canDelete,
        boolean canPin,
        List<Comment> comments
) {
}
