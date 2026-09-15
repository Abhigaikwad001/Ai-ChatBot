export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type MessageStatus = 'SENT' | 'FAILED' | 'PENDING'

export interface MessageResponse {
  id: string
  conversationId: string
  sequenceNumber?: number
  role: MessageRole
  content: string
  status: MessageStatus
  promptTokens?: number
  completionTokens?: number
  metadata?: Record<string, unknown>
  createdAt: string
}

export interface SendMessageRequest {
  content: string
  metadata?: Record<string, unknown>
}
