import React, { useState } from 'react'
import { Edit2, Check, X, Menu, Bot } from 'lucide-react'
import type { StreamingState } from '../../types/stream'

interface HeaderProps {
  title?: string
  conversationId?: string
  streamingState: StreamingState
  onRename?: (newTitle: string) => Promise<void>
  onToggleMobileSidebar: () => void
}

export const Header: React.FC<HeaderProps> = ({
  title = 'New conversation',
  conversationId,
  streamingState,
  onRename,
  onToggleMobileSidebar,
}) => {
  const [isEditing, setIsEditing] = useState(false)
  const [editedTitle, setEditedTitle] = useState(title)

  const isStreamingActive = streamingState === 'STREAMING' || streamingState === 'SENDING'

  const handleStartEdit = () => {
    setEditedTitle(title)
    setIsEditing(true)
  }

  const handleSaveEdit = async () => {
    if (editedTitle.trim() && editedTitle !== title && onRename) {
      await onRename(editedTitle.trim())
    }
    setIsEditing(false)
  }

  const handleCancelEdit = () => {
    setEditedTitle(title)
    setIsEditing(false)
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSaveEdit()
    } else if (e.key === 'Escape') {
      handleCancelEdit()
    }
  }

  return (
    <header className="chat-header">
      <div className="header-left">
        <button
          type="button"
          className="mobile-menu-btn"
          onClick={onToggleMobileSidebar}
          aria-label="Toggle navigation menu"
        >
          <Menu size={20} />
        </button>

        <div className="header-title-container">
          {isEditing ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <input
                type="text"
                className="form-input"
                style={{ padding: '4px 8px', fontSize: '0.9rem', width: '220px' }}
                value={editedTitle}
                onChange={(e) => setEditedTitle(e.target.value)}
                onKeyDown={handleKeyDown}
                autoFocus
                aria-label="Edit conversation title"
              />
              <button
                type="button"
                onClick={handleSaveEdit}
                className="icon-action-btn"
                aria-label="Save title"
              >
                <Check size={16} color="#10b981" />
              </button>
              <button
                type="button"
                onClick={handleCancelEdit}
                className="icon-action-btn"
                aria-label="Cancel editing"
              >
                <X size={16} />
              </button>
            </div>
          ) : (
            <>
              <h1 className="header-title">{title}</h1>
              {conversationId && onRename && (
                <button
                  type="button"
                  onClick={handleStartEdit}
                  className="icon-action-btn"
                  aria-label="Rename conversation"
                  title="Rename"
                >
                  <Edit2 size={14} />
                </button>
              )}
            </>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        {isStreamingActive ? (
          <span className="header-badge streaming" role="status">
            <Bot size={13} />
            <span>Streaming...</span>
          </span>
        ) : (
          <span className="header-badge">AI Assistant</span>
        )}
      </div>
    </header>
  )
}
