package org.benjamincharles.camposocial.repository;

import org.benjamincharles.camposocial.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);

    /** Newest messages first; callers reverse them for display. */
    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    /** Messages sent to {@code username} in the conversation that they have not opened yet. */
    List<Message> findByConversationIdAndSenderUsernameNotAndSeenFalse(Long conversationId, String username);

    long countByConversationIdAndSenderUsernameNotAndSeenFalse(Long conversationId, String username);

    @Query("select count(m) from Message m "
            + "where m.seen = false and m.sender.username <> :username "
            + "and (m.conversation.userOne.username = :username or m.conversation.userTwo.username = :username)")
    long countUnreadFor(@Param("username") String username);
}

