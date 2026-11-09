package org.benjamincharles.camposocial.repository;

import org.benjamincharles.camposocial.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

    @Query("select c from Conversation c "
            + "where c.userOne.username = :username or c.userTwo.username = :username "
            + "order by c.lastMessageAt desc")
    List<Conversation> findForUser(@Param("username") String username);
}

