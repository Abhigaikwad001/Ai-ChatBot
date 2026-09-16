import { getApiBaseUrl } from './client'
import type {
  ContentChunkEvent,
  HeartbeatEvent,
  MessageCompleteEvent,
  MessageStartEvent,
  StreamErrorEvent,
} from '../types/stream'

export interface RawSseEvent {
  event: string
  data: string
}

export function parseSseBlock(block: string): RawSseEvent | null {
  const lines = block.split(/\r?\n/)
  let eventType = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith(':')) {
      // SSE comment, ignore
      continue
    }
    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      const value = line.slice(5)
      dataLines.push(value.startsWith(' ') ? value.slice(1) : value)
    }
  }

  if (dataLines.length === 0 && eventType === 'message') {
    return null
  }

  return {
    event: eventType,
    data: dataLines.join('\n'),
  }
}

export class SseStreamParser {
  private buffer = ''

  public feed(chunk: string): RawSseEvent[] {
    this.buffer += chunk
    const events: RawSseEvent[] = []

    let delimiterIndex: number
    while ((delimiterIndex = this.buffer.search(/\r?\n\r?\n/)) !== -1) {
      const block = this.buffer.slice(0, delimiterIndex)
      const match = this.buffer.match(/\r?\n\r?\n/)
      const delimiterLength = match ? match[0].length : 2
      this.buffer = this.buffer.slice(delimiterIndex + delimiterLength)

      if (block.trim()) {
        const parsed = parseSseBlock(block)
        if (parsed) {
          events.push(parsed)
        }
      }
    }

    return events
  }

  public flush(): RawSseEvent[] {
    const events: RawSseEvent[] = []
    if (this.buffer.trim()) {
      const parsed = parseSseBlock(this.buffer)
      if (parsed) {
        events.push(parsed)
      }
      this.buffer = ''
    }
    return events
  }
}

export interface StreamMessageCallbacks {
  onStart?: (event: MessageStartEvent) => void
  onChunk: (content: string, sequence: number) => void
  onHeartbeat?: (event: HeartbeatEvent) => void
  onComplete: (event: MessageCompleteEvent) => void
  onError: (error: Error | StreamErrorEvent) => void
}

export async function streamMessage(
  conversationId: string,
  content: string,
  callbacks: StreamMessageCallbacks,
  signal?: AbortSignal
): Promise<void> {
  const baseUrl = getApiBaseUrl()
  const url = `${baseUrl}/conversations/${conversationId}/messages/stream`

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }

  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({ content }),
      signal,
    })
  } catch (err: unknown) {
    if (signal?.aborted || (err instanceof DOMException && err.name === 'AbortError')) {
      return
    }
    const message = err instanceof Error ? err.message : 'Network error'
    callbacks.onError(new Error(`Failed to connect to streaming endpoint: ${message}`))
    return
  }

  if (!response.ok) {
    let errorMsg = `Server returned status ${response.status}`
    try {
      const errorJson = await response.json()
      if (errorJson?.message) {
        errorMsg = errorJson.message
      }
    } catch {
      // ignore
    }
    callbacks.onError({
      code: `HTTP_${response.status}`,
      message: errorMsg,
    })
    return
  }

  if (!response.body) {
    callbacks.onError(new Error('Streaming response body is null'))
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = new SseStreamParser()

  const processEvent = (raw: RawSseEvent) => {
    switch (raw.event) {
      case 'message_start': {
        try {
          const data: MessageStartEvent = JSON.parse(raw.data)
          callbacks.onStart?.(data)
        } catch {
          callbacks.onStart?.({ conversationId, role: 'assistant' })
        }
        break
      }
      case 'content': {
        try {
          const data: ContentChunkEvent = JSON.parse(raw.data)
          callbacks.onChunk(data.content, data.sequence)
        } catch {
          callbacks.onChunk(raw.data, 0)
        }
        break
      }
      case 'heartbeat': {
        // Heartbeats are intentionally ignored visually to preserve clean UI
        try {
          const data: HeartbeatEvent = JSON.parse(raw.data)
          callbacks.onHeartbeat?.(data)
        } catch {
          callbacks.onHeartbeat?.({})
        }
        break
      }
      case 'message_complete': {
        try {
          const data: MessageCompleteEvent = JSON.parse(raw.data)
          callbacks.onComplete(data)
        } catch {
          callbacks.onError(new Error('Failed to parse message_complete event'))
        }
        break
      }
      case 'error': {
        try {
          const data: StreamErrorEvent = JSON.parse(raw.data)
          callbacks.onError(data)
        } catch {
          callbacks.onError({
            code: 'STREAM_ERROR',
            message: raw.data || 'An error occurred during response streaming',
          })
        }
        break
      }
    }
  }

  try {
    while (true) {
      if (signal?.aborted) {
        await reader.cancel()
        return
      }

      const { done, value } = await reader.read()
      if (done) {
        const remainingEvents = parser.flush()
        for (const event of remainingEvents) {
          processEvent(event)
        }
        break
      }

      const textChunk = decoder.decode(value, { stream: true })
      const events = parser.feed(textChunk)
      for (const event of events) {
        processEvent(event)
      }
    }
  } catch (err: unknown) {
    if (signal?.aborted || (err instanceof DOMException && err.name === 'AbortError')) {
      return
    }
    callbacks.onError(err instanceof Error ? err : new Error(String(err)))
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // ignore
    }
  }
}
