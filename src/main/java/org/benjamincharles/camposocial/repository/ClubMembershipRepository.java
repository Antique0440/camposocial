package org.benjamincharles.camposocial.repository;

import org.benjamincharles.camposocial.entity.ClubMembership;
import org.benjamincharles.camposocial.entity.ClubRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMembershipRepository extends JpaRepository<ClubMembership, Long> {
    long countByClubId(Long clubId);
    long countByClubIdAndRole(Long clubId, ClubRole role);
    boolean existsByClubIdAndRole(Long clubId, ClubRole role);
    boolean existsByClubIdAndUserUsername(Long clubId, String username);
    boolean existsByClubIdAndUserUsernameAndRole(Long clubId, String username, ClubRole role);
    Optional<ClubMembership> findByClubIdAndUserUsername(Long clubId, String username);
    List<ClubMembership> findByClubIdOrderByJoinedAtAsc(Long clubId);
}
