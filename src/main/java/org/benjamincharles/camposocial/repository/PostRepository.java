package org.benjamincharles.camposocial.repository;

import org.benjamincharles.camposocial.entity.Post;
import org.benjamincharles.camposocial.entity.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Post> findByCategoryOrderByCreatedAtDesc(PostCategory category, Pageable pageable);

    @Query(value = "select p from Post p "
            + "order by (select count(r) from Reaction r where r.post = p) desc, p.createdAt desc",
            countQuery = "select count(p) from Post p")
    Page<Post> findAllByEngagement(Pageable pageable);

    @Query(value = "select p from Post p where p.category = :category "
            + "order by (select count(r) from Reaction r where r.post = p) desc, p.createdAt desc",
            countQuery = "select count(p) from Post p where p.category = :category")
    Page<Post> findByCategoryByEngagement(@Param("category") PostCategory category, Pageable pageable);
}

