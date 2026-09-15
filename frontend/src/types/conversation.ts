import type { MessageResponse } from './message'

export type ConversationStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED'

export interface AiModelConfigDto {
  provider?: 'OLLAMA' | 'OPENAI' | 'ANTHROPIC' | 'CUSTOM'
  model?: string
  temperature?: number
  maxTokens?: number
  topP?: number
}

export interface ConversationResponse {
  id: string
  userId: string
  title: string
  systemPrompt?: string
  aiModelConfig?: AiModelConfigDto
  status: ConversationStatus
  metadata?: Record<string, unknown>
  messages: MessageResponse[]
  createdAt: string
  updatedAt: string
}

export interface ConversationSummaryResponse {
  id: string
  userId: string
  title: string
  aiModelConfig?: AiModelConfigDto
  status: ConversationStatus
  metadata?: Record<string, unknown>
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateConversationRequest {
  title?: string
  systemPrompt?: string
  aiModelConfig?: AiModelConfigDto
  metadata?: Record<string, unknown>
}

export interface UpdateConversationRequest {
  title?: string
  systemPrompt?: string
  aiModelConfig?: AiModelConfigDto
  metadata?: Record<string, unknown>
}
