-- ==============================================================================
-- Flyway Database Migration: V2__make_conversation_user_id_nullable.sql
-- Standalone No-Auth Chatbot: Allow conversations without an authenticated owner
-- Target: MySQL 8.x (InnoDB Engine, utf8mb4 Charset)
-- ==============================================================================

-- 1. Modify user_id column on conversations table to be nullable
ALTER TABLE conversations MODIFY COLUMN user_id VARCHAR(36) NULL;

-- 2. Add composite index for non-user conversation querying and ordering
CREATE INDEX idx_conversations_status_updated ON conversations (status, updated_at DESC);
