package org.benjamincharles.camposocial.config;

import org.benjamincharles.camposocial.entity.Club;
import org.benjamincharles.camposocial.entity.ClubCategory;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Seeds the initial campus clubs on an empty database. Safe to run repeatedly. */
@Component
public class DataSeeder implements ApplicationRunner {

    static final String SYSTEM_USERNAME = "campo_system";

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(ClubRepository clubRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (clubRepository.count() > 0) {
            return;
        }
        User creator = userRepository.findByUsername(SYSTEM_USERNAME).orElseGet(this::createSystemUser);

        clubRepository.saveAll(List.of(
                new Club("Tech Society",
                        "Hackathons, coding nights and talks on everything from web apps to AI.",
                        ClubCategory.TECH, creator),
                new Club("Arts & Media",
                        "Photography, film, design and live performance by campus creatives.",
                        ClubCategory.ARTS, creator),
                new Club("Sports Hub",
                        "Intramural leagues, fitness sessions and match-day meetups for every skill level.",
                        ClubCategory.SPORTS, creator)
        ));
    }

    /** Owner of seeded clubs. Its password is random and never exposed, so nobody can log in as it. */
    private User createSystemUser() {
        return userRepository.save(new User(
                SYSTEM_USERNAME,
                "system@campo.local",
                passwordEncoder.encode(UUID.randomUUID().toString()),
                "ROLE_CAMPUS_ADMIN"
        ));
    }
}

