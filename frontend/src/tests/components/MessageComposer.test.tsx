import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MessageComposer } from '../../components/chat/MessageComposer'

describe('MessageComposer Component', () => {
  it('should render textarea and disable send when input is empty', () => {
    const onSend = vi.fn()
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="IDLE"
      />
    )

    const textarea = screen.getByRole('textbox', { name: /message input/i })
    const sendBtn = screen.getByRole('button', { name: /send message/i })

    expect(textarea).toBeInTheDocument()
    expect(sendBtn).toBeDisabled()
  })

  it('should prevent submission if input is only whitespace', () => {
    const onSend = vi.fn()
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="IDLE"
      />
    )

    const textarea = screen.getByRole('textbox', { name: /message input/i })
    const sendBtn = screen.getByRole('button', { name: /send message/i })

    fireEvent.change(textarea, { target: { value: '   \n  \t  ' } })
    expect(sendBtn).toBeDisabled()

    fireEvent.keyDown(textarea, { key: 'Enter', code: 'Enter', shiftKey: false })
    expect(onSend).not.toHaveBeenCalled()
  })

  it('should call onSend when Enter key is pressed with valid text', async () => {
    const onSend = vi.fn().mockResolvedValue(undefined)
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="IDLE"
      />
    )

    const textarea = screen.getByRole('textbox', { name: /message input/i })
    fireEvent.change(textarea, { target: { value: 'Hello Assistant' } })

    fireEvent.keyDown(textarea, { key: 'Enter', code: 'Enter', shiftKey: false })
    expect(onSend).toHaveBeenCalledWith('Hello Assistant')
  })

  it('should allow Shift+Enter without sending', () => {
    const onSend = vi.fn()
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="IDLE"
      />
    )

    const textarea = screen.getByRole('textbox', { name: /message input/i })
    fireEvent.change(textarea, { target: { value: 'Line 1' } })

    fireEvent.keyDown(textarea, { key: 'Enter', code: 'Enter', shiftKey: true })
    expect(onSend).not.toHaveBeenCalled()
  })

  it('should block submission when IME composition is active (isComposing: true)', () => {
    const onSend = vi.fn()
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="IDLE"
      />
    )

    const textarea = screen.getByRole('textbox', { name: /message input/i })
    fireEvent.change(textarea, { target: { value: 'nihongo' } })

    // Simulate IME active keydown
    const imeEvent = new KeyboardEvent('keydown', {
      key: 'Enter',
      bubbles: true,
      cancelable: true,
    })
    Object.defineProperty(imeEvent, 'isComposing', { value: true })

    textarea.dispatchEvent(imeEvent)
    expect(onSend).not.toHaveBeenCalled()
  })

  it('should display Stop generating button during streaming and trigger onStop', () => {
    const onSend = vi.fn()
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="STREAMING"
      />
    )

    const stopBtn = screen.getByRole('button', { name: /stop generation/i })
    expect(stopBtn).toBeInTheDocument()

    fireEvent.click(stopBtn)
    expect(onStop).toHaveBeenCalled()
  })

  it('should warn and disable send when input exceeds 10,000 characters limit', () => {
    const onSend = vi.fn()
    const onStop = vi.fn()

    render(
      <MessageComposer
        onSend={onSend}
        onStop={onStop}
        streamingState="IDLE"
      />
    )

    const textarea = screen.getByRole('textbox', { name: /message input/i })
    const hugeText = 'A'.repeat(10001)

    fireEvent.change(textarea, { target: { value: hugeText } })

    expect(screen.getByText(/your message is too large/i)).toBeInTheDocument()
    const sendBtn = screen.getByRole('button', { name: /send message/i })
    expect(sendBtn).toBeDisabled()
  })
})
