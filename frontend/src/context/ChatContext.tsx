import React, {
  createContext,
  useContext,
  useState,
  useCallback,
  useRef,
  useEffect,
} from 'react'
import { conversationApi } from '../api/conversationApi'
import { messageApi } from '../api/messageApi'
import { streamMessage } from '../api/streamingApi'
import type {
  ConversationResponse,
  ConversationSummaryResponse,
} from '../types/conversation'
import type { MessageResponse } from '../types/message'
import type { StreamingState } from '../types/stream'

interface ChatContextType {
  conversations: ConversationSummaryResponse[]
  activeConversation: ConversationResponse | null
  messages: MessageResponse[]
  isLoadingConversations: boolean
  isLoadingMessages: boolean
  streamingState: StreamingState
  streamingContent: string
  errorMessage: string | null
  loadConversations: () => Promise<void>
  selectConversation: (id: string) => Promise<void>
  createNewConversation: (title?: string) => Promise<ConversationResponse | null>
  renameConversation: (id: string, newTitle: string) => Promise<void>
  deleteConversation: (id: string) => Promise<void>
  sendMessage: (content: string) => Promise<void>
  stopGeneration: () => void
  retryLastMessage: () => Promise<void>
  clearError: () => void
}

const ChatContext = createContext<ChatContextType | undefined>(undefined)

