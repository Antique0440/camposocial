package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Conversation;
import org.benjamincharles.camposocial.entity.Message;
import org.benjamincharles.camposocial.entity.User;

/** One row of the inbox, from the viewer's point of view. {@code lastMessage} is null for an empty conversation. */
public record ConversationView(Conversation conversation, User other, Message lastMessage, long unreadCount) {
}

