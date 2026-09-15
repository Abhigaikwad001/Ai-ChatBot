import { apiClient } from './client'
import type { PageResponse } from '../types/api'
import type {
  ConversationResponse,
  ConversationSummaryResponse,
  CreateConversationRequest,
  UpdateConversationRequest,
} from '../types/conversation'

export const conversationApi = {
  async listConversations(page = 0, size = 50): Promise<PageResponse<ConversationSummaryResponse>> {
    return apiClient<PageResponse<ConversationSummaryResponse>>(
      `/conversations?page=${page}&size=${size}`,
      { method: 'GET' }
    )
  },

  async createConversation(request?: CreateConversationRequest): Promise<ConversationResponse> {
    return apiClient<ConversationResponse>('/conversations', {
      method: 'POST',
      body: JSON.stringify(request || {}),
    })
  },

  async getConversation(conversationId: string): Promise<ConversationResponse> {
    return apiClient<ConversationResponse>(`/conversations/${conversationId}`, {
      method: 'GET',
    })
  },

  async updateConversation(
    conversationId: string,
    request: UpdateConversationRequest
  ): Promise<ConversationResponse> {
    return apiClient<ConversationResponse>(`/conversations/${conversationId}`, {
      method: 'PATCH',
      body: JSON.stringify(request),
    })
  },

  async deleteConversation(conversationId: string): Promise<void> {
    await apiClient<void>(`/conversations/${conversationId}`, {
      method: 'DELETE',
    })
  },
}
