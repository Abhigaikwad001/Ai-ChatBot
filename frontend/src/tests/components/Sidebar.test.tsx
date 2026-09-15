import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Sidebar } from '../../components/layout/Sidebar'
import type { ConversationSummaryResponse } from '../../types/conversation'
import type { UserResponse } from '../../types/auth'

describe('Sidebar Component', () => {
  const mockConversations: ConversationSummaryResponse[] = [
    {
      id: 'c1',
      userId: 'u1',
      title: 'Spring Security Discussion',
      status: 'ACTIVE',
      messageCount: 4,
      createdAt: '2026-09-15T10:00:00Z',
      updatedAt: '2026-09-15T10:05:00Z',
    },
    {
      id: 'c2',
      userId: 'u1',
      title: 'Database Schema Optimization',
      status: 'ACTIVE',
      messageCount: 2,
      createdAt: '2026-09-15T09:00:00Z',
      updatedAt: '2026-09-15T09:10:00Z',
    },
  ]

  const mockUser: UserResponse = {
    id: 'u1',
    email: 'test.user@hospital.org',
    fullName: 'Dr. Jane Smith',
    role: 'ROLE_USER',
    status: 'ACTIVE',
    createdAt: '2026-09-15T08:00:00Z',
    updatedAt: '2026-09-15T08:00:00Z',
  }

  it('should render conversation list and active selection', () => {
    render(
      <Sidebar
        conversations={mockConversations}
        activeId="c1"
        isLoading={false}
        user={mockUser}
        isOpenMobile={false}
        onSelect={vi.fn()}
        onNewChat={vi.fn()}
        onRename={vi.fn()}
        onDelete={vi.fn()}
        onLogout={vi.fn()}
        onCloseMobile={vi.fn()}
      />
    )

    expect(screen.getByText('Spring Security Discussion')).toBeInTheDocument()
    expect(screen.getByText('Database Schema Optimization')).toBeInTheDocument()

    const activeItem = screen.getByText('Spring Security Discussion').closest('.conversation-item')
    expect(activeItem).toHaveClass('active')
  })

  it('should trigger onNewChat when New Chat button is clicked', () => {
    const onNewChat = vi.fn()

    render(
      <Sidebar
        conversations={mockConversations}
        activeId="c1"
        isLoading={false}
        user={mockUser}
        isOpenMobile={false}
        onSelect={vi.fn()}
        onNewChat={onNewChat}
        onRename={vi.fn()}
        onDelete={vi.fn()}
        onLogout={vi.fn()}
        onCloseMobile={vi.fn()}
      />
    )

    const newChatBtn = screen.getByRole('button', { name: /start a new chat conversation/i })
    fireEvent.click(newChatBtn)

    expect(onNewChat).toHaveBeenCalled()
  })

  it('should support inline rename flow', async () => {
    const onRename = vi.fn().mockResolvedValue(undefined)

    render(
      <Sidebar
        conversations={mockConversations}
        activeId="c1"
        isLoading={false}
        user={mockUser}
        isOpenMobile={false}
        onSelect={vi.fn()}
        onNewChat={vi.fn()}
        onRename={onRename}
        onDelete={vi.fn()}
        onLogout={vi.fn()}
        onCloseMobile={vi.fn()}
      />
    )

    const renameBtn = screen.getByRole('button', { name: /rename spring security discussion/i })
    fireEvent.click(renameBtn)

    const input = screen.getByDisplayValue('Spring Security Discussion')
    fireEvent.change(input, { target: { value: 'New Security Title' } })

    const confirmBtn = screen.getByRole('button', { name: /confirm rename/i })
    fireEvent.click(confirmBtn)

    await waitFor(() => {
      expect(onRename).toHaveBeenCalledWith('c1', 'New Security Title')
    })
  })

  it('should support delete conversation flow with confirmation', async () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)

    render(
      <Sidebar
        conversations={mockConversations}
        activeId="c1"
        isLoading={false}
        user={mockUser}
        isOpenMobile={false}
        onSelect={vi.fn()}
        onNewChat={vi.fn()}
        onRename={vi.fn()}
        onDelete={onDelete}
        onLogout={vi.fn()}
        onCloseMobile={vi.fn()}
      />
    )

    const deleteBtn = screen.getByRole('button', { name: /delete database schema optimization/i })
    fireEvent.click(deleteBtn)

    expect(screen.getByText('Delete chat?')).toBeInTheDocument()

    const confirmDeleteBtn = screen.getByRole('button', { name: /confirm delete conversation/i })
    fireEvent.click(confirmDeleteBtn)

    await waitFor(() => {
      expect(onDelete).toHaveBeenCalledWith('c2')
    })
  })

  it('should display user info and trigger logout', () => {
    const onLogout = vi.fn()

    render(
      <Sidebar
        conversations={mockConversations}
        activeId="c1"
        isLoading={false}
        user={mockUser}
        isOpenMobile={false}
        onSelect={vi.fn()}
        onNewChat={vi.fn()}
        onRename={vi.fn()}
        onDelete={vi.fn()}
        onLogout={onLogout}
        onCloseMobile={vi.fn()}
      />
    )

    expect(screen.getByText('Dr. Jane Smith')).toBeInTheDocument()
    expect(screen.getByText('test.user@hospital.org')).toBeInTheDocument()

    const logoutBtn = screen.getByRole('button', { name: /log out/i })
    fireEvent.click(logoutBtn)

    expect(onLogout).toHaveBeenCalled()
  })
})
