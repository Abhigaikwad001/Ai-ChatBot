package com.chatbot.platform.core.repository;

import com.chatbot.platform.core.domain.entity.Conversation;
import com.chatbot.platform.core.domain.enums.ConversationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Efficient paginated query for user's active conversations excluding soft-deleted records.
     */
    Page<Conversation> findByUserIdAndStatusNot(UUID userId, ConversationStatus status, Pageable pageable);

    /**
     * Look up a conversation by ID ensuring the conversation belongs to the specified user and is not soft-deleted.
     */
    Optional<Conversation> findByIdAndUserIdAndStatusNot(UUID id, UUID userId, ConversationStatus status);

    /**
     * Look up a conversation by ID ensuring user ownership (regardless of status).
     */
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Count non-deleted conversations owned by user.
     */
    long countByUserIdAndStatusNot(UUID userId, ConversationStatus status);

    /**
     * Efficient paginated query for all active conversations excluding soft-deleted records (standalone mode).
     */
    Page<Conversation> findByStatusNot(ConversationStatus status, Pageable pageable);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = :id AND c.user.id = :userId")
    Optional<Conversation> findByIdWithMessages(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT c FROM Conversation c LEFT JOIN FETCH c.messages WHERE c.id = :id")
    Optional<Conversation> findByIdWithMessages(@Param("id") UUID id);

    /**
     * Pessimistically locks the conversation row for concurrent message creation,
     * ensuring race-free sequential ordering and sequence number assignment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);
}
