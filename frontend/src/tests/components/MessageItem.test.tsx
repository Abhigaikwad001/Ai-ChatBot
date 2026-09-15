import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MessageItem } from '../../components/chat/MessageItem'

describe('MessageItem Component', () => {
  it('should render user message correctly', () => {
    render(
      <MessageItem
        role="USER"
        content="How do I use Spring Boot?"
      />
    )

    expect(screen.getByText('How do I use Spring Boot?')).toBeInTheDocument()
  })

  it('should render assistant message with markdown content', () => {
    const markdownContent = '## Heading\n\nThis is **bold** text and a list:\n* Item 1\n* Item 2'

    render(
      <MessageItem
        role="ASSISTANT"
        content={markdownContent}
      />
    )

    expect(screen.getByRole('heading', { level: 2, name: 'Heading' })).toBeInTheDocument()
    expect(screen.getByText(/Item 1/i)).toBeInTheDocument()
    expect(screen.getByText(/Item 2/i)).toBeInTheDocument()
  })

  it('should render code block with language tag and copy button', async () => {
    const codeContent = '```java\npublic class Main {\n  public static void main(String[] args) {}\n}\n```'

    render(
      <MessageItem
        role="ASSISTANT"
        content={codeContent}
      />
    )

    expect(screen.getByText('java')).toBeInTheDocument()
    expect(screen.getByText(/public class Main/)).toBeInTheDocument()

    const copyBtn = screen.getByRole('button', { name: /copy code/i })
    expect(copyBtn).toBeInTheDocument()

    // Mock clipboard
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })

    fireEvent.click(copyBtn)
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
        expect.stringContaining('public class Main')
      )
    })
  })

  it('should display streaming indicator while message is streaming', () => {
    render(
      <MessageItem
        role="ASSISTANT"
        content="Streaming chunk..."
        isStreaming={true}
      />
    )

    expect(screen.getByRole('status', { name: /AI is generating response/i })).toBeInTheDocument()
  })

  it('should allow copying assistant response text to clipboard', async () => {
    const text = 'Here is the comprehensive answer.'

    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })

    render(
      <MessageItem
        role="ASSISTANT"
        content={text}
      />
    )

    const copyBtn = screen.getByRole('button', { name: /copy response/i })
    fireEvent.click(copyBtn)

    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith(text)
    })
  })
})
