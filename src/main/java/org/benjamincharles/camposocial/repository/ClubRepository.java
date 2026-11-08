package org.benjamincharles.camposocial.repository;

import org.benjamincharles.camposocial.entity.Club;
import org.benjamincharles.camposocial.entity.ClubCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {
    List<Club> findAllByOrderByNameAsc();
    List<Club> findByCategoryOrderByNameAsc(ClubCategory category);
    boolean existsByNameIgnoreCase(String name);
}

