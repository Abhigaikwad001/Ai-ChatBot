# Enterprise AI Chatbot Platform

A production-grade, standalone conversational AI Chatbot platform built with **Java 21 LTS**, **Spring Boot 3.4**, **React 19 / Vite**, **MySQL 8.x**, and **Ollama** (`llama3.2:3b`).

---

## Architecture Overview

```text
React 19 + TypeScript + Vite (Port 5173)
   │
   │  HTTPS / REST / SSE Streaming
   ▼
Spring Boot 3.4 + Java 21 Virtual Threads (Port 8080)
   ├── Spring Security 6 (Stateless JWT Authentication & RBAC)
   ├── ChatService (Split-Transaction Architecture)
   │     ├── Transaction 1: Persist User Message (Pessimistic Lock & Strict Sequencing)
   │     ├── Context Assembly: Bounded Sliding Window + Token Budget Management
   │     └── Transaction 2: Persist Assistant Message + AiAuditLog
   ├── AiProvider Abstraction Boundary
   │     └── OllamaAiProvider (NDJSON Streaming over RestClient)
   └── MySQL 8.x (InnoDB, utf8mb4, VARCHAR(36) UUID, Native JSON)
```

---

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.4.3, Spring Security 6, Spring Data JPA / Hibernate, Flyway
- **Database**: MySQL 8.x (InnoDB)
- **AI Engine**: Ollama (`llama3.2:3b`) via HTTP/NDJSON streaming
- **Frontend**: React 19, TypeScript, Vite, TailwindCSS, Lucide Icons, Markdown + Syntax Highlighting
- **Streaming**: Server-Sent Events (SSE) executed on JDK 21 Virtual Threads
- **Security**: Stateless JWT, BCrypt password hashing, CORS, rate limiting, audit logging

---

## Prerequisites

- **Java Development Kit (JDK)**: 21+ (`C:\Program Files\Java\jdk-21.0.11`)
- **Apache Maven**: 3.9+
- **Node.js**: 18+ & npm
- **MySQL Server**: 8.0+ running on `localhost:3306`
- **Ollama**: running locally on `http://localhost:11434` with model `llama3.2:3b`
  ```bash
  ollama run llama3.2:3b
  ```

---

## Environment Configuration

Copy the example environment configuration in `backend/.env.example` to `backend/.env` or export environment variables:

```bash
DATABASE_URL=jdbc:mysql://localhost:3306/chatbot_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DATABASE_USERNAME=root
DATABASE_PASSWORD=your_mysql_password
JWT_SECRET=your_minimum_256_bit_hex_encoded_secret_key
AI_DEFAULT_PROVIDER=OLLAMA
AI_DEFAULT_MODEL=llama3.2:3b
AI_OLLAMA_BASE_URL=http://localhost:11434
```

---

## Getting Started

### 1. Database Setup
Ensure MySQL is running and create the database:
```sql
CREATE DATABASE IF NOT EXISTS chatbot_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
*(Flyway will automatically execute migrations upon application startup).*

### 2. Run the Backend
```bash
cd backend
mvn spring-boot:run
```
The backend starts on `http://localhost:8080`.

### 3. Run the Frontend
```bash
cd frontend
npm install
npm run dev
```
The frontend starts on `http://localhost:5173` with Vite proxy forwarding `/api` requests to port 8080.

---

## Running Tests

### Backend Tests
```bash
cd backend
mvn clean test
```

### Frontend Tests & Production Build
```bash
cd frontend
npm test -- --run
npm run build
```

---

## License
Proprietary / Internal Hospital Management & Enterprise AI Platform.