export const ChatProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [conversations, setConversations] = useState<ConversationSummaryResponse[]>([])
  const [activeConversation, setActiveConversation] = useState<ConversationResponse | null>(null)
  const [messages, setMessages] = useState<MessageResponse[]>([])
  const [isLoadingConversations, setIsLoadingConversations] = useState<boolean>(false)
  const [isLoadingMessages, setIsLoadingMessages] = useState<boolean>(false)
  const [streamingState, setStreamingState] = useState<StreamingState>('IDLE')
  const [streamingContent, setStreamingContent] = useState<string>('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const abortControllerRef = useRef<AbortController | null>(null)
  const streamingContentRef = useRef<string>('')
  const lastUserPromptRef = useRef<string>('')

  const clearError = () => setErrorMessage(null)

  // Load conversation list
  const loadConversations = useCallback(async () => {
    try {
      setIsLoadingConversations(true)
      const res = await conversationApi.listConversations(0, 50)
      setConversations(res.content || [])
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load conversations'
      setErrorMessage(msg)
    } finally {
      setIsLoadingConversations(false)
    }
  }, [])

  useEffect(() => {
    loadConversations()
  }, [loadConversations])

  // Select conversation and load its messages
  const selectConversation = useCallback(
    async (id: string) => {
      if (streamingState === 'STREAMING' || streamingState === 'SENDING') {
        // Stop current stream before switching
        if (abortControllerRef.current) {
          abortControllerRef.current.abort()
        }
        setStreamingState('IDLE')
        setStreamingContent('')
      }

      setErrorMessage(null)
      try {
        setIsLoadingMessages(true)
        const convo = await conversationApi.getConversation(id)
        setActiveConversation(convo)

        const msgs = await messageApi.getMessages(id, 0, 100)
        setMessages(msgs.content || [])
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to load conversation'
        setErrorMessage(msg)
      } finally {
        setIsLoadingMessages(false)
      }
    },
    [streamingState]
  )

  // Create new conversation
  const createNewConversation = useCallback(
    async (title?: string): Promise<ConversationResponse | null> => {
      setErrorMessage(null)
      try {
        const convo = await conversationApi.createConversation({ title })
        setConversations(prev => [
          {
            id: convo.id,
            userId: convo.userId,
            title: convo.title,
            aiModelConfig: convo.aiModelConfig,
            status: convo.status,
            metadata: convo.metadata,
            messageCount: 0,
            createdAt: convo.createdAt,
            updatedAt: convo.updatedAt,
          },
          ...prev,
        ])
        setActiveConversation(convo)
        setMessages([])
        return convo
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to create conversation'
        setErrorMessage(msg)
        return null
      }
    },
    []
  )

  // Rename conversation
  const renameConversation = useCallback(
    async (id: string, newTitle: string) => {
      if (!newTitle.trim()) return
      setErrorMessage(null)
      try {
        const updated = await conversationApi.updateConversation(id, { title: newTitle.trim() })
        setConversations(prev =>
          prev.map(c => (c.id === id ? { ...c, title: updated.title, updatedAt: updated.updatedAt } : c))
        )
        if (activeConversation?.id === id) {
          setActiveConversation(prev => (prev ? { ...prev, title: updated.title } : null))
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to rename conversation'
        setErrorMessage(msg)
      }
    },
    [activeConversation]
  )

  // Delete conversation
  const deleteConversation = useCallback(
    async (id: string) => {
      if (activeConversation?.id === id && (streamingState === 'STREAMING' || streamingState === 'SENDING')) {
        if (abortControllerRef.current) {
          abortControllerRef.current.abort()
        }
        setStreamingState('IDLE')
        setStreamingContent('')
      }

      setErrorMessage(null)
      try {
        await conversationApi.deleteConversation(id)
        setConversations(prev => prev.filter(c => c.id !== id))
        if (activeConversation?.id === id) {
          setActiveConversation(null)
          setMessages([])
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Failed to delete conversation'
        setErrorMessage(msg)
      }
    },
    [activeConversation, streamingState]
  )

  // Stop active generation
  const stopGeneration = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort()
      abortControllerRef.current = null
    }
    setStreamingState('CANCELLED')

    // Refetch messages from backend to sync any partial/failed message state accurately
    if (activeConversation?.id) {
      messageApi.getMessages(activeConversation.id, 0, 100).then(msgs => {
        setMessages(msgs.content || [])
        setStreamingContent('')
        setStreamingState('IDLE')
      }).catch(() => {
        setStreamingContent('')
        setStreamingState('IDLE')
      })
    } else {
      setStreamingContent('')
      setStreamingState('IDLE')
    }
  }, [activeConversation])

  // Send message with SSE streaming
  const sendMessage = useCallback(
    async (rawContent: string) => {
      const trimmedContent = rawContent.trim()
      if (!trimmedContent) return

      if (streamingState === 'STREAMING' || streamingState === 'SENDING') {
        return
      }

      setErrorMessage(null)
      lastUserPromptRef.current = trimmedContent

      // Auto-create conversation if none is active
      let targetConvo = activeConversation
      if (!targetConvo) {
        targetConvo = await createNewConversation()
        if (!targetConvo) return
      }

      const conversationId = targetConvo.id

      // Optimistically add user message to list
      const tempUserMessage: MessageResponse = {
        id: `temp-${Date.now()}`,
        conversationId,
        role: 'USER',
        content: trimmedContent,
        status: 'SENT',
        createdAt: new Date().toISOString(),
      }
      setMessages(prev => [...prev, tempUserMessage])

      // Initialize streaming state
      setStreamingState('SENDING')
      setStreamingContent('')
      streamingContentRef.current = ''

      const controller = new AbortController()
      abortControllerRef.current = controller

      try {
        await streamMessage(
          conversationId,
          trimmedContent,
          {
            onStart: () => {
              setStreamingState('STREAMING')
            },
            onChunk: (chunk: string) => {
              setStreamingState('STREAMING')
              streamingContentRef.current += chunk
              setStreamingContent(prev => prev + chunk)
            },
            onHeartbeat: () => {
              // Heartbeats are purely connection keep-alives; ignored visually
            },
            onComplete: (completeEvent) => {
              const finalAssistantMessage: MessageResponse = {
                id: completeEvent.messageId,
                conversationId,
                role: 'ASSISTANT',
                content: streamingContentRef.current,
                status: 'SENT',
                promptTokens: completeEvent.promptTokens,
                completionTokens: completeEvent.completionTokens,
                createdAt: new Date().toISOString(),
              }

              setMessages(prev => [...prev, finalAssistantMessage])
              setStreamingContent('')
              streamingContentRef.current = ''
              setStreamingState('IDLE')
              abortControllerRef.current = null

              // Update conversation summary recency & message count
              setConversations(prev =>
                prev.map(c =>
                  c.id === conversationId
                    ? { ...c, messageCount: c.messageCount + 2, updatedAt: new Date().toISOString() }
                    : c
                )
              )
            },
            onError: (err) => {
              const errorText =
                'message' in err ? err.message : 'An error occurred during response generation'
              setErrorMessage(errorText)
              setStreamingState('ERROR')
              abortControllerRef.current = null

              // Sync backend message state to show accurate status
              messageApi.getMessages(conversationId, 0, 100).then(msgs => {
                setMessages(msgs.content || [])
              }).catch(() => {
                // Keep existing messages if network fails
              })
            },
          },
          controller.signal
        )
      } catch (err: unknown) {
        if (controller.signal.aborted) {
          setStreamingState('CANCELLED')
        } else {
          const msg = err instanceof Error ? err.message : 'Failed to send message'
          setErrorMessage(msg)
          setStreamingState('ERROR')
        }
        abortControllerRef.current = null
      }
    },
    [activeConversation, createNewConversation, streamingState]
  )

  // Retry generation for last user message safely without duplicating user message
  const retryLastMessage = useCallback(async () => {
    if (!activeConversation || !messages.length) return
    const lastUserMessage = [...messages].reverse().find(m => m.role === 'USER')
    if (!lastUserMessage) return

    setErrorMessage(null)
    setStreamingState('SENDING')
    setStreamingContent('')
    streamingContentRef.current = ''

    const controller = new AbortController()
    abortControllerRef.current = controller

    try {
      await streamMessage(
        activeConversation.id,
        lastUserMessage.content,
        {
          onStart: () => {
            setStreamingState('STREAMING')
          },
          onChunk: (chunk: string) => {
            setStreamingState('STREAMING')
            streamingContentRef.current += chunk
            setStreamingContent(prev => prev + chunk)
          },
          onComplete: (completeEvent) => {
            const finalAssistantMessage: MessageResponse = {
              id: completeEvent.messageId,
              conversationId: activeConversation.id,
              role: 'ASSISTANT',
              content: streamingContentRef.current,
              status: 'SENT',
              promptTokens: completeEvent.promptTokens,
              completionTokens: completeEvent.completionTokens,
              createdAt: new Date().toISOString(),
            }

            setMessages(prev => [...prev, finalAssistantMessage])
            setStreamingContent('')
            streamingContentRef.current = ''
            setStreamingState('IDLE')
            abortControllerRef.current = null
          },
          onError: (err) => {
            const errorText =
              'message' in err ? err.message : 'An error occurred during response generation'
            setErrorMessage(errorText)
            setStreamingState('ERROR')
            abortControllerRef.current = null
          },
        },
        controller.signal
      )
    } catch (err: unknown) {
      if (controller.signal.aborted) {
        setStreamingState('CANCELLED')
      } else {
        const msg = err instanceof Error ? err.message : 'Retry failed'
        setErrorMessage(msg)
        setStreamingState('ERROR')
      }
      abortControllerRef.current = null
    }
  }, [activeConversation, messages])

  return (
    <ChatContext.Provider
      value={{
        conversations,
        activeConversation,
        messages,
        isLoadingConversations,
        isLoadingMessages,
        streamingState,
        streamingContent,
        errorMessage,
        loadConversations,
        selectConversation,
        createNewConversation,
        renameConversation,
        deleteConversation,
        sendMessage,
        stopGeneration,
        retryLastMessage,
        clearError,
      }}
    >
      {children}
    </ChatContext.Provider>
  )
}

export const useChat = (): ChatContextType => {
  const context = useContext(ChatContext)
  if (!context) {
    throw new Error('useChat must be used within a ChatProvider')
  }
  return context
}
