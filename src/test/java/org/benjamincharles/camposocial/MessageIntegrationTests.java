package org.benjamincharles.camposocial;

import org.benjamincharles.camposocial.entity.Conversation;
import org.benjamincharles.camposocial.entity.Message;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubRepository;
import org.benjamincharles.camposocial.repository.ConversationRepository;
import org.benjamincharles.camposocial.repository.MessageRepository;
import org.benjamincharles.camposocial.repository.PostRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class MessageIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private User kofi;
    private User ama;
    private User yaw;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        clubRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();

        kofi = userRepository.save(new User("kofi", "kofi@ashesi.edu.gh", "pass"));
        ama = userRepository.save(new User("ama", "ama@ashesi.edu.gh", "pass"));
        yaw = userRepository.save(new User("yaw", "yaw@ashesi.edu.gh", "pass"));
    }

    @Test
    void startConversationSuccessfullyAndCheckInbox() throws Exception {
        // kofi starts a conversation with ama
        mockMvc.perform(post("/messages/new")
                        .with(user("kofi"))
                        .with(csrf())
                        .param("username", "ama"))
                .andExpect(status().is3xxRedirection());

        Conversation conversation = conversationRepository.findByUserOneIdAndUserTwoId(kofi.getId(), ama.getId())
                .or(() -> conversationRepository.findByUserOneIdAndUserTwoId(ama.getId(), kofi.getId()))
                .orElseThrow();

        // kofi sends a message
        mockMvc.perform(post("/messages/" + conversation.getId())
                        .with(user("kofi"))
                        .with(csrf())
                        .param("body", "Hey Ama, are you attending the hackathon?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages/" + conversation.getId()));

        assertThat(messageRepository.count()).isEqualTo(1);

        // ama views inbox: sees conversation with kofi and unread count = 1
        mockMvc.perform(get("/messages")
                        .with(user("ama")))
                .andExpect(status().isOk())
                .andExpect(view().name("messages"))
                .andExpect(content().string(containsString("kofi")))
                .andExpect(content().string(containsString("Hey Ama, are you attending the hackathon?")));

        // ama checks feed, navbar should have unread badge
        mockMvc.perform(get("/feed")
                        .with(user("ama")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Messages")));
    }

    @Test
    void openingConversationMarksMessagesAsSeen() throws Exception {
        User first = kofi.getId() < ama.getId() ? kofi : ama;
        User second = first == kofi ? ama : kofi;
        Conversation conversation = conversationRepository.save(new Conversation(first, second));
        Message message = messageRepository.save(new Message(conversation, kofi, "Hello from Kofi!"));

        assertThat(message.isSeen()).isFalse();

        // ama opens conversation
        mockMvc.perform(get("/messages/" + conversation.getId())
                        .with(user("ama")))
                .andExpect(status().isOk())
                .andExpect(view().name("conversation"))
                .andExpect(content().string(containsString("Hello from Kofi!")));

        Message reloaded = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(reloaded.isSeen()).isTrue();
    }

    @Test
    void replyMessageAppendsToThread() throws Exception {
        User first = kofi.getId() < ama.getId() ? kofi : ama;
        User second = first == kofi ? ama : kofi;
        Conversation conversation = conversationRepository.save(new Conversation(first, second));
        messageRepository.save(new Message(conversation, kofi, "Message 1"));

        // ama replies
        mockMvc.perform(post("/messages/" + conversation.getId())
                        .with(user("ama"))
                        .with(csrf())
                        .param("body", "Message 2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages/" + conversation.getId()));

        mockMvc.perform(get("/messages/" + conversation.getId())
                        .with(user("ama")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Message 1")))
                .andExpect(content().string(containsString("Message 2")));
    }

    @Test
    void validationWhenStartingConversation() throws Exception {
        // messaging oneself
        mockMvc.perform(post("/messages/new")
                        .with(user("kofi"))
                        .with(csrf())
                        .param("username", "kofi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages"))
                .andExpect(flash().attribute("error", "You cannot message yourself."));

        // messaging non-existent user
        mockMvc.perform(post("/messages/new")
                        .with(user("kofi"))
                        .with(csrf())
                        .param("username", "ghost_user"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages"))
                .andExpect(flash().attribute("error", "No student found with that username."));

        // blank username
        mockMvc.perform(post("/messages/new")
                        .with(user("kofi"))
                        .with(csrf())
                        .param("username", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages"))
                .andExpect(flash().attribute("error", "Enter the username of the student you want to message."));
    }

    @Test
    void nonParticipantCannotAccessOrPostToConversation() throws Exception {
        User first = kofi.getId() < ama.getId() ? kofi : ama;
        User second = first == kofi ? ama : kofi;
        Conversation conversation = conversationRepository.save(new Conversation(first, second));

        // yaw tries to read kofi & ama's conversation
        mockMvc.perform(get("/messages/" + conversation.getId())
                        .with(user("yaw")))
                .andExpect(status().isNotFound());

        // yaw tries to post to kofi & ama's conversation
        mockMvc.perform(post("/messages/" + conversation.getId())
                        .with(user("yaw"))
                        .with(csrf())
                        .param("body", "Eavesdropping!"))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyOrTooLongMessageIsRejected() throws Exception {
        User first = kofi.getId() < ama.getId() ? kofi : ama;
        User second = first == kofi ? ama : kofi;
        Conversation conversation = conversationRepository.save(new Conversation(first, second));

        // empty message
        mockMvc.perform(post("/messages/" + conversation.getId())
                        .with(user("kofi"))
                        .with(csrf())
                        .param("body", "    "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Message cannot be empty."));

        // message > 1000 characters
        String longText = "a".repeat(1001);
        mockMvc.perform(post("/messages/" + conversation.getId())
                        .with(user("kofi"))
                        .with(csrf())
                        .param("body", longText))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Message cannot exceed 1000 characters."));

        assertThat(messageRepository.count()).isZero();
    }
}

