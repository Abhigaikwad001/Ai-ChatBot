import React from 'react'
import { Sparkles } from 'lucide-react'

interface EmptyChatStateProps {
  onSelectPrompt: (prompt: string) => void
}

const SUGGESTED_PROMPTS = [
  'Explain how Spring Boot handles dependency injection.',
  'Write a clean algorithm in Java to reverse a linked list.',
  'What are the best practices for designing REST APIs?',
  'How do optimistic and pessimistic locking differ in SQL databases?',
]

export const EmptyChatState: React.FC<EmptyChatStateProps> = ({ onSelectPrompt }) => {
  return (
    <div className="empty-state">
      <div className="empty-state-icon" aria-hidden="true">
        <Sparkles size={32} />
      </div>
      <h2 className="empty-state-title">Start a conversation</h2>
      <p className="empty-state-desc">
        Ask anything about programming, technology, software architecture, or general questions.
      </p>

      <div className="prompt-suggestions">
        {SUGGESTED_PROMPTS.map((prompt, idx) => (
          <button
            key={idx}
            type="button"
            className="prompt-suggestion-card"
            onClick={() => onSelectPrompt(prompt)}
          >
            {prompt}
          </button>
        ))}
      </div>
    </div>
  )
}
