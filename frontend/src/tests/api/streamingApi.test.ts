import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  parseSseBlock,
  SseStreamParser,
  streamMessage,
} from '../../api/streamingApi'

describe('SSE Parser and Streaming API', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })

  describe('parseSseBlock', () => {
    it('should parse single line event and data', () => {
      const block = 'event: content\ndata: {"content":"hello","sequence":1}'
      const res = parseSseBlock(block)
      expect(res).toEqual({
        event: 'content',
        data: '{"content":"hello","sequence":1}',
      })
    })

    it('should join multiple data lines with newline per SSE spec', () => {
      const block = 'event: custom\ndata: line1\ndata: line2'
      const res = parseSseBlock(block)
      expect(res).toEqual({
        event: 'custom',
        data: 'line1\nline2',
      })
    })

    it('should ignore SSE comments starting with colon', () => {
      const block = ': keep-alive ping\nevent: heartbeat\ndata: {"timestamp":12345}'
      const res = parseSseBlock(block)
      expect(res).toEqual({
        event: 'heartbeat',
        data: '{"timestamp":12345}',
      })
    })

    it('should strip single leading space after data colon if present', () => {
      const block = 'data: hello world'
      const res = parseSseBlock(block)
      expect(res?.data).toBe('hello world')
    })

    it('should return null for empty blocks without data', () => {
      const block = ': comment only\n\n'
      const res = parseSseBlock(block)
      expect(res).toBeNull()
    })
  })

  describe('SseStreamParser', () => {
    it('should parse complete events split by double newlines', () => {
      const parser = new SseStreamParser()
      const raw = 'event: message_start\ndata: {"role":"assistant"}\n\nevent: content\ndata: {"content":"Hi"}\n\n'
      const events = parser.feed(raw)

      expect(events).toHaveLength(2)
      expect(events[0].event).toBe('message_start')
      expect(events[1].event).toBe('content')
    })

    it('should handle partial chunks split across network reads', () => {
      const parser = new SseStreamParser()

      // First packet delivers partial block
      const events1 = parser.feed('event: content\ndata: {"content":')
      expect(events1).toHaveLength(0)

      // Second packet delivers remaining block with delimiter
      const events2 = parser.feed('"Java"}\n\n')
      expect(events2).toHaveLength(1)
      expect(events2[0].event).toBe('content')
      expect(events2[0].data).toBe('{"content":"Java"}')
    })

    it('should support Windows CRLF (\\r\\n\\r\\n) delimiters', () => {
      const parser = new SseStreamParser()
      const raw = 'event: content\r\ndata: {"content":"Test"}\r\n\r\n'
      const events = parser.feed(raw)

      expect(events).toHaveLength(1)
      expect(events[0].event).toBe('content')
      expect(events[0].data).toBe('{"content":"Test"}')
    })

    it('should flush remaining buffer when stream closes', () => {
      const parser = new SseStreamParser()
      parser.feed('event: message_complete\ndata: {"finishReason":"stop"}')
      const flushed = parser.flush()

      expect(flushed).toHaveLength(1)
      expect(flushed[0].event).toBe('message_complete')
    })
  })

  describe('streamMessage integration', () => {
    it('should consume stream events in chronological order', async () => {
      const streamChunks = [
        'event: message_start\ndata: {"conversationId":"c1","role":"assistant"}\n\n',
        'event: content\ndata: {"content":"Hello, ","sequence":1}\n\n',
        'event: heartbeat\ndata: {"timestamp":1700000000}\n\n',
        'event: content\ndata: {"content":"world!","sequence":2}\n\n',
        'event: message_complete\ndata: {"messageId":"m1","finishReason":"stop","totalTokens":12}\n\n',
      ]

      const mockResponseStream = new ReadableStream({
        start(controller) {
          const encoder = new TextEncoder()
          for (const chunk of streamChunks) {
            controller.enqueue(encoder.encode(chunk))
          }
          controller.close()
        },
      })

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        body: mockResponseStream,
        headers: new Headers({ 'Content-Type': 'text/event-stream' }),
      })

      const onStart = vi.fn()
      const onChunk = vi.fn()
      const onHeartbeat = vi.fn()
      const onComplete = vi.fn()
      const onError = vi.fn()

      await streamMessage('convo-1', 'Hi', {
        onStart,
        onChunk,
        onHeartbeat,
        onComplete,
        onError,
      })

      expect(onStart).toHaveBeenCalledWith({ conversationId: 'c1', role: 'assistant' })
      expect(onChunk).toHaveBeenCalledTimes(2)
      expect(onChunk).toHaveBeenNthCalledWith(1, 'Hello, ', 1)
      expect(onChunk).toHaveBeenNthCalledWith(2, 'world!', 2)
      expect(onHeartbeat).toHaveBeenCalledWith({ timestamp: 1700000000 })
      expect(onComplete).toHaveBeenCalledWith(
        expect.objectContaining({ messageId: 'm1', finishReason: 'stop', totalTokens: 12 })
      )
      expect(onError).not.toHaveBeenCalled()
    })

    it('should handle backend error event', async () => {
      const streamChunks = [
        'event: message_start\ndata: {"conversationId":"c1","role":"assistant"}\n\n',
        'event: error\ndata: {"code":"AI_PROVIDER_UNAVAILABLE","message":"Ollama service offline"}\n\n',
      ]

      const mockResponseStream = new ReadableStream({
        start(controller) {
          const encoder = new TextEncoder()
          for (const chunk of streamChunks) {
            controller.enqueue(encoder.encode(chunk))
          }
          controller.close()
        },
      })

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        body: mockResponseStream,
        headers: new Headers({ 'Content-Type': 'text/event-stream' }),
      })

      const onStart = vi.fn()
      const onChunk = vi.fn()
      const onComplete = vi.fn()
      const onError = vi.fn()

      await streamMessage('convo-1', 'Hi', {
        onStart,
        onChunk,
        onComplete,
        onError,
      })

      expect(onStart).toHaveBeenCalled()
      expect(onError).toHaveBeenCalledWith({
        code: 'AI_PROVIDER_UNAVAILABLE',
        message: 'Ollama service offline',
      })
      expect(onComplete).not.toHaveBeenCalled()
    })

    it('should stop and release stream when AbortController aborts', async () => {
      const abortController = new AbortController()
      let streamCancelled = false

      const mockResponseStream = new ReadableStream({
        start(controller) {
          const encoder = new TextEncoder()
          controller.enqueue(encoder.encode('event: content\ndata: {"content":"Chunk 1","sequence":1}\n\n'))
        },
        cancel() {
          streamCancelled = true
        },
      })

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        body: mockResponseStream,
        headers: new Headers({ 'Content-Type': 'text/event-stream' }),
      })

      const onChunk = vi.fn(() => {
        // Immediately abort on first chunk
        abortController.abort()
      })
      const onError = vi.fn()

      await streamMessage(
        'convo-1',
        'Cancel test',
        {
          onChunk,
          onComplete: vi.fn(),
          onError,
        },
        abortController.signal
      )

      expect(onChunk).toHaveBeenCalledTimes(1)
      expect(streamCancelled).toBe(true)
      expect(onError).not.toHaveBeenCalled() // clean abort should not trigger onError
    })
  })
})
