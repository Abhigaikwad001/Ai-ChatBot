-- ==============================================================================
-- Flyway Database Migration: V1__init_schema.sql
-- Enterprise AI Chatbot Platform: Domain & Persistence Schema
-- Target: MySQL 8.x (InnoDB Engine, utf8mb4 Charset)
-- ==============================================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_users_email (email),
    INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Conversations Table (With Soft Deletion and Embedded AI Configuration)
CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    system_prompt TEXT,
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    temperature DOUBLE NOT NULL DEFAULT 0.7,
    max_tokens INT NOT NULL DEFAULT 2048,
    top_p DOUBLE NOT NULL DEFAULT 1.0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    deleted_at DATETIME(6),
    metadata JSON,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_conversations_user_updated (user_id, updated_at DESC),
    INDEX idx_conversations_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Messages Table (Strictly Ordered via Sequence Number & Conversation FK)
CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    sequence_number INT NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    metadata JSON,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT uk_messages_conversation_sequence UNIQUE (conversation_id, sequence_number),
    INDEX idx_messages_conversation_seq (conversation_id, sequence_number ASC),
    INDEX idx_messages_conversation_created (conversation_id, created_at ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Message Attachments Table (Multimodal & File Uploads)
CREATE TABLE IF NOT EXISTS message_attachments (
    id VARCHAR(36) PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_uri VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_attachments_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
    INDEX idx_attachments_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. AI Audit Logs Table (Token Usage, Latency, and Diagnostics)
CREATE TABLE IF NOT EXISTS ai_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    message_id VARCHAR(36),
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    latency_ms BIGINT NOT NULL,
    total_tokens INT,
    finish_reason VARCHAR(50),
    error_message TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_audit_logs_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_logs_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE SET NULL,
    INDEX idx_audit_logs_conv_created (conversation_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
