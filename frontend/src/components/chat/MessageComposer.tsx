import React, { useState, useRef, useEffect } from 'react'
import { ArrowUp, Square } from 'lucide-react'
import type { StreamingState } from '../../types/stream'

interface MessageComposerProps {
  onSend: (content: string) => Promise<void>
  onStop: () => void
  streamingState: StreamingState
  disabled?: boolean
}

const MAX_CHAR_LIMIT = 10000

export const MessageComposer: React.FC<MessageComposerProps> = ({
  onSend,
  onStop,
  streamingState,
  disabled = false,
}) => {
  const [content, setContent] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const isStreamingActive = streamingState === 'STREAMING' || streamingState === 'SENDING'
  const isTooLong = content.length > MAX_CHAR_LIMIT
  const trimmedLength = content.trim().length
  const canSend = trimmedLength > 0 && !isTooLong && !isStreamingActive && !disabled

  // Auto-resize textarea height
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`
    }
  }, [content])

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter without shift submits, with IME composition safety
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()

      // Native IME composition check + Safari keyCode 229 fallback
      if (e.nativeEvent.isComposing || e.keyCode === 229) {
        return
      }

      if (canSend) {
        handleSubmit()
      }
    }
  }

  const handleSubmit = async () => {
    if (!canSend) return
    const textToSend = content
    setContent('')

    // Reset textarea height
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
    }

    try {
      await onSend(textToSend)
    } catch {
      // Restore input on failure so user does not lose their text
      setContent(textToSend)
    }
  }

  return (
    <div className="composer-container">
      <div className="composer-inner">
        <div className="composer-box">
          <textarea
            ref={textareaRef}
            className="composer-textarea"
            placeholder={
              isStreamingActive
                ? 'AI is generating response...'
                : 'Message AI Assistant... (Shift+Enter for newline)'
            }
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled || isStreamingActive}
            rows={1}
            aria-label="Message input"
          />

          <div className="composer-footer">
            <div className={`composer-hint ${isTooLong ? 'warning' : ''}`}>
              {isTooLong ? (
                <span>Your message is too large ({content.length}/{MAX_CHAR_LIMIT} chars). Please shorten it.</span>
              ) : content.length > 8000 ? (
                <span>{content.length}/{MAX_CHAR_LIMIT} chars</span>
              ) : (
                <span>Enter to send, Shift+Enter for newline</span>
              )}
            </div>

            {isStreamingActive ? (
              <button
                type="button"
                onClick={onStop}
                className="send-action-btn stop-btn"
                aria-label="Stop generation"
              >
                <Square size={14} fill="currentColor" />
                <span>Stop generating</span>
              </button>
            ) : (
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!canSend}
                className="send-action-btn send-btn"
                aria-label="Send message"
              >
                <ArrowUp size={16} />
                <span>Send</span>
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
