package org.benjamincharles.camposocial.repository;

import org.benjamincharles.camposocial.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    long countByPostId(Long postId);
    boolean existsByPostIdAndUserUsername(Long postId, String username);
    Optional<Reaction> findByPostIdAndUserUsername(Long postId, String username);
}

