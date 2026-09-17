import { Message } from './message.model';

export type ConversationStatus = 'ACTIVE' | 'ARCHIVED' | 'DELETED';

export interface Conversation {
  id: string;
  title: string;
  status: ConversationStatus;
  createdAt: string;
  updatedAt: string;
  messages?: Message[];
  metadata?: Record<string, string>;
}

export interface ConversationSummary {
  id: string;
  title: string;
  status: ConversationStatus;
  messageCount: number;
  lastMessageAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConversationRequest {
  title?: string;
}

export interface UpdateConversationRequest {
  title: string;
}
