export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';
export type MessageStatus = 'SENT' | 'STREAMING' | 'COMPLETED' | 'ERROR';

export interface Message {
  id: string;
  conversationId: string;
  sequenceNumber: number;
  role: MessageRole;
  content: string;
  status: MessageStatus;
  promptTokens?: number;
  completionTokens?: number;
  metadata?: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface SendMessageRequest {
  content: string;
  metadata?: Record<string, string>;
}
