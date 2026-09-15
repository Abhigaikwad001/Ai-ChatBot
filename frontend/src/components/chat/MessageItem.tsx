import React, { useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Bot, Check, Copy, User } from 'lucide-react'
import { CodeBlock } from './CodeBlock'
import { StreamingIndicator } from './StreamingIndicator'
import type { MessageRole } from '../../types/message'

interface MessageItemProps {
  id?: string
  role: MessageRole
  content: string
  isStreaming?: boolean
  promptTokens?: number
  completionTokens?: number
  createdAt?: string
}

export const MessageItem: React.FC<MessageItemProps> = ({
  role,
  content,
  isStreaming = false,
  promptTokens,
  completionTokens,
}) => {
  const [copied, setCopied] = useState(false)
  const isUser = role === 'USER'

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(content)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = content
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  return (
    <div className={`message-row ${isUser ? 'user' : 'assistant'}`}>
      {!isUser && (
        <div className="message-avatar assistant-avatar" aria-hidden="true">
          <Bot size={18} />
        </div>
      )}

      <div className="message-bubble">
        {isUser ? (
          <div style={{ whiteSpace: 'pre-wrap' }}>{content}</div>
        ) : (
          <div className="markdown-content">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                code({ className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '')
                  const strVal = String(children).replace(/\n$/, '')
                  const isBlock = match || String(children).includes('\n')

                  if (isBlock) {
                    return <CodeBlock language={match ? match[1] : undefined} value={strVal} />
                  }
                  return (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  )
                },
              }}
            >
              {content}
            </ReactMarkdown>

            {isStreaming && <StreamingIndicator />}
          </div>
        )}

        {!isUser && !isStreaming && content.trim() && (
          <div className="message-actions">
            <button
              type="button"
              onClick={handleCopy}
              className="copy-btn"
              aria-label={copied ? 'Response copied' : 'Copy response to clipboard'}
            >
              {copied ? <Check size={13} color="#10b981" /> : <Copy size={13} />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>

            {(promptTokens !== undefined || completionTokens !== undefined) && (
              <span className="message-meta">
                {completionTokens ? `${completionTokens} tokens` : ''}
              </span>
            )}
          </div>
        )}
      </div>

      {isUser && (
        <div className="message-avatar user-avatar" aria-hidden="true">
          <User size={18} />
        </div>
      )}
    </div>
  )
}
