package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Conversation;
import org.benjamincharles.camposocial.entity.Message;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ConversationRepository;
import org.benjamincharles.camposocial.repository.MessageRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.benjamincharles.camposocial.security.InputSanitizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
public class MessageService {

    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MAX_VISIBLE_MESSAGES = 100;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /** Finds the conversation between the two students, creating it if this is their first contact. */
    public Conversation startConversation(String username, String otherUsername) {
        String target = otherUsername == null ? "" : otherUsername.trim();
        if (target.isEmpty()) {
            throw new IllegalArgumentException("Enter the username of the student you want to message.");
        }
        if (target.equals(username)) {
            throw new IllegalArgumentException("You cannot message yourself.");
        }
        User me = findUser(username);
        User other = userRepository.findByUsername(target)
                .orElseThrow(() -> new IllegalArgumentException("No student found with that username."));

        User first = me.getId() < other.getId() ? me : other;
        User second = first == me ? other : me;
        return conversationRepository.findByUserOneIdAndUserTwoId(first.getId(), second.getId())
                .orElseGet(() -> conversationRepository.save(new Conversation(first, second)));
    }

    @Transactional(readOnly = true)
    public List<ConversationView> getInbox(String username) {
        return conversationRepository.findForUser(username).stream()
                .map(conversation -> new ConversationView(
                        conversation,
                        conversation.otherParticipant(username),
                        messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId())
                                .orElse(null),
                        messageRepository.countByConversationIdAndSenderUsernameNotAndSeenFalse(
                                conversation.getId(), username)))
                .toList();
    }

    /** Opens a conversation for a participant and marks the messages sent to them as seen. */
    public ThreadView openConversation(Long conversationId, String username) {
        Conversation conversation = findConversationFor(conversationId, username);
        messageRepository.findByConversationIdAndSenderUsernameNotAndSeenFalse(conversationId, username)
                .forEach(message -> message.setSeen(true));

        List<Message> latest = new ArrayList<>(messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId, PageRequest.of(0, MAX_VISIBLE_MESSAGES)));
        Collections.reverse(latest);
        return new ThreadView(conversation, conversation.otherParticipant(username), latest);
    }

    public Message send(String username, Long conversationId, String body) {
        Conversation conversation = findConversationFor(conversationId, username);
        String text = InputSanitizer.sanitize(body);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Message cannot be empty.");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message cannot exceed " + MAX_MESSAGE_LENGTH + " characters.");
        }
        Message message = messageRepository.save(new Message(conversation, findUser(username), text));
        conversation.setLastMessageAt(message.getCreatedAt());
        return message;
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return messageRepository.countUnreadFor(username);
    }

    /** Non-participants get a 404 so the existence of other people's conversations is not revealed. */
    private Conversation findConversationFor(Long conversationId, String username) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.hasParticipant(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}

