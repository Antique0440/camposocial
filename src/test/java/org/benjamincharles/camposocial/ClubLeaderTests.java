package org.benjamincharles.camposocial;

import org.benjamincharles.camposocial.entity.Club;
import org.benjamincharles.camposocial.entity.ClubCategory;
import org.benjamincharles.camposocial.entity.ClubMembership;
import org.benjamincharles.camposocial.entity.ClubRole;
import org.benjamincharles.camposocial.entity.Post;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubMembershipRepository;
import org.benjamincharles.camposocial.repository.ClubRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClubLeaderTests {

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

    private User kofi;
    private User ama;
    private User yaw;
    private Club club;
    private ClubMembership kofiMembership;
    private ClubMembership amaMembership;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        clubRepository.deleteAll();
        userRepository.deleteAll();
        kofi = userRepository.save(new User("kofi", "kofi@ashesi.edu.gh", "x"));
        ama = userRepository.save(new User("ama", "ama@ashesi.edu.gh", "x"));
        yaw = userRepository.save(new User("yaw", "yaw@ashesi.edu.gh", "x"));
        club = clubRepository.save(new Club("Tech Society", "Code together", ClubCategory.TECH, kofi));
        kofiMembership = membershipRepository.save(new ClubMembership(kofi, club, ClubRole.LEADER));
        amaMembership = membershipRepository.save(new ClubMembership(ama, club, ClubRole.MEMBER));
    }

    private ClubRole roleOf(User user) {
        return membershipRepository.findByClubIdAndUserUsername(club.getId(), user.getUsername())
                .orElseThrow().getRole();
    }

    private Post clubPost(User author, String content, PostCategory category) {
        Post post = new Post(author, content, category);
        post.setClub(club);
        return postRepository.save(post);
    }

    // ---- creating clubs and becoming a leader ----

    @Test
    void creatorOfANewClubBecomesItsLeader() throws Exception {
        mockMvc.perform(post("/clubs").with(user("yaw")).with(csrf())
                        .param("name", "Chess Club")
                        .param("description", "Think ahead")
                        .param("category", "ACADEMIC"))
                .andExpect(status().is3xxRedirection());

        Club created = clubRepository.findAll().stream()
                .filter(c -> c.getName().equals("Chess Club")).findFirst().orElseThrow();
        assertThat(membershipRepository.findByClubIdAndUserUsername(created.getId(), "yaw").orElseThrow().getRole())
                .isEqualTo(ClubRole.LEADER);
    }

    @Test
    void duplicateOrInvalidClubsAreRejected() throws Exception {
        long before = clubRepository.count();

        mockMvc.perform(post("/clubs").with(user("yaw")).with(csrf())
                        .param("name", "  tech society ")
                        .param("category", "TECH"))
                .andExpect(redirectedUrl("/clubs"))
                .andExpect(flash().attributeExists("error"));

        mockMvc.perform(post("/clubs").with(user("yaw")).with(csrf())
                        .param("name", "   ")
                        .param("category", "TECH"))
                .andExpect(flash().attributeExists("error"));

        mockMvc.perform(post("/clubs").with(user("yaw")).with(csrf())
                        .param("name", "Valid Name")
                        .param("category", "NOPE"))
                .andExpect(flash().attributeExists("error"));

        assertThat(clubRepository.count()).isEqualTo(before);
    }

    @Test
    void nextJoinerOfALeaderlessClubBecomesLeader() throws Exception {
        Club seeded = clubRepository.save(new Club("Arts & Media", "Create", ClubCategory.ARTS, kofi));

        mockMvc.perform(post("/clubs/{id}/join", seeded.getId()).with(user("ama")).with(csrf()));
        mockMvc.perform(post("/clubs/{id}/join", seeded.getId()).with(user("yaw")).with(csrf()));

        assertThat(membershipRepository.findByClubIdAndUserUsername(seeded.getId(), "ama").orElseThrow().getRole())
                .isEqualTo(ClubRole.LEADER);
        assertThat(membershipRepository.findByClubIdAndUserUsername(seeded.getId(), "yaw").orElseThrow().getRole())
                .isEqualTo(ClubRole.MEMBER);
    }

    // ---- managing members ----

    @Test
    void leaderCanPromoteDemoteAndRemoveMembers() throws Exception {
        mockMvc.perform(post("/clubs/{id}/members/{mid}/promote", club.getId(), amaMembership.getId())
                        .with(user("kofi")).with(csrf()))
                .andExpect(redirectedUrl("/clubs/" + club.getId()));
        assertThat(roleOf(ama)).isEqualTo(ClubRole.LEADER);

        mockMvc.perform(post("/clubs/{id}/members/{mid}/demote", club.getId(), amaMembership.getId())
                        .with(user("kofi")).with(csrf()));
        assertThat(roleOf(ama)).isEqualTo(ClubRole.MEMBER);

        mockMvc.perform(post("/clubs/{id}/members/{mid}/remove", club.getId(), amaMembership.getId())
                        .with(user("kofi")).with(csrf()));
        assertThat(membershipRepository.existsByClubIdAndUserUsername(club.getId(), "ama")).isFalse();
    }

    @Test
    void regularMembersAndOutsidersCannotManageMembers() throws Exception {
        mockMvc.perform(post("/clubs/{id}/members/{mid}/remove", club.getId(), kofiMembership.getId())
                        .with(user("ama")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/clubs/{id}/members/{mid}/promote", club.getId(), amaMembership.getId())
                        .with(user("yaw")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(roleOf(kofi)).isEqualTo(ClubRole.LEADER);
        assertThat(roleOf(ama)).isEqualTo(ClubRole.MEMBER);
    }

    @Test
    void leadersCannotRemoveThemselvesDemoteThemselvesOrRemoveOtherLeaders() throws Exception {
        mockMvc.perform(post("/clubs/{id}/members/{mid}/remove", club.getId(), kofiMembership.getId())
                        .with(user("kofi")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        mockMvc.perform(post("/clubs/{id}/members/{mid}/demote", club.getId(), kofiMembership.getId())
                        .with(user("kofi")).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        mockMvc.perform(post("/clubs/{id}/members/{mid}/promote", club.getId(), amaMembership.getId())
                .with(user("kofi")).with(csrf()));
        mockMvc.perform(post("/clubs/{id}/members/{mid}/remove", club.getId(), amaMembership.getId())
                        .with(user("kofi")).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        assertThat(membershipRepository.countByClubId(club.getId())).isEqualTo(2);
    }

    @Test
    void membershipOfAnotherClubCannotBeManagedThroughThisOne() throws Exception {
        Club other = clubRepository.save(new Club("Sports Hub", "Play", ClubCategory.SPORTS, kofi));
        ClubMembership yawInOther = membershipRepository.save(new ClubMembership(yaw, other, ClubRole.MEMBER));

        mockMvc.perform(post("/clubs/{id}/members/{mid}/remove", club.getId(), yawInOther.getId())
                        .with(user("kofi")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyLeaderCannotLeaveWhileOthersRemainUntilAnotherLeaderIsPromoted() throws Exception {
        mockMvc.perform(post("/clubs/{id}/leave", club.getId()).with(user("kofi")).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        assertThat(membershipRepository.existsByClubIdAndUserUsername(club.getId(), "kofi")).isTrue();

        mockMvc.perform(post("/clubs/{id}/members/{mid}/promote", club.getId(), amaMembership.getId())
                .with(user("kofi")).with(csrf()));
        mockMvc.perform(post("/clubs/{id}/leave", club.getId()).with(user("kofi")).with(csrf()));

        assertThat(membershipRepository.existsByClubIdAndUserUsername(club.getId(), "kofi")).isFalse();
    }

    @Test
    void manageButtonsAreOnlyRenderedForLeaders() throws Exception {
        mockMvc.perform(get("/clubs/{id}", club.getId()).with(user("kofi")))
                .andExpect(content().string(containsString("/members/" + amaMembership.getId() + "/remove")));

        mockMvc.perform(get("/clubs/{id}", club.getId()).with(user("ama")))
                .andExpect(content().string(not(containsString("/remove"))));
    }

    // ---- pinning announcements ----

    @Test
    void leaderCanPinAndUnpinAnnouncementsAndPinnedPostsComeFirst() throws Exception {
        Post older = clubPost(ama, "older announcement", PostCategory.ANNOUNCEMENT);
        clubPost(ama, "newer chatter", PostCategory.GENERAL_DISCUSSION);

        mockMvc.perform(post("/posts/{id}/pin", older.getId()).with(user("kofi")).with(csrf())
                        .param("returnTo", "/clubs/" + club.getId()))
                .andExpect(redirectedUrl("/clubs/" + club.getId() + "#post-" + older.getId()));
        assertThat(postRepository.findById(older.getId()).orElseThrow().isPinned()).isTrue();

        String page = mockMvc.perform(get("/clubs/{id}", club.getId()).with(user("kofi")))
                .andReturn().getResponse().getContentAsString();
        assertThat(page.indexOf("older announcement")).isLessThan(page.indexOf("newer chatter"));
        assertThat(page).contains("Pinned");

        mockMvc.perform(post("/posts/{id}/pin", older.getId()).with(user("kofi")).with(csrf()));
        assertThat(postRepository.findById(older.getId()).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void membersCannotPinAndOnlyAnnouncementsCanBePinned() throws Exception {
        Post announcement = clubPost(kofi, "big news", PostCategory.ANNOUNCEMENT);
        Post chatter = clubPost(kofi, "just chatting", PostCategory.GENERAL_DISCUSSION);

        mockMvc.perform(post("/posts/{id}/pin", announcement.getId()).with(user("ama")).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/posts/{id}/pin", chatter.getId()).with(user("kofi")).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        assertThat(postRepository.findById(announcement.getId()).orElseThrow().isPinned()).isFalse();
        assertThat(postRepository.findById(chatter.getId()).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void campusFeedPostsCannotBePinned() throws Exception {
        Post campusPost = postRepository.save(new Post(kofi, "campus wide", PostCategory.ANNOUNCEMENT));

        mockMvc.perform(post("/posts/{id}/pin", campusPost.getId()).with(user("kofi")).with(csrf()))
                .andExpect(flash().attributeExists("error"));

        assertThat(postRepository.findById(campusPost.getId()).orElseThrow().isPinned()).isFalse();
    }

    @Test
    void campusAdminCanPinAndManageEveryClub() throws Exception {
        Post announcement = clubPost(ama, "admin pinned", PostCategory.ANNOUNCEMENT);

        mockMvc.perform(post("/posts/{id}/pin", announcement.getId()).with(user("mod").roles("CAMPUS_ADMIN")).with(csrf()));
        assertThat(postRepository.findById(announcement.getId()).orElseThrow().isPinned()).isTrue();

        mockMvc.perform(post("/clubs/{id}/members/{mid}/promote", club.getId(), amaMembership.getId())
                .with(user("mod").roles("CAMPUS_ADMIN")).with(csrf()));
        assertThat(roleOf(ama)).isEqualTo(ClubRole.LEADER);
    }
}

