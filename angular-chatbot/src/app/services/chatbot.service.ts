import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import {
  Conversation,
  ConversationSummary,
  CreateConversationRequest,
  UpdateConversationRequest,
} from '../models/conversation.model';
import { Message, SendMessageRequest } from '../models/message.model';
import {
  ContentChunkEvent,
  HeartbeatEvent,
  MessageCompleteEvent,
  MessageStartEvent,
  StreamErrorEvent,
  StreamMessageCallbacks,
} from '../models/stream.model';

export interface RawSseEvent {
  event: string;
  data: string;
}

export function parseSseBlock(block: string): RawSseEvent | null {
  const lines = block.split(/\r?\n/);
  let eventType = 'message';
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith(':')) {
      // SSE comment, ignore
      continue;
    }
    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      const value = line.slice(5);
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value);
    }
  }

  if (dataLines.length === 0 && eventType === 'message') {
    return null;
  }

  return {
    event: eventType,
    data: dataLines.join('\n'),
  };
}

@Injectable({
  providedIn: 'root',
})
export class ChatbotService {
  private readonly http = inject(HttpClient);
  public readonly baseUrl = environment.apiBaseUrl;

  /**
   * Creates a new conversation.
   */
  createConversation(
    request?: CreateConversationRequest
  ): Observable<ApiResponse<Conversation>> {
    return this.http.post<ApiResponse<Conversation>>(
      `${this.baseUrl}/conversations`,
      request || {}
    );
  }

  /**
   * Retrieves active conversations with pagination.
   */
  getConversations(
    page: number = 0,
    size: number = 20
  ): Observable<ApiResponse<PageResponse<ConversationSummary>>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<ApiResponse<PageResponse<ConversationSummary>>>(
      `${this.baseUrl}/conversations`,
      { params }
    );
  }

  /**
   * Retrieves full conversation details including messages.
   */
  getConversation(conversationId: string): Observable<ApiResponse<Conversation>> {
    return this.http.get<ApiResponse<Conversation>>(
      `${this.baseUrl}/conversations/${conversationId}`
    );
  }

  /**
   * Renames a conversation.
   */
  renameConversation(
    conversationId: string,
    title: string
  ): Observable<ApiResponse<Conversation>> {
    const request: UpdateConversationRequest = { title };
    return this.http.patch<ApiResponse<Conversation>>(
      `${this.baseUrl}/conversations/${conversationId}`,
      request
    );
  }

  /**
   * Soft-deletes a conversation.
   */
  deleteConversation(conversationId: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(
      `${this.baseUrl}/conversations/${conversationId}`
    );
  }

  /**
   * Retrieves messages for a conversation.
   */
  getMessages(
    conversationId: string,
    page: number = 0,
    size: number = 50
  ): Observable<ApiResponse<PageResponse<Message>>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<ApiResponse<PageResponse<Message>>>(
      `${this.baseUrl}/conversations/${conversationId}/messages`,
      { params }
    );
  }

  /**
   * Sends a message synchronously (non-streaming).
   */
  sendMessage(
    conversationId: string,
    content: string
  ): Observable<ApiResponse<Message>> {
    const request: SendMessageRequest = { content };
    return this.http.post<ApiResponse<Message>>(
      `${this.baseUrl}/conversations/${conversationId}/messages`,
      request
    );
  }

  /**
   * Streams AI response using Server-Sent Events (SSE).
   */
  async streamMessage(
    conversationId: string,
    content: string,
    callbacks: StreamMessageCallbacks,
    signal?: AbortSignal
  ): Promise<void> {
    const url = `${this.baseUrl}/conversations/${conversationId}/messages/stream`;

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    };

    let response: Response;
    try {
      response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify({ content }),
        signal,
      });
    } catch (err: unknown) {
      if (
        signal?.aborted ||
        (err instanceof DOMException && err.name === 'AbortError')
      ) {
        return;
      }
      const message = err instanceof Error ? err.message : 'Network error';
      callbacks.onError(
        new Error(`Failed to connect to streaming endpoint: ${message}`)
      );
      return;
    }

    if (!response.ok) {
      let errorMsg = `Server returned status ${response.status}`;
      try {
        const errorJson = await response.json();
        if (errorJson?.message) {
          errorMsg = errorJson.message;
        }
      } catch {
        // ignore
      }
      callbacks.onError({
        code: `HTTP_${response.status}`,
        message: errorMsg,
      });
      return;
    }

    if (!response.body) {
      callbacks.onError(new Error('ReadableStream not supported by browser'));
      return;
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    try {
      while (true) {
        if (signal?.aborted) {
          await reader.cancel();
          return;
        }

        const { done, value } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });

        const parts = buffer.split(/\r?\n\r?\n/);
        buffer = parts.pop() || '';

        for (const part of parts) {
          if (!part.trim()) continue;
          this.processSseBlock(part, callbacks);
        }
      }

      if (buffer.trim()) {
        this.processSseBlock(buffer, callbacks);
      }
    } catch (err: unknown) {
      if (
        signal?.aborted ||
        (err instanceof DOMException && err.name === 'AbortError')
      ) {
        return;
      }
      const message = err instanceof Error ? err.message : 'Stream failed';
      callbacks.onError(new Error(message));
    } finally {
      reader.releaseLock();
    }
  }

  private processSseBlock(block: string, callbacks: StreamMessageCallbacks): void {
    const parsed = parseSseBlock(block);
    if (!parsed) return;

    try {
      switch (parsed.event) {
        case 'message_start': {
          const data: MessageStartEvent = JSON.parse(parsed.data);
          callbacks.onStart?.(data);
          break;
        }
        case 'content': {
          const data: ContentChunkEvent = JSON.parse(parsed.data);
          callbacks.onChunk(data.content, data.sequence);
          break;
        }
        case 'heartbeat': {
          const data: HeartbeatEvent = JSON.parse(parsed.data);
          callbacks.onHeartbeat?.(data);
          break;
        }
        case 'message_complete': {
          const data: MessageCompleteEvent = JSON.parse(parsed.data);
          callbacks.onComplete(data);
          break;
        }
        case 'error': {
          const data: StreamErrorEvent = JSON.parse(parsed.data);
          callbacks.onError(data);
          break;
        }
        default:
          break;
      }
    } catch (err) {
      // Ignored malformed event payloads
    }
  }
}
