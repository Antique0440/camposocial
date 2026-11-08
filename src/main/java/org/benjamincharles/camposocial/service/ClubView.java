package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Club;

/** A club prepared for rendering, tailored to the current viewer. */
public record ClubView(Club club, long memberCount, boolean joined, boolean canManage) {
}
