package org.benjamincharles.camposocial;

import org.benjamincharles.camposocial.config.DataSeeder;
import org.benjamincharles.camposocial.entity.Club;
import org.benjamincharles.camposocial.entity.ClubCategory;
import org.benjamincharles.camposocial.entity.Post;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubMembershipRepository;
import org.benjamincharles.camposocial.repository.ClubRepository;
import org.benjamincharles.camposocial.repository.ConversationRepository;
import org.benjamincharles.camposocial.repository.PostRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ClubIntegrationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private ClubMembershipRepository membershipRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private DataSeeder dataSeeder;

    private User kofi;
    private Club tech;
    private Club sports;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        clubRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
        kofi = userRepository.save(new User("kofi", "kofi@ashesi.edu.gh", "x"));
        tech = clubRepository.save(new Club("Tech Society", "Code together", ClubCategory.TECH, kofi));
        sports = clubRepository.save(new Club("Sports Hub", "Play together", ClubCategory.SPORTS, kofi));
    }

    @Test
    void seederCreatesInitialClubsOnlyOnce() {
        clubRepository.deleteAll();

        dataSeeder.run(null);
        dataSeeder.run(null);

        assertThat(clubRepository.findAll()).extracting(Club::getName)
                .containsExactlyInAnyOrder("Tech Society", "Arts & Media", "Sports Hub");
    }

    @Test
    @WithMockUser(username = "kofi")
    void directoryListsClubsAndFiltersByCategory() throws Exception {
        mockMvc.perform(get("/clubs"))
                .andExpect(status().isOk())
                .andExpect(view().name("clubs"))
                .andExpect(content().string(containsString("Tech Society")))
                .andExpect(content().string(containsString("Sports Hub")));

        mockMvc.perform(get("/clubs").param("category", "SPORTS"))
                .andExpect(content().string(containsString("Sports Hub")))
                .andExpect(content().string(not(containsString("Tech Society"))));
    }

    @Test
    @WithMockUser(username = "kofi")
    void joinIsIdempotentAndLeaveRemovesMembership() throws Exception {
        mockMvc.perform(post("/clubs/{id}/join", tech.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clubs/" + tech.getId()));
        mockMvc.perform(post("/clubs/{id}/join", tech.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(membershipRepository.countByClubId(tech.getId())).isEqualTo(1);

        mockMvc.perform(post("/clubs/{id}/leave", tech.getId()).with(csrf()).param("returnTo", "/clubs"))
                .andExpect(redirectedUrl("/clubs"));

        assertThat(membershipRepository.countByClubId(tech.getId())).isZero();
    }

    @Test
    @WithMockUser(username = "kofi")
    void memberPostsToClubAndItAppearsOnClubPageAndCampusFeed() throws Exception {
        mockMvc.perform(post("/clubs/{id}/join", tech.getId()).with(csrf()));

        mockMvc.perform(post("/clubs/{id}/posts", tech.getId()).with(csrf())
                        .param("content", "hack night friday")
                        .param("category", "ANNOUNCEMENT"))
                .andExpect(redirectedUrl("/clubs/" + tech.getId()));

        mockMvc.perform(get("/clubs/{id}", tech.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("club"))
                .andExpect(content().string(containsString("hack night friday")))
                .andExpect(content().string(containsString("kofi")));

        mockMvc.perform(get("/feed"))
                .andExpect(content().string(containsString("hack night friday")))
                .andExpect(content().string(containsString("Tech Society")));
    }

    @Test
    @WithMockUser(username = "kofi")
    void nonMemberCannotPostToClub() throws Exception {
        mockMvc.perform(post("/clubs/{id}/posts", tech.getId()).with(csrf())
                        .param("content", "sneaky post")
                        .param("category", "ACADEMIC"))
                .andExpect(status().isForbidden());

        assertThat(postRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "kofi")
    void clubPageOnlyShowsThatClubsPosts() throws Exception {
        postRepository.save(clubPost("tech only post", tech));
        postRepository.save(clubPost("sports only post", sports));

        mockMvc.perform(get("/clubs/{id}", tech.getId()))
                .andExpect(content().string(containsString("tech only post")))
                .andExpect(content().string(not(containsString("sports only post"))));
    }

    @Test
    @WithMockUser(username = "kofi")
    void unknownClubReturnsNotFound() throws Exception {
        mockMvc.perform(get("/clubs/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "kofi")
    void agreeReturnsToWhitelistedPageButNeverToExternalUrls() throws Exception {
        Post post = postRepository.save(clubPost("some post", tech));

        mockMvc.perform(post("/posts/{id}/agree", post.getId()).with(csrf())
                        .param("returnTo", "/clubs/" + tech.getId()))
                .andExpect(redirectedUrl("/clubs/" + tech.getId() + "#post-" + post.getId()));

        mockMvc.perform(post("/posts/{id}/agree", post.getId()).with(csrf())
                        .param("returnTo", "//evil.example.com"))
                .andExpect(redirectedUrl("/feed#post-" + post.getId()));
    }

    private Post clubPost(String content, Club club) {
        Post post = new Post(kofi, content, PostCategory.GENERAL_DISCUSSION);
        post.setClub(club);
        return post;
    }
}

