import React, { useState } from 'react'
import { Sparkles, AlertCircle } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'

export const AuthModal: React.FC = () => {
  const { login, register, error, clearError, isLoading } = useAuth()
  const [tab, setTab] = useState<'login' | 'register'>('login')

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  const handleTabSwitch = (newTab: 'login' | 'register') => {
    setTab(newTab)
    clearError()
    setValidationError(null)
  }

  const validate = (): boolean => {
    if (!email.trim() || !email.includes('@')) {
      setValidationError('Please enter a valid email address.')
      return false
    }
    if (!password || password.length < 8) {
      setValidationError('Password must be at least 8 characters long.')
      return false
    }
    if (tab === 'register' && !fullName.trim()) {
      setValidationError('Please enter your full name.')
      return false
    }
    setValidationError(null)
    return true
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validate()) return

    try {
      if (tab === 'login') {
        await login({ email: email.trim(), password })
      } else {
        await register({
          email: email.trim(),
          password,
          fullName: fullName.trim(),
        })
      }
    } catch {
      // Error handled by AuthContext
    }
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="auth-modal-title">
      <div className="modal-card">
        <div className="modal-header">
          <div
            style={{
              display: 'inline-flex',
              padding: '10px',
              borderRadius: 'var(--radius-full)',
              background: 'var(--accent-gradient)',
              color: '#fff',
              marginBottom: '12px',
            }}
            aria-hidden="true"
          >
            <Sparkles size={24} />
          </div>
          <h2 id="auth-modal-title" className="modal-title">
            {tab === 'login' ? 'Welcome Back' : 'Create Account'}
          </h2>
          <p className="modal-subtitle">
            {tab === 'login'
              ? 'Sign in to access your conversational AI assistant'
              : 'Register to start having intelligent AI conversations'}
          </p>
        </div>

        <div className="auth-tabs" role="tablist">
          <button
            type="button"
            className={`auth-tab-btn ${tab === 'login' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('login')}
            role="tab"
            aria-selected={tab === 'login'}
          >
            Sign In
          </button>
          <button
            type="button"
            className={`auth-tab-btn ${tab === 'register' ? 'active' : ''}`}
            onClick={() => handleTabSwitch('register')}
            role="tab"
            aria-selected={tab === 'register'}
          >
            Register
          </button>
        </div>

        {(error || validationError) && (
          <div className="error-banner" style={{ marginBottom: '16px' }} role="alert">
            <div className="error-banner-content">
              <AlertCircle size={16} flex-shrink={0} />
              <span>{validationError || error}</span>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {tab === 'register' && (
            <div className="form-group">
              <label className="form-label" htmlFor="register-fullname">
                Full Name
              </label>
              <input
                id="register-fullname"
                type="text"
                className="form-input"
                placeholder="Dr. Alex Rivera"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                disabled={isLoading}
                required
              />
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor="auth-email">
              Email Address
            </label>
            <input
              id="auth-email"
              type="email"
              className="form-input"
              placeholder="alex.rivera@hospital.org"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={isLoading}
              required
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="auth-password">
              Password
            </label>
            <input
              id="auth-password"
              type="password"
              className="form-input"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
              required
            />
          </div>

          <button
            type="submit"
            className="form-submit-btn"
            disabled={isLoading}
          >
            {isLoading ? 'Processing...' : tab === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>
      </div>
    </div>
  )
}
