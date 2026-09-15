import React, { useState } from 'react'
import {
  Plus,
  MessageSquare,
  Trash2,
  Edit2,
  Check,
  X,
  LogOut,
  Sparkles,
  User,
} from 'lucide-react'
import type { ConversationSummaryResponse } from '../../types/conversation'
import type { UserResponse } from '../../types/auth'

interface SidebarProps {
  conversations: ConversationSummaryResponse[]
  activeId?: string
  isLoading: boolean
  user: UserResponse | null
  isOpenMobile: boolean
  onSelect: (id: string) => void
  onNewChat: () => void
  onRename: (id: string, newTitle: string) => Promise<void>
  onDelete: (id: string) => Promise<void>
  onLogout: () => void
  onCloseMobile: () => void
}

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  activeId,
  isLoading,
  user,
  isOpenMobile,
  onSelect,
  onNewChat,
  onRename,
  onDelete,
  onLogout,
  onCloseMobile,
}) => {
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [renameText, setRenameText] = useState('')
  const [deletingId, setDeletingId] = useState<string | null>(null)

  const handleStartRename = (e: React.MouseEvent, c: ConversationSummaryResponse) => {
    e.stopPropagation()
    setRenamingId(c.id)
    setRenameText(c.title || 'New conversation')
  }

  const handleSaveRename = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    if (renameText.trim()) {
      await onRename(id, renameText.trim())
    }
    setRenamingId(null)
  }

  const handleCancelRename = (e: React.MouseEvent) => {
    e.stopPropagation()
    setRenamingId(null)
  }

  const handleStartDelete = (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    setDeletingId(id)
  }

  const handleConfirmDelete = async (e: React.MouseEvent, id: string) => {
    e.stopPropagation()
    await onDelete(id)
    setDeletingId(null)
  }

  const handleCancelDelete = (e: React.MouseEvent) => {
    e.stopPropagation()
    setDeletingId(null)
  }

  return (
    <>
      {isOpenMobile && (
        <div className="sidebar-backdrop" onClick={onCloseMobile} aria-hidden="true" />
      )}

      <aside className={`sidebar ${isOpenMobile ? 'mobile-open' : ''}`}>
        <div className="sidebar-header">
          <div className="sidebar-brand">
            <Sparkles size={20} />
            <span>AI Platform</span>
          </div>
          {isOpenMobile && (
            <button
              type="button"
              className="icon-action-btn"
              onClick={onCloseMobile}
              aria-label="Close sidebar"
            >
              <X size={18} />
            </button>
          )}
        </div>

        <button
          type="button"
          className="new-chat-btn"
          onClick={() => {
            onNewChat()
            if (isOpenMobile) onCloseMobile()
          }}
          aria-label="Start a new chat conversation"
        >
          <Plus size={16} />
          <span>New Chat</span>
        </button>

        <nav className="conversation-list" aria-label="Past conversations">
          {isLoading ? (
            <div style={{ padding: '16px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              Loading chats...
            </div>
          ) : conversations.length === 0 ? (
            <div style={{ padding: '16px', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
              No conversations yet. Start a new chat above!
            </div>
          ) : (
            conversations.map((c) => {
              const isActive = c.id === activeId
              const isRenaming = renamingId === c.id
              const isConfirmingDelete = deletingId === c.id

              return (
                <div
                  key={c.id}
                  className={`conversation-item ${isActive ? 'active' : ''}`}
                  onClick={() => {
                    if (!isRenaming && !isConfirmingDelete) {
                      onSelect(c.id)
                      if (isOpenMobile) onCloseMobile()
                    }
                  }}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      onSelect(c.id)
                      if (isOpenMobile) onCloseMobile()
                    }
                  }}
                  aria-selected={isActive}
                >
                  <div className="conversation-title-wrap">
                    <MessageSquare size={16} color={isActive ? 'var(--accent-primary)' : 'var(--text-muted)'} />

                    {isRenaming ? (
                      <input
                        type="text"
                        className="form-input"
                        style={{ padding: '2px 6px', fontSize: '0.85rem' }}
                        value={renameText}
                        onChange={(e) => setRenameText(e.target.value)}
                        onClick={(e) => e.stopPropagation()}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') handleSaveRename(e as unknown as React.MouseEvent, c.id)
                          if (e.key === 'Escape') handleCancelRename(e as unknown as React.MouseEvent)
                        }}
                        autoFocus
                      />
                    ) : isConfirmingDelete ? (
                      <span style={{ color: 'var(--danger-primary)', fontSize: '0.82rem' }}>
                        Delete chat?
                      </span>
                    ) : (
                      <span className="conversation-title">{c.title || 'New conversation'}</span>
                    )}
                  </div>

                  <div className="conversation-actions">
                    {isRenaming ? (
                      <>
                        <button
                          type="button"
                          className="icon-action-btn"
                          onClick={(e) => handleSaveRename(e, c.id)}
                          aria-label="Confirm rename"
                        >
                          <Check size={14} color="#10b981" />
                        </button>
                        <button
                          type="button"
                          className="icon-action-btn"
                          onClick={handleCancelRename}
                          aria-label="Cancel rename"
                        >
                          <X size={14} />
                        </button>
                      </>
                    ) : isConfirmingDelete ? (
                      <>
                        <button
                          type="button"
                          className="icon-action-btn"
                          onClick={(e) => handleConfirmDelete(e, c.id)}
                          aria-label="Confirm delete conversation"
                          title="Confirm Delete"
                        >
                          <Check size={14} color="#ef4444" />
                        </button>
                        <button
                          type="button"
                          className="icon-action-btn"
                          onClick={handleCancelDelete}
                          aria-label="Cancel delete"
                        >
                          <X size={14} />
                        </button>
                      </>
                    ) : (
                      <>
                        <button
                          type="button"
                          className="icon-action-btn"
                          onClick={(e) => handleStartRename(e, c)}
                          aria-label={`Rename ${c.title || 'conversation'}`}
                        >
                          <Edit2 size={13} />
                        </button>
                        <button
                          type="button"
                          className="icon-action-btn delete-btn"
                          onClick={(e) => handleStartDelete(e, c.id)}
                          aria-label={`Delete ${c.title || 'conversation'}`}
                        >
                          <Trash2 size={13} />
                        </button>
                      </>
                    )}
                  </div>
                </div>
              )
            })
          )}
        </nav>

        <div className="sidebar-footer">
          <div className="user-profile">
            <div className="user-avatar" aria-hidden="true">
              {user?.fullName ? user.fullName.charAt(0).toUpperCase() : <User size={16} />}
            </div>
            <div className="user-info">
              <div className="user-name">{user?.fullName || 'User'}</div>
              <div className="user-email">{user?.email || ''}</div>
            </div>
          </div>

          <button
            type="button"
            className="icon-action-btn"
            onClick={onLogout}
            aria-label="Log out"
            title="Log out"
          >
            <LogOut size={16} />
          </button>
        </div>
      </aside>
    </>
  )
}
