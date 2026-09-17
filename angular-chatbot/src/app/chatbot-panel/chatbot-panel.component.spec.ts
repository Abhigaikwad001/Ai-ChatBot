import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ChatbotPanelComponent } from './chatbot-panel.component';
import { ChatbotService } from '../services/chatbot.service';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { Conversation, ConversationSummary } from '../models/conversation.model';

describe('ChatbotPanelComponent', () => {
  let component: ChatbotPanelComponent;
  let fixture: ComponentFixture<ChatbotPanelComponent>;
  let chatbotServiceSpy: jasmine.SpyObj<ChatbotService>;

  const mockConversations: ConversationSummary[] = [
    {
      id: 'c1',
      title: 'Clinical Inquiry',
      status: 'ACTIVE',
      messageCount: 2,
      createdAt: '2026-09-15T10:00:00Z',
      updatedAt: '2026-09-15T10:05:00Z',
    },
  ];

  const mockConversationDetail: Conversation = {
    id: 'c1',
    title: 'Clinical Inquiry',
    status: 'ACTIVE',
    createdAt: '2026-09-15T10:00:00Z',
    updatedAt: '2026-09-15T10:05:00Z',
    messages: [
      {
        id: 'm1',
        conversationId: 'c1',
        sequenceNumber: 1,
        role: 'USER',
        content: 'What is normal blood pressure?',
        status: 'SENT',
        createdAt: '2026-09-15T10:00:00Z',
        updatedAt: '2026-09-15T10:00:00Z',
      },
      {
        id: 'm2',
        conversationId: 'c1',
        sequenceNumber: 2,
        role: 'ASSISTANT',
        content: 'Normal blood pressure is generally around 120/80 mmHg.',
        status: 'COMPLETED',
        completionTokens: 14,
        createdAt: '2026-09-15T10:01:00Z',
        updatedAt: '2026-09-15T10:01:00Z',
      },
    ],
  };

  beforeEach(async () => {
    chatbotServiceSpy = jasmine.createSpyObj('ChatbotService', [
      'getConversations',
      'getConversation',
      'createConversation',
      'renameConversation',
      'deleteConversation',
      'streamMessage',
    ]);

    chatbotServiceSpy.getConversations.and.returnValue(
      of({
        success: true,
        message: 'Success',
        timestamp: '2026-09-16T12:00:00Z',
        data: {
          content: mockConversations,
          pageNumber: 0,
          pageSize: 20,
          totalElements: 1,
          totalPages: 1,
          isFirst: true,
          isLast: true,
        },
      } as ApiResponse<PageResponse<ConversationSummary>>)
    );

    chatbotServiceSpy.getConversation.and.callFake(() =>
      of({
        success: true,
        message: 'Success',
        timestamp: '2026-09-16T12:00:00Z',
        data: JSON.parse(JSON.stringify(mockConversationDetail)),
      } as ApiResponse<Conversation>)
    );

    chatbotServiceSpy.createConversation.and.returnValue(
      of({
        success: true,
        message: 'Created',
        timestamp: '2026-09-16T12:00:00Z',
        data: {
          id: 'new-c',
          title: 'New Conversation',
          status: 'ACTIVE',
          createdAt: '2026-09-16T12:00:00Z',
          updatedAt: '2026-09-16T12:00:00Z',
          messages: [],
        },
      } as ApiResponse<Conversation>)
    );

    await TestBed.configureTestingModule({
      imports: [ChatbotPanelComponent],
      providers: [{ provide: ChatbotService, useValue: chatbotServiceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(ChatbotPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the panel and render header information', () => {
    expect(component).toBeTruthy();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.assistant-name')?.textContent).toContain('AI Assistant');
    expect(compiled.querySelector('.model-badge')?.textContent).toContain('llama3.2:3b');
  });

  it('should load conversations and automatically select the first active conversation', () => {
    expect(chatbotServiceSpy.getConversations).toHaveBeenCalled();
    expect(component.conversations.length).toBe(1);
    expect(component.activeConversation?.id).toBe('c1');
    expect(component.messages.length).toBe(2);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.message-row').length).toBe(2);
  });

  it('should emit close event when close button is clicked', () => {
    spyOn(component.close, 'emit');
    const compiled = fixture.nativeElement as HTMLElement;
    const closeBtn = compiled.querySelector('.close-btn') as HTMLButtonElement;
    closeBtn.click();
    expect(component.close.emit).toHaveBeenCalled();
  });

  it('should toggle conversation history drawer', () => {
    expect(component.isHistoryOpen).toBeFalse();
    component.toggleHistory();
    fixture.detectChanges();
    expect(component.isHistoryOpen).toBeTrue();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.history-drawer')).toBeTruthy();
  });

  it('should display copy confirmation when copying message text', async () => {
    spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.copyMessage('Sample message text', 'm1');
    expect(component.copiedMessageId).toBe('m1');
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('Sample message text');
  });

  it('should handle stop generation when streaming is active', () => {
    component.isStreaming = true;
    component.messages.push({
      id: 'temp-assistant-1',
      conversationId: 'c1',
      sequenceNumber: 3,
      role: 'ASSISTANT',
      content: 'In-progress text...',
      status: 'STREAMING',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    });

    component.stopGeneration();
    expect(component.isStreaming).toBeFalse();
    expect(component.messages[component.messages.length - 1].status).toBe('COMPLETED');
  });

  it('should filter conversations by search query', () => {
    component.conversations = [
      {
        id: 'c1',
        title: 'Clinical Blood Pressure Inquiry',
        status: 'ACTIVE',
        messageCount: 2,
        createdAt: '2026-09-15T10:00:00Z',
        updatedAt: '2026-09-15T10:05:00Z',
      },
      {
        id: 'c2',
        title: 'Diabetes Medication Guidelines',
        status: 'ACTIVE',
        messageCount: 5,
        createdAt: '2026-09-15T11:00:00Z',
        updatedAt: '2026-09-15T11:05:00Z',
      },
    ];

    component.historySearchQuery = 'Diabetes';
    expect(component.filteredConversations.length).toBe(1);
    expect(component.filteredConversations[0].id).toBe('c2');

    component.historySearchQuery = 'xyz-no-match';
    expect(component.filteredConversations.length).toBe(0);

    component.historySearchQuery = '';
    expect(component.filteredConversations.length).toBe(2);
  });

  it('should adjust textarea height when typing content', () => {
    expect(() => component.adjustTextareaHeight()).not.toThrow();
  });

  it('should prevent sending empty or whitespace-only messages', () => {
    chatbotServiceSpy.createConversation.calls.reset();
    component.inputContent = '   ';
    component.sendMessage();
    expect(chatbotServiceSpy.createConversation).not.toHaveBeenCalled();
  });
});

