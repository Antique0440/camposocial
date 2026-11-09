package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Conversation;
import org.benjamincharles.camposocial.entity.Message;
import org.benjamincharles.camposocial.entity.User;

import java.util.List;

/** An opened conversation with its most recent messages in chronological order. */
public record ThreadView(Conversation conversation, User other, List<Message> messages) {
}

