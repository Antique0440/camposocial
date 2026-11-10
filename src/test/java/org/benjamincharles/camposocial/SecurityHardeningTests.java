package org.benjamincharles.camposocial;

import org.benjamincharles.camposocial.entity.ClubCategory;
import org.benjamincharles.camposocial.entity.Post;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubRepository;
import org.benjamincharles.camposocial.repository.ConversationRepository;
import org.benjamincharles.camposocial.repository.PostRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.benjamincharles.camposocial.security.InputSanitizer;
import org.benjamincharles.camposocial.security.RateLimitingFilter;
import org.benjamincharles.camposocial.service.ClubService;
import org.benjamincharles.camposocial.service.PostService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private Flyway flyway;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private ClubService clubService;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.reset();
        postRepository.deleteAll();
        clubRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void flywayMigrationSuccessfullyInitializedSchema() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }

    @Test
    void inputSanitizerNeutralizesDangerousMarkup() {
        String xssPayload = "<script>alert('xss')</script>Clean Content";
        assertThat(InputSanitizer.sanitize(xssPayload)).isEqualTo("Clean Content");

        String iframePayload = "<iframe src='evil.com'></iframe>Important Note";
        assertThat(InputSanitizer.sanitize(iframePayload)).isEqualTo("Important Note");

        String onEventPayload = "<img src='photo.jpg' onerror='hack()'/>Picture caption";
        assertThat(InputSanitizer.sanitize(onEventPayload)).isEqualTo("<img src='photo.jpg'/>Picture caption");
    }

    @Test
    void postsAndCommentsAreSanitizedBeforeStorage() {
        User user = userRepository.save(new User("kofi", "kofi@ashesi.edu.gh", "pwd"));

        Post post = postService.createPost("kofi",
                "<script>evil()</script>Legitimate campus announcement",
                PostCategory.ANNOUNCEMENT);
        assertThat(post.getContent()).isEqualTo("Legitimate campus announcement");

        var comment = postService.addComment("kofi", post.getId(),
                "<style>body {display:none;}</style>Great post!");
        assertThat(comment.getBody()).isEqualTo("Great post!");
    }

    @Test
    void clubsAreSanitizedBeforeStorage() {
        User user = userRepository.save(new User("kofi", "kofi@ashesi.edu.gh", "pwd"));

        var club = clubService.createClub("kofi",
                "<script>hack()</script>Robotics Society",
                "<script>alert(1)</script>Build autonomous hardware.",
                ClubCategory.TECH);

        assertThat(club.getName()).isEqualTo("Robotics Society");
        assertThat(club.getDescription()).isEqualTo("Build autonomous hardware.");
    }

    @Test
    void signupRateLimitBlocksExcessiveAttempts() throws Exception {
        // Limit is 5 signup attempts per minute per IP
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/signup").with(csrf())
                            .param("username", "student" + i)
                            .param("email", "student" + i + "@ashesi.edu.gh")
                            .param("password", "strongPass123"))
                    .andExpect(status().is3xxRedirection());
        }

        // 6th attempt exceeds threshold -> 429 Too Many Requests
        mockMvc.perform(post("/signup").with(csrf())
                        .param("username", "student6")
                        .param("email", "student6@ashesi.edu.gh")
                        .param("password", "strongPass123"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }
}

