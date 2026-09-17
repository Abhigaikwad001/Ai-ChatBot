import {
  Component,
  OnInit,
  OnDestroy,
  Output,
  EventEmitter,
  ViewChild,
  ElementRef,
  AfterViewChecked,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatbotService } from '../services/chatbot.service';
import { MarkdownPipe } from '../pipes/markdown.pipe';
import {
  Conversation,
  ConversationSummary,
} from '../models/conversation.model';
import { Message } from '../models/message.model';
import {
  MessageCompleteEvent,
  MessageStartEvent,
  StreamErrorEvent,
} from '../models/stream.model';

@Component({
  selector: 'app-chatbot-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, MarkdownPipe],
  templateUrl: './chatbot-panel.component.html',
  styleUrls: ['./chatbot-panel.component.scss'],
})
export class ChatbotPanelComponent implements OnInit, OnDestroy, AfterViewChecked {
  private readonly chatbotService = inject(ChatbotService);

  @Output() close = new EventEmitter<void>();
  @ViewChild('messagesContainer') private messagesContainer!: ElementRef<HTMLDivElement>;
  @ViewChild('messageInput') private messageInput!: ElementRef<HTMLTextAreaElement>;

  public conversations: ConversationSummary[] = [];
  public activeConversation: Conversation | null = null;
  public messages: Message[] = [];

  public isHistoryOpen = false;
  public isLoading = false;
  public isStreaming = false;
  public errorMessage: string | null = null;
  public inputContent = '';
  public copiedMessageId: string | null = null;

  public editingId: string | null = null;
  public editTitle = '';
  public historySearchQuery = '';

  private abortController: AbortController | null = null;
  private shouldScrollToBottom = false;

  public promptSuggestions: string[] = [
    'Explain how llama3.2:3b processes conversational context',
    'Draft a brief health monitoring reminder',
    'Summarize dietary guidelines for diabetic patients',
  ];

  public get filteredConversations(): ConversationSummary[] {
    const query = this.historySearchQuery.trim().toLowerCase();
    if (!query) {
      return this.conversations;
    }
    return this.conversations.filter((c) =>
      c.title.toLowerCase().includes(query)
    );
  }

