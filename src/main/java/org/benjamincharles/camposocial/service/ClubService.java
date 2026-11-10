package org.benjamincharles.camposocial.service;

import org.benjamincharles.camposocial.entity.Club;
import org.benjamincharles.camposocial.entity.ClubCategory;
import org.benjamincharles.camposocial.entity.ClubMembership;
import org.benjamincharles.camposocial.entity.ClubRole;
import org.benjamincharles.camposocial.entity.User;
import org.benjamincharles.camposocial.repository.ClubMembershipRepository;
import org.benjamincharles.camposocial.repository.ClubRepository;
import org.benjamincharles.camposocial.repository.UserRepository;
import org.benjamincharles.camposocial.security.InputSanitizer;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class ClubService {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private final ClubRepository clubRepository;
    private final ClubMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ClubService(
            ClubRepository clubRepository,
            ClubMembershipRepository membershipRepository,
            UserRepository userRepository
    ) {
        this.clubRepository = clubRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ClubView> getDirectory(ClubCategory category, String username, boolean admin) {
        List<Club> clubs = category == null
                ? clubRepository.findAllByOrderByNameAsc()
                : clubRepository.findByCategoryOrderByNameAsc(category);
        return clubs.stream().map(club -> toView(club, username, admin)).toList();
    }

    @Transactional(readOnly = true)
    public ClubView getClub(Long clubId, String username, boolean admin) {
        return toView(findClub(clubId), username, admin);
    }

    @Transactional(readOnly = true)
    public List<ClubMembership> getMembers(Long clubId) {
        return membershipRepository.findByClubIdOrderByJoinedAtAsc(clubId);
    }

    /** Creates a club and makes its creator the first leader. */
    public Club createClub(String username, String name, String description, ClubCategory category) {
        String clubName = InputSanitizer.sanitize(name);
        String clubDescription = InputSanitizer.sanitize(description);
        if (clubName.isEmpty()) {
            throw new IllegalArgumentException("Club name is required.");
        }
        if (clubName.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Club name cannot exceed " + MAX_NAME_LENGTH + " characters.");
        }
        if (clubDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Please choose a valid category.");
        }
        if (clubRepository.existsByNameIgnoreCase(clubName)) {
            throw new IllegalArgumentException("A club with that name already exists.");
        }
        User creator = findUser(username);
        Club club = clubRepository.save(new Club(clubName, clubDescription, category, creator));
        membershipRepository.save(new ClubMembership(creator, club, ClubRole.LEADER));
        return club;
    }

    /**
     * Joins the club; joining a club you already belong to is a no-op.
     * A club without any leader (e.g. a freshly seeded one) makes its next joiner the leader.
     */
    public void join(String username, Long clubId) {
        Club club = findClub(clubId);
        if (membershipRepository.existsByClubIdAndUserUsername(clubId, username)) {
            return;
        }
        ClubRole role = membershipRepository.existsByClubIdAndRole(clubId, ClubRole.LEADER)
                ? ClubRole.MEMBER
                : ClubRole.LEADER;
        membershipRepository.save(new ClubMembership(findUser(username), club, role));
    }

    /** Leaves the club. The only leader cannot leave while other members remain. */
    public void leave(String username, Long clubId) {
        findClub(clubId);
        membershipRepository.findByClubIdAndUserUsername(clubId, username).ifPresent(membership -> {
            boolean onlyLeader = membership.getRole() == ClubRole.LEADER
                    && membershipRepository.countByClubIdAndRole(clubId, ClubRole.LEADER) == 1;
            if (onlyLeader && membershipRepository.countByClubId(clubId) > 1) {
                throw new IllegalArgumentException("Promote another leader before leaving the club.");
            }
            membershipRepository.delete(membership);
        });
    }

    public void promote(String actor, Long clubId, Long membershipId, boolean admin) {
        requireLeader(clubId, actor, admin);
        findMembership(clubId, membershipId).setRole(ClubRole.LEADER);
    }

    public void demote(String actor, Long clubId, Long membershipId, boolean admin) {
        requireLeader(clubId, actor, admin);
        ClubMembership target = findMembership(clubId, membershipId);
        if (target.getRole() != ClubRole.LEADER) {
            throw new IllegalArgumentException("That member is not a leader.");
        }
        if (target.getUser().getUsername().equals(actor)) {
            throw new IllegalArgumentException("Leaders cannot demote themselves.");
        }
        target.setRole(ClubRole.MEMBER);
    }

    public void removeMember(String actor, Long clubId, Long membershipId, boolean admin) {
        requireLeader(clubId, actor, admin);
        ClubMembership target = findMembership(clubId, membershipId);
        if (target.getUser().getUsername().equals(actor)) {
            throw new IllegalArgumentException("Use \"Leave club\" to leave the club yourself.");
        }
        if (target.getRole() == ClubRole.LEADER) {
            throw new IllegalArgumentException("Demote this leader before removing them.");
        }
        membershipRepository.delete(target);
    }

    /** Campus admins act as leaders of every club. */
    @Transactional(readOnly = true)
    public boolean isLeader(Long clubId, String username, boolean admin) {
        return admin || membershipRepository.existsByClubIdAndUserUsernameAndRole(clubId, username, ClubRole.LEADER);
    }

    @Transactional(readOnly = true)
    public void requireLeader(Long clubId, String username, boolean admin) {
        findClub(clubId);
        if (!isLeader(clubId, username, admin)) {
            throw new AccessDeniedException("Only club leaders can do that.");
        }
    }

    private ClubView toView(Club club, String username, boolean admin) {
        return new ClubView(
                club,
                membershipRepository.countByClubId(club.getId()),
                membershipRepository.existsByClubIdAndUserUsername(club.getId(), username),
                isLeader(club.getId(), username, admin)
        );
    }

    private ClubMembership findMembership(Long clubId, Long membershipId) {
        return membershipRepository.findById(membershipId)
                .filter(membership -> membership.getClub().getId().equals(clubId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private Club findClub(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
