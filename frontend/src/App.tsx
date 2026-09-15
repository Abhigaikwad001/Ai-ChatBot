import React from 'react'
import { AuthProvider } from './context/AuthContext'
import { ChatProvider } from './context/ChatContext'
import { AppLayout } from './components/layout/AppLayout'

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <ChatProvider>
        <AppLayout />
      </ChatProvider>
    </AuthProvider>
  )
}

export default App