  public adjustTextareaHeight(): void {
    const el = this.messageInput?.nativeElement;
    if (el) {
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 120) + 'px';
    }
  }


  ngOnInit(): void {
    this.loadConversations();
  }

  ngOnDestroy(): void {
    this.stopGeneration();
  }

  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  public toggleHistory(): void {
    this.isHistoryOpen = !this.isHistoryOpen;
  }

  public loadConversations(): void {
    this.isLoading = true;
    this.chatbotService.getConversations(0, 30).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.success && response.data) {
          this.conversations = response.data.content || [];
          if (this.conversations.length > 0 && !this.activeConversation) {
            this.selectConversation(this.conversations[0].id);
          } else if (this.conversations.length === 0) {
            this.createNewChat();
          }
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = 'Failed to load conversation history. Check backend connection.';
      },
    });
  }

  public selectConversation(conversationId: string): void {
    if (this.isStreaming) {
      this.stopGeneration();
    }
    this.isHistoryOpen = false;
    this.isLoading = true;
    this.errorMessage = null;

    this.chatbotService.getConversation(conversationId).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.success && response.data) {
          this.activeConversation = response.data;
          this.messages = response.data.messages ? [...response.data.messages] : [];
          this.shouldScrollToBottom = true;
          setTimeout(() => this.focusInput(), 100);
        }
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Could not load conversation details.';
      },
    });
  }

  public createNewChat(): void {
    if (this.isStreaming) {
      this.stopGeneration();
    }
    this.isLoading = true;
    this.isHistoryOpen = false;
    this.errorMessage = null;

    this.chatbotService.createConversation({ title: 'New Conversation' }).subscribe({
      next: (response) => {
        this.isLoading = false;
        if (response.success && response.data) {
          const newConvo = response.data;
          this.activeConversation = newConvo;
          this.messages = [];
          this.conversations.unshift({
            id: newConvo.id,
            title: newConvo.title,
            status: newConvo.status,
            messageCount: 0,
            createdAt: newConvo.createdAt,
            updatedAt: newConvo.updatedAt,
          });
          this.focusInput();
        }
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'Failed to create new conversation.';
      },
    });
  }

  public startRename(conv: ConversationSummary, event: Event): void {
    event.stopPropagation();
    this.editingId = conv.id;
    this.editTitle = conv.title;
  }

  public saveRename(conv: ConversationSummary, event: Event): void {
    event.stopPropagation();
    const trimmed = this.editTitle.trim();
    if (!trimmed || trimmed === conv.title) {
      this.editingId = null;
      return;
    }

    this.chatbotService.renameConversation(conv.id, trimmed).subscribe({
      next: (response) => {
        if (response.success) {
          conv.title = trimmed;
          if (this.activeConversation && this.activeConversation.id === conv.id) {
            this.activeConversation.title = trimmed;
          }
        }
        this.editingId = null;
      },
      error: () => {
        this.errorMessage = 'Failed to rename conversation.';
        this.editingId = null;
      },
    });
  }

  public cancelRename(event: Event): void {
    event.stopPropagation();
    this.editingId = null;
  }

  public deleteConversation(convId: string, event: Event): void {
    event.stopPropagation();
    if (!confirm('Are you sure you want to delete this conversation?')) {
      return;
    }

    this.chatbotService.deleteConversation(convId).subscribe({
      next: () => {
        this.conversations = this.conversations.filter((c) => c.id !== convId);
        if (this.activeConversation?.id === convId) {
          this.activeConversation = null;
          this.messages = [];
          if (this.conversations.length > 0) {
            this.selectConversation(this.conversations[0].id);
          } else {
            this.createNewChat();
          }
        }
      },
      error: () => {
        this.errorMessage = 'Failed to delete conversation.';
      },
    });
  }

  public applySuggestion(suggestion: string): void {
    this.inputContent = suggestion;
    this.sendMessage();
  }

  public onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  public async sendMessage(): Promise<void> {
    const text = this.inputContent.trim();
    if (!text || this.isStreaming) return;

    if (!this.activeConversation) {
      this.chatbotService.createConversation({ title: text.slice(0, 30) }).subscribe({
        next: (resp) => {
          if (resp.success && resp.data) {
            this.activeConversation = resp.data;
            this.conversations.unshift({
              id: resp.data.id,
              title: resp.data.title,
              status: resp.data.status,
              messageCount: 0,
              createdAt: resp.data.createdAt,
              updatedAt: resp.data.updatedAt,
            });
            this.executeSend(text);
          }
        },
        error: () => {
          this.errorMessage = 'Failed to initialize conversation.';
        },
      });
      return;
    }

    this.executeSend(text);
  }

  private executeSend(text: string): void {
    if (!this.activeConversation) return;
    const conversationId = this.activeConversation.id;

    // Add optimistic user message
    const userMsg: Message = {
      id: 'temp-user-' + Date.now(),
      conversationId,
      sequenceNumber: this.messages.length + 1,
      role: 'USER',
      content: text,
      status: 'SENT',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    this.messages.push(userMsg);
    this.inputContent = '';
    if (this.messageInput?.nativeElement) {
      this.messageInput.nativeElement.style.height = 'auto';
    }
    this.shouldScrollToBottom = true;
    this.errorMessage = null;

    // Placeholder streaming assistant message
    const assistantMsg: Message = {
      id: 'temp-assistant-' + Date.now(),
      conversationId,
      sequenceNumber: this.messages.length + 1,
      role: 'ASSISTANT',
      content: '',
      status: 'STREAMING',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    this.messages.push(assistantMsg);

    this.isStreaming = true;
    this.abortController = new AbortController();

    this.chatbotService
      .streamMessage(
        conversationId,
        text,
        {
          onStart: (ev: MessageStartEvent) => {
            assistantMsg.status = 'STREAMING';
          },
          onChunk: (contentChunk: string) => {
            assistantMsg.content += contentChunk;
            this.shouldScrollToBottom = true;
          },
          onComplete: (completeEv: MessageCompleteEvent) => {
            this.isStreaming = false;
            assistantMsg.id = completeEv.messageId;
            assistantMsg.status = 'COMPLETED';
            assistantMsg.completionTokens = completeEv.completionTokens;
            assistantMsg.promptTokens = completeEv.promptTokens;
            this.abortController = null;
            this.shouldScrollToBottom = true;

            // Update conversation list summary
            const summary = this.conversations.find((c) => c.id === conversationId);
            if (summary) {
              summary.messageCount = this.messages.length;
              summary.lastMessageAt = new Date().toISOString();
            }
          },
          onError: (err: Error | StreamErrorEvent) => {
            this.isStreaming = false;
            assistantMsg.status = 'ERROR';
            this.abortController = null;
            const message = 'message' in err ? err.message : 'Streaming error occurred';
            this.errorMessage = message;
            this.shouldScrollToBottom = true;
          },
        },
        this.abortController.signal
      )
      .catch((err) => {
        this.isStreaming = false;
        assistantMsg.status = 'ERROR';
        this.abortController = null;
        this.errorMessage = 'Network connection failed while streaming.';
      });
  }

  public stopGeneration(): void {
    if (this.isStreaming) {
      if (this.abortController) {
        this.abortController.abort();
        this.abortController = null;
      }
      this.isStreaming = false;

      const lastMsg = this.messages[this.messages.length - 1];
      if (lastMsg && lastMsg.role === 'ASSISTANT' && lastMsg.status === 'STREAMING') {
        lastMsg.status = 'COMPLETED';
      }
    }
  }

  public copyMessage(content: string, id: string): void {
    this.copiedMessageId = id;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(content).catch(() => {});
    }
    setTimeout(() => {
      if (this.copiedMessageId === id) {
        this.copiedMessageId = null;
      }
    }, 2000);
  }

  private scrollToBottom(): void {
    try {
      if (this.messagesContainer) {
        this.messagesContainer.nativeElement.scrollTop =
          this.messagesContainer.nativeElement.scrollHeight;
      }
    } catch {
      // ignore
    }
  }

  private focusInput(): void {
    try {
      this.messageInput?.nativeElement.focus();
    } catch {
      // ignore
    }
  }
}
