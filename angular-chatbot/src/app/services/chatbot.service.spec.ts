import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatbotService, parseSseBlock } from './chatbot.service';
import { environment } from '../../environments/environment';

describe('ChatbotService', () => {
  let service: ChatbotService;
  let httpMock: HttpTestingController;
  const baseUrl = environment.apiBaseUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ChatbotService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(ChatbotService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('REST API Methods', () => {
    it('should call createConversation via POST', () => {
      service.createConversation({ title: 'New Test Chat' }).subscribe((resp) => {
        expect(resp.success).toBeTrue();
        expect(resp.data.title).toBe('New Test Chat');
      });

      const req = httpMock.expectOne(`${baseUrl}/conversations`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ title: 'New Test Chat' });
      req.flush({
        success: true,
        message: 'Created',
        timestamp: '2026-09-16T12:00:00Z',
        data: { id: 'c1', title: 'New Test Chat', status: 'ACTIVE' },
      });
    });

    it('should call getConversations with pagination params via GET', () => {
      service.getConversations(1, 10).subscribe((resp) => {
        expect(resp.data.content.length).toBe(1);
      });

      const req = httpMock.expectOne(`${baseUrl}/conversations?page=1&size=10`);
      expect(req.request.method).toBe('GET');
      req.flush({
        success: true,
        message: 'Success',
        timestamp: '2026-09-16T12:00:00Z',
        data: {
          content: [{ id: 'c1', title: 'Chat 1', status: 'ACTIVE', messageCount: 2 }],
          pageNumber: 1,
          pageSize: 10,
          totalElements: 1,
          totalPages: 1,
          isFirst: false,
          isLast: true,
        },
      });
    });

    it('should call getConversation by ID via GET', () => {
      service.getConversation('c1').subscribe((resp) => {
        expect(resp.data.id).toBe('c1');
      });

      const req = httpMock.expectOne(`${baseUrl}/conversations/c1`);
      expect(req.request.method).toBe('GET');
      req.flush({
        success: true,
        message: 'Success',
        timestamp: '2026-09-16T12:00:00Z',
        data: { id: 'c1', title: 'Chat 1', status: 'ACTIVE', messages: [] },
      });
    });

    it('should call renameConversation via PATCH', () => {
      service.renameConversation('c1', 'Renamed Chat').subscribe((resp) => {
        expect(resp.data.title).toBe('Renamed Chat');
      });

      const req = httpMock.expectOne(`${baseUrl}/conversations/c1`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ title: 'Renamed Chat' });
      req.flush({
        success: true,
        message: 'Updated',
        timestamp: '2026-09-16T12:00:00Z',
        data: { id: 'c1', title: 'Renamed Chat', status: 'ACTIVE' },
      });
    });

    it('should call deleteConversation via DELETE', () => {
      service.deleteConversation('c1').subscribe((resp) => {
        expect(resp.success).toBeTrue();
      });

      const req = httpMock.expectOne(`${baseUrl}/conversations/c1`);
      expect(req.request.method).toBe('DELETE');
      req.flush({
        success: true,
        message: 'Deleted',
        timestamp: '2026-09-16T12:00:00Z',
        data: null,
      });
    });

    it('should call sendMessage via POST', () => {
      service.sendMessage('c1', 'Hello assistant').subscribe((resp) => {
        expect(resp.data.content).toBe('Hello assistant');
      });

      const req = httpMock.expectOne(`${baseUrl}/conversations/c1/messages`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ content: 'Hello assistant' });
      req.flush({
        success: true,
        message: 'Sent',
        timestamp: '2026-09-16T12:00:00Z',
        data: { id: 'm1', content: 'Hello assistant', role: 'USER', status: 'SENT' },
      });
    });
  });

  describe('parseSseBlock', () => {
    it('should parse single line event and data', () => {
      const block = 'event: content\ndata: {"content":"Hello ","sequence":1}';
      const result = parseSseBlock(block);
      expect(result).toEqual({
        event: 'content',
        data: '{"content":"Hello ","sequence":1}',
      });
    });

    it('should concatenate multiline data with newline', () => {
      const block = 'event: custom\ndata: line 1\ndata: line 2';
      const result = parseSseBlock(block);
      expect(result).toEqual({
        event: 'custom',
        data: 'line 1\nline 2',
      });
    });

    it('should ignore SSE comments starting with colon', () => {
      const block = ': ping comment\nevent: heartbeat\ndata: {"timestamp":1700000000}';
      const result = parseSseBlock(block);
      expect(result).toEqual({
        event: 'heartbeat',
        data: '{"timestamp":1700000000}',
      });
    });

    it('should return null for empty blocks without data', () => {
      const block = '   \n: only comment\n  ';
      const result = parseSseBlock(block);
      expect(result).toBeNull();
    });
  });
});
