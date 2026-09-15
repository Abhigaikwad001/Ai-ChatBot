import React from 'react'

export const StreamingIndicator: React.FC = () => {
  return (
    <span className="streaming-pulse-container" aria-label="AI is generating response" role="status">
      <span className="pulse-dot" />
      <span className="pulse-dot" />
      <span className="pulse-dot" />
    </span>
  )
}
