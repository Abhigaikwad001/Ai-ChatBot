export interface MessageStartEvent {
  conversationId: string;
  role: string;
}

export interface ContentChunkEvent {
  content: string;
  sequence: number;
}

export interface HeartbeatEvent {
  timestamp: number;
}

export interface MessageCompleteEvent {
  messageId: string;
  conversationId: string;
  role: string;
  provider: string;
  model: string;
  finishReason: string;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
  totalLatencyMs?: number;
  timeToFirstChunkMs?: number;
}

export interface StreamErrorEvent {
  code: string;
  message: string;
}

export interface StreamMessageCallbacks {
  onStart?: (event: MessageStartEvent) => void;
  onChunk: (content: string, sequence: number) => void;
  onHeartbeat?: (event: HeartbeatEvent) => void;
  onComplete: (event: MessageCompleteEvent) => void;
  onError: (error: Error | StreamErrorEvent) => void;
}
