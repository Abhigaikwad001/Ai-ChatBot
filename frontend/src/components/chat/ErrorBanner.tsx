import React from 'react'
import { AlertCircle, RefreshCw, X } from 'lucide-react'

interface ErrorBannerProps {
  message: string
  onRetry?: () => void
  onDismiss: () => void
}

export const ErrorBanner: React.FC<ErrorBannerProps> = ({ message, onRetry, onDismiss }) => {
  return (
    <div className="error-banner" role="alert">
      <div className="error-banner-content">
        <AlertCircle size={18} flex-shrink={0} />
        <span>{message}</span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        {onRetry && (
          <button type="button" onClick={onRetry} className="retry-btn">
            <RefreshCw size={13} />
            <span>Retry</span>
          </button>
        )}
        <button
          type="button"
          onClick={onDismiss}
          className="icon-action-btn"
          aria-label="Dismiss error"
        >
          <X size={16} />
        </button>
      </div>
    </div>
  )
}
