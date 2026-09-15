package com.chatbot.platform.core.repository;

import com.chatbot.platform.core.domain.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Efficient paginated query for messages in a conversation ordered deterministically by sequence number.
     */
    Page<Message> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId, Pageable pageable);

    /**
     * Full conversation history retrieval ordered by sequence number.
     */
    List<Message> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);

    /**
     * Bounded retrieval of most recent messages in a conversation ordered descending by sequence number.
     * Used for context retrieval to avoid loading massive histories into memory.
     */
    Page<Message> findByConversationIdOrderBySequenceNumberDesc(UUID conversationId, Pageable pageable);

    /**
     * Calculates the next consecutive sequence number for a conversation.
     */
    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) + 1 FROM Message m WHERE m.conversation.id = :conversationId")
    int getNextSequenceNumber(@Param("conversationId") UUID conversationId);

    /**
     * Total messages in conversation.
     */
    long countByConversationId(UUID conversationId);
}
