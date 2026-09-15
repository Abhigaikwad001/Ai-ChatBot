import React, { useEffect, useRef, useState, useCallback } from 'react'
import { ArrowDown } from 'lucide-react'
import { MessageItem } from './MessageItem'
import { EmptyChatState } from './EmptyChatState'
import type { MessageResponse } from '../../types/message'
import type { StreamingState } from '../../types/stream'

interface MessageListProps {
  messages: MessageResponse[]
  streamingState: StreamingState
  streamingContent: string
  isLoading: boolean
  onSelectPrompt: (prompt: string) => void
}

export const MessageList: React.FC<MessageListProps> = ({
  messages,
  streamingState,
  streamingContent,
  isLoading,
  onSelectPrompt,
}) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const [isUserScrolledUp, setIsUserScrolledUp] = useState(false)

  const isStreamingActive = streamingState === 'STREAMING' || streamingState === 'SENDING'

  const scrollToBottom = useCallback((smooth = true) => {
    if (bottomRef.current) {
      bottomRef.current.scrollIntoView({
        behavior: smooth ? 'smooth' : 'auto',
        block: 'end',
      })
    }
  }, [])

  // Handle scroll events to detect if user manually scrolled up
  const handleScroll = () => {
    if (!containerRef.current) return
    const { scrollTop, scrollHeight, clientHeight } = containerRef.current
    const distanceFromBottom = scrollHeight - (scrollTop + clientHeight)
    // If user is more than 80px away from bottom, consider them scrolled up
    setIsUserScrolledUp(distanceFromBottom > 80)
  }

  // Auto-scroll when new messages or new streaming chunks arrive, unless user scrolled up
  useEffect(() => {
    if (!isUserScrolledUp) {
      scrollToBottom(true)
    }
  }, [messages, streamingContent, isUserScrolledUp, scrollToBottom])

  // Scroll to bottom immediately when conversation switches or loads
  useEffect(() => {
    scrollToBottom(false)
  }, [messages.length, scrollToBottom])

  if (isLoading) {
    return (
      <div className="messages-container" style={{ alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
          Loading conversation history...
        </div>
      </div>
    )
  }

  if (messages.length === 0 && !isStreamingActive) {
    return (
      <div className="messages-container">
        <EmptyChatState onSelectPrompt={onSelectPrompt} />
      </div>
    )
  }

  return (
    <div className="messages-container" ref={containerRef} onScroll={handleScroll}>
      <div className="messages-inner">
        {messages.map((msg) => (
          <MessageItem
            key={msg.id}
            id={msg.id}
            role={msg.role}
            content={msg.content}
            promptTokens={msg.promptTokens}
            completionTokens={msg.completionTokens}
            createdAt={msg.createdAt}
          />
        ))}

        {isStreamingActive && (
          <MessageItem
            role="ASSISTANT"
            content={streamingContent}
            isStreaming={true}
          />
        )}

        <div ref={bottomRef} style={{ height: 1 }} />
      </div>

      {isUserScrolledUp && (
        <button
          type="button"
          className="jump-to-bottom-btn"
          onClick={() => {
            setIsUserScrolledUp(false)
            scrollToBottom(true)
          }}
          aria-label="Scroll to bottom"
        >
          <ArrowDown size={14} />
          <span>Jump to latest</span>
        </button>
      )}
    </div>
  )
}
