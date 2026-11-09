package org.benjamincharles.camposocial;

import org.benjamincharles.camposocial.entity.Post;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubRepository;
import org.benjamincharles.camposocial.repository.CommentRepository;
import org.benjamincharles.camposocial.repository.ConversationRepository;
import org.benjamincharles.camposocial.repository.PostRepository;
import org.benjamincharles.camposocial.repository.ReactionRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FeedIntegrationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private ReactionRepository reactionRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private ConversationRepository conversationRepository;

    private User kofi;
    private User ama;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        clubRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        kofi = userRepository.save(new User("kofi", "kofi@ashesi.edu.gh", "x"));
        ama = userRepository.save(new User("ama", "ama@ashesi.edu.gh", "x"));
    }

    private Post savePost(User author, String content) {
        return postRepository.save(new Post(author, content, PostCategory.INSIDE_JOKE));
    }

    @Test
    @WithMockUser(username = "kofi")
    void createdPostAppearsOnFeed() throws Exception {
        mockMvc.perform(post("/posts").with(csrf())
                        .param("content", "hello campus")
                        .param("category", "ANNOUNCEMENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));

        assertThat(postRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hello campus")))
                .andExpect(content().string(containsString("#Announcement")));
    }

    @Test
    @WithMockUser(username = "kofi")
    void blankPostIsRejected() throws Exception {
        mockMvc.perform(post("/posts").with(csrf())
                        .param("content", "   ")
                        .param("category", "ACADEMIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(postRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "kofi")
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/posts")
                        .param("content", "no token")
                        .param("category", "ACADEMIC"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "kofi")
    void agreeTogglesOnAndOff() throws Exception {
        Post post = savePost(ama, "funny story");

        mockMvc.perform(post("/posts/{id}/agree", post.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reactionRepository.countByPostId(post.getId())).isEqualTo(1);

        mockMvc.perform(post("/posts/{id}/agree", post.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(reactionRepository.countByPostId(post.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "kofi")
    void commentIsSavedAndShown() throws Exception {
        Post post = savePost(ama, "funny story");

        mockMvc.perform(post("/posts/{id}/comments", post.getId()).with(csrf())
                        .param("body", "haha same"))
                .andExpect(status().is3xxRedirection());

        assertThat(commentRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/feed"))
                .andExpect(content().string(containsString("haha same")));
    }

    @Test
    @WithMockUser(username = "kofi")
    void nonAuthorCannotDeletePost() throws Exception {
        Post post = savePost(ama, "ama's post");

        mockMvc.perform(post("/posts/{id}/delete", post.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(postRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "kofi")
    void authorCanDeleteOwnPostWithItsComments() throws Exception {
        Post post = savePost(kofi, "kofi's post");
        mockMvc.perform(post("/posts/{id}/agree", post.getId()).with(csrf()));
        mockMvc.perform(post("/posts/{id}/comments", post.getId()).with(csrf()).param("body", "me too"));

        mockMvc.perform(post("/posts/{id}/delete", post.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(postRepository.count()).isZero();
        assertThat(commentRepository.count()).isZero();
        assertThat(reactionRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "mod", authorities = "ROLE_CAMPUS_ADMIN")
    void campusAdminCanDeleteAnyPost() throws Exception {
        Post post = savePost(ama, "flagged post");

        mockMvc.perform(post("/posts/{id}/delete", post.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(postRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "kofi")
    void topSortAndCategoryFilterRender() throws Exception {
        savePost(ama, "joke post");
        postRepository.save(new Post(ama, "study tips", PostCategory.ACADEMIC));

        mockMvc.perform(get("/feed").param("sort", "top"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("joke post")));

        mockMvc.perform(get("/feed").param("category", "ACADEMIC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("study tips")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("joke post"))));
    }
}

