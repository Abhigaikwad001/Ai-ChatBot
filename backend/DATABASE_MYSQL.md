# MySQL Database Configuration & Migration Guide

This document describes the MySQL 8.x database setup, schema migration, and configuration for the Enterprise Standalone AI Chatbot Platform.

---

## 1. Prerequisites

- **Java**: Java 21+ (`C:\Program Files\Java\jdk-21.0.11`)
- **Build Tool**: Apache Maven 3.9+
- **Database**: MySQL Server 8.0+ running on `localhost:3306`

---

## 2. Database Creation

Before starting the application for the first time, ensure MySQL is running and execute the following SQL command to create the database with UTF-8 support:

```sql
CREATE DATABASE IF NOT EXISTS chatbot_db 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;
```

> **Note**: The application's JDBC connection string includes `createDatabaseIfNotExist=true`, which will attempt to create the database automatically if the connecting user has sufficient privileges (`CREATE` permission).

---

## 3. Environment Variables & Configuration

The datasource configuration in `src/main/resources/application.yml` is externalized and respects the following environment variables:

| Variable | Description | Default |
|:---|:---|:---|
| `DATABASE_URL` (or `DB_URL`) | JDBC connection URL for MySQL | `jdbc:mysql://localhost:3306/chatbot_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DATABASE_USERNAME` (or `DB_USERNAME`) | MySQL database username | `root` |
| `DATABASE_PASSWORD` (or `DB_PASSWORD`) | MySQL database password | *(empty)* |
| `DB_POOL_MAX` | HikariCP max connection pool size | `20` |
| `DB_POOL_MIN_IDLE` | HikariCP minimum idle connections | `5` |

---

## 4. Running the Application

### Using PowerShell:

```powershell
# 1. Set Java 21
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$env:PATH = "C:\apache-maven-3.9.9\bin;C:\Program Files\Java\jdk-21.0.11\bin;" + $env:PATH

# 2. Configure MySQL Credentials (if root has a password)
$env:DATABASE_PASSWORD = "your_mysql_password"

# 3. Navigate to backend and run
cd "C:\Users\Abhi Gaikwad\New folder\inpatient-management\backend"
mvn spring-boot:run
```

### Alternatively passing credentials via Maven CLI:

```powershell
mvn spring-boot:run -Dspring-boot.run.arguments="--DATABASE_PASSWORD=your_mysql_password"
```

---

## 5. Schema & Entity Mapping Details

| Component | PostgreSQL (Previous) | MySQL 8.x (Current) | Mapping Detail |
|:---|:---|:---|:---|
| **Driver** | `org.postgresql:postgresql` | `com.mysql:mysql-connector-j` | Official MySQL Connector/J |
| **Migration** | `flyway-database-postgresql` | `flyway-mysql` | Flyway MySQL database extension |
| **UUIDs** | Native `UUID` | `VARCHAR(36)` | Managed globally via `hibernate.type.preferred_uuid_jdbc_type: VARCHAR` |
| **Timestamps** | `TIMESTAMP WITH TIME ZONE` | `DATETIME(6)` | Precision down to microseconds (6 digits) |
| **Metadata** | `JSONB` | `JSON` | Native MySQL JSON type via `@JdbcTypeCode(SqlTypes.JSON)` |
| **Storage Engine** | N/A | `InnoDB` | Transactional engine with row-level locking (`SELECT ... FOR UPDATE`) |
| **Indexes** | Standalone `CREATE INDEX` | Inline `INDEX ...` | Embedded in `CREATE TABLE` for clean MySQL 8 DDL compatibility |

---

## 6. Verification & Health Check

Verify MySQL port connectivity:
```powershell
Test-NetConnection localhost -Port 3306
```

Verify tables created by Flyway:
```sql
USE chatbot_db;
SHOW TABLES;
```
Expected tables:
- `users`
- `conversations`
- `messages`
- `message_attachments`
- `ai_audit_logs`
- `flyway_schema_history`
