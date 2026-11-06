package org.benjamincharles.camposocial.controller;

import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @PostMapping("/signup")
    public String registerUser(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            Model model
    ) {
        String trimmedUsername = username != null ? username.trim() : "";
        String trimmedEmail = email != null ? email.trim() : "";

        if (trimmedUsername.isEmpty() || trimmedEmail.isEmpty() || password == null || password.isBlank()) {
            model.addAttribute("error", "All fields are required.");
            model.addAttribute("username", trimmedUsername);
            model.addAttribute("email", trimmedEmail);
            return "signup";
        }

        if (userRepository.existsByUsername(trimmedUsername)) {
            model.addAttribute("error", "Username is already taken.");
            model.addAttribute("username", trimmedUsername);
            model.addAttribute("email", trimmedEmail);
            return "signup";
        }

        if (userRepository.existsByEmail(trimmedEmail)) {
            model.addAttribute("error", "An account with this email already exists.");
            model.addAttribute("username", trimmedUsername);
            model.addAttribute("email", trimmedEmail);
            return "signup";
        }

        User newUser = new User(
                trimmedUsername,
                trimmedEmail,
                passwordEncoder.encode(password),
                "ROLE_USER"
        );
        userRepository.save(newUser);

        return "redirect:/login?registered";
    }
}

