import { apiClient } from './client'
import type { PageResponse } from '../types/api'
import type { MessageResponse, SendMessageRequest } from '../types/message'

export const messageApi = {
  async getMessages(
    conversationId: string,
    page = 0,
    size = 50
  ): Promise<PageResponse<MessageResponse>> {
    return apiClient<PageResponse<MessageResponse>>(
      `/conversations/${conversationId}/messages?page=${page}&size=${size}`,
      { method: 'GET' }
    )
  },

  async sendMessage(
    conversationId: string,
    request: SendMessageRequest
  ): Promise<MessageResponse> {
    return apiClient<MessageResponse>(
      `/conversations/${conversationId}/messages`,
      {
        method: 'POST',
        body: JSON.stringify(request),
      }
    )
  },
}
