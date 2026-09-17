export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isFirst: boolean;
  isLast: boolean;
}

export class ChatbotApiError extends Error {
  public status: number;
  public details?: unknown;

  constructor(status: number, message: string, details?: unknown) {
    super(message);
    this.name = 'ChatbotApiError';
    this.status = status;
    this.details = details;
  }
}
