import React from 'react'
import { ChatProvider } from './context/ChatContext'
import { AppLayout } from './components/layout/AppLayout'

export const App: React.FC = () => {
  return (
    <ChatProvider>
      <AppLayout />
    </ChatProvider>
  )
}

export default App
