export type StreamingState = 'IDLE' | 'SENDING' | 'STREAMING' | 'ERROR' | 'CANCELLED'

export interface MessageStartEvent {
  conversationId: string
  role: string
}

export interface ContentChunkEvent {
  content: string
  sequence: number
}

export interface HeartbeatEvent {
  timestamp?: number
}

export interface MessageCompleteEvent {
  messageId: string
  conversationId: string
  role: string
  provider: string
  model: string
  finishReason: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  latencyMs?: number
  timeToFirstChunkMs?: number
}

export interface StreamErrorEvent {
  code: string
  message: string
}
