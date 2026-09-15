package com.chatbot.platform.core.repository;

import com.chatbot.platform.core.domain.entity.AiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, UUID> {

    List<AiAuditLog> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
