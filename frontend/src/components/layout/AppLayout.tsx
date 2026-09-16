import React, { useState } from 'react'
import { Sidebar } from './Sidebar'
import { Header } from './Header'
import { MessageList } from '../chat/MessageList'
import { MessageComposer } from '../chat/MessageComposer'
import { ErrorBanner } from '../chat/ErrorBanner'
import { useChat } from '../../context/ChatContext'

export const AppLayout: React.FC = () => {
  const {
    conversations,
    activeConversation,
    messages,
    isLoadingConversations,
    isLoadingMessages,
    streamingState,
    streamingContent,
    errorMessage,
    selectConversation,
    createNewConversation,
    renameConversation,
    deleteConversation,
    sendMessage,
    stopGeneration,
    retryLastMessage,
    clearError,
  } = useChat()

  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false)

  const handlePromptSelect = (prompt: string) => {
    sendMessage(prompt)
  }

  return (
    <div className="app-container">
      <Sidebar
        conversations={conversations}
        activeId={activeConversation?.id}
        isLoading={isLoadingConversations}
        isOpenMobile={isMobileSidebarOpen}
        onSelect={selectConversation}
        onNewChat={() => createNewConversation()}
        onRename={renameConversation}
        onDelete={deleteConversation}
        onCloseMobile={() => setIsMobileSidebarOpen(false)}
      />

      <main className="main-chat" role="main">
        <Header
          title={activeConversation?.title || 'New conversation'}
          conversationId={activeConversation?.id}
          streamingState={streamingState}
          onRename={
            activeConversation
              ? (newTitle) => renameConversation(activeConversation.id, newTitle)
              : undefined
          }
          onToggleMobileSidebar={() => setIsMobileSidebarOpen((prev) => !prev)}
        />

        {errorMessage && (
          <div style={{ padding: '0 20px' }}>
            <ErrorBanner
              message={errorMessage}
              onRetry={retryLastMessage}
              onDismiss={clearError}
            />
          </div>
        )}

        <MessageList
          messages={messages}
          streamingState={streamingState}
          streamingContent={streamingContent}
          isLoading={isLoadingMessages}
          onSelectPrompt={handlePromptSelect}
        />

        <MessageComposer
          onSend={sendMessage}
          onStop={stopGeneration}
          streamingState={streamingState}
        />
      </main>
    </div>
  )
}
