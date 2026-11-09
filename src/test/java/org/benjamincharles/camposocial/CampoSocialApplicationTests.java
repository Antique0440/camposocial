package org.benjamincharles.camposocial;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CampoSocialApplicationTests {

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

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();
        clubRepository.deleteAll();
        conversationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void publicEndpointsAreAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));

        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"));

        mockMvc.perform(get("/css/style.css"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRedirectToLoginWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void signupCreatesUserAndAllowsFormLogin() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "kofi")
                        .param("email", "kofi@ashesi.edu.gh")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.existsByUsername("kofi")).isTrue();
        assertThat(userRepository.existsByEmail("kofi@ashesi.edu.gh")).isTrue();

        mockMvc.perform(formLogin("/login").user("kofi").password("secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed"));
    }

    @Test
    void duplicateUsernameOrEmailShowsErrorOnSignup() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "ama")
                        .param("email", "ama@ashesi.edu.gh")
                        .param("password", "secret123"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "ama")
                        .param("email", "other@ashesi.edu.gh")
                        .param("password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void nonUniversityEmailIsRejectedOnSignup() throws Exception {
        mockMvc.perform(post("/signup")
                        .with(csrf())
                        .param("username", "yaw")
                        .param("email", "yaw@gmail.com")
                        .param("password", "secret123"))
                .andExpect(status().isOk())
                .andExpect(view().name("signup"))
                .andExpect(model().attributeExists("error"));

        assertThat(userRepository.existsByUsername("yaw")).isFalse();
    }

    @Test
    @WithMockUser(username = "kwame")
    void authenticatedUserCanAccessFeed() throws Exception {
        mockMvc.perform(get("/feed"))
                .andExpect(status().isOk())
                .andExpect(view().name("feed"))
                .andExpect(model().attribute("username", "kwame"));
    }
}
