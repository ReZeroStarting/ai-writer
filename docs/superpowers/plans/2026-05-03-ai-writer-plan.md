# AI Writer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a multi-user AI writing SaaS with style learning — users upload texts to train custom AI writing personas, then write with AI assistance (outline, continue, polish, expand/shorten).

**Architecture:** Monorepo with Vue 3 frontend (Vite + Element Plus + Tiptap) and Spring Boot 3 backend (Spring AI + Spring Security + JPA). Frontend communicates via REST/SSE. AI style learning uses System Prompt injection via Claude.

**Tech Stack:** Vue 3, Vite, Element Plus, Tiptap, Pinia, TypeScript, Spring Boot 3, Spring AI, Spring Security, Spring Data JPA, MySQL, Anthropic Claude

---

## File Map

```
ai-writer/
├── frontend/
│   ├── src/
│   │   ├── api/           # Axios client & API wrappers
│   │   ├── stores/        # Pinia stores
│   │   ├── router/        # Vue Router config
│   │   ├── components/    # Reusable components
│   │   │   ├── auth/      # Login/Register forms
│   │   │   ├── layout/    # AppHeader, ProjectSidebar
│   │   │   ├── editor/    # Tiptap editor + AI panel
│   │   │   ├── ai/        # AI tool subcomponents
│   │   │   └── styles/    # Style management components
│   │   ├── views/         # Page-level route components
│   │   └── types/         # TypeScript interfaces
│   ├── index.html
│   ├── vite.config.ts
│   ├── tsconfig.json
│   └── package.json
└── backend/
    ├── src/main/java/com/aiwriter/
    │   ├── config/        # Security, JWT, AI beans
    │   ├── controller/    # REST controllers
    │   ├── service/       # Business logic
    │   ├── model/         # JPA entities
    │   ├── repository/    # Spring Data repos
    │   ├── dto/           # Request/Response DTOs
    │   ├── security/      # JWT filter, token provider
    │   ├── exception/     # Global handler + exceptions
    │   └── ai/            # Claude integration
    ├── src/main/resources/
    │   ├── application.yml
    │   └── schema.sql
    └── pom.xml
```

---

## Phase 1: Project Scaffolding

### Task 1.1: Initialize backend Spring Boot project

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/aiwriter/AiWriterApplication.java`
- Create: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Generate Spring Boot project with Maven**

Use Spring Initializr (or write pom.xml directly) with dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-ai-anthropic-spring-boot-starter, mysql-connector-j, lombok, spring-boot-starter-test.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>com.aiwriter</groupId>
    <artifactId>ai-writer</artifactId>
    <version>0.1.0</version>
    <name>AI Writer</name>
    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.1.4</spring-ai.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-anthropic-spring-boot-starter</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.6</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.6</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Write main application class**

```java
package com.aiwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiWriterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiWriterApplication.class, args);
    }
}
```

- [ ] **Step 3: Write application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-writer
  datasource:
    url: jdbc:mysql://localhost:3306/ai_writer?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8mb4
    username: root
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-sonnet-4-6
          temperature: 0.7
          max-tokens: 4096

jwt:
  secret: ${JWT_SECRET:change-me-in-production-use-256-bit-key}
  access-token-expiration: 1800000
  refresh-token-expiration: 604800000
```

- [ ] **Step 4: Verify build**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/
git commit -m "feat: scaffold Spring Boot backend project"
```

---

### Task 1.2: Initialize frontend Vue 3 project

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/types/index.ts`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "ai-writer-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc -b && vite build",
    "preview": "vite preview",
    "test": "vitest",
    "test:run": "vitest run"
  },
  "dependencies": {
    "vue": "^3.5",
    "vue-router": "^4.5",
    "pinia": "^2.3",
    "element-plus": "^2.9",
    "@element-plus/icons-vue": "^2.3",
    "@tiptap/vue-3": "^2.11",
    "@tiptap/starter-kit": "^2.11",
    "@tiptap/extension-placeholder": "^2.11",
    "@tiptap/extension-underline": "^2.11",
    "axios": "^1.7",
    "@vueuse/core": "^12.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2",
    "typescript": "~5.7",
    "vite": "^6.2",
    "vue-tsc": "^2.2",
    "vitest": "^3.0",
    "@vue/test-utils": "^2.4",
    "jsdom": "^25.0",
    "msw": "^2.7",
    "@playwright/test": "^1.50"
  }
}
```

- [ ] **Step 2: Create vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

- [ ] **Step 3: Create index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>AI Writer</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

- [ ] **Step 4: Create src/main.ts**

```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
import zhCn from 'element-plus/es/locale/lang/zh-cn'
app.mount('#app')
```

- [ ] **Step 5: Create src/App.vue**

```vue
<template>
  <router-view />
</template>
```

- [ ] **Step 6: Create src/types/index.ts**

```typescript
export interface User {
  id: number
  username: string
  email: string
  phone?: string
  createdAt: string
}

export interface Project {
  id: number
  name: string
  description?: string
  docCount?: number
  createdAt: string
  updatedAt: string
}

export interface Document {
  id: number
  projectId: number
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface StyleTemplate {
  id: number
  name: string
  description?: string
  systemPrompt: string
  createdAt: string
}

export interface LearningJob {
  id: number
  templateId: number
  materialId: number
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  resultPrompt?: string
  errorMsg?: string
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: User
}

export interface AiRequest {
  content: string
  styleTemplateId?: number
  instruction?: string
}

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  phone?: string
  password: string
}
```

- [ ] **Step 7: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForExpose": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"]
}
```

- [ ] **Step 8: Install dependencies and verify**

Run: `cd frontend && npm install && npx vite build --emptyOutDir`
Expected: Build succeeds with no errors.

- [ ] **Step 9: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold Vue 3 frontend project"
```

---

## Phase 2: Database Schema & JPA Entities

### Task 2.1: Create database schema

**Files:**
- Create: `backend/src/main/resources/schema.sql`

- [ ] **Step 1: Write schema.sql**

```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    oauth_provider VARCHAR(20),
    oauth_open_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS style_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    system_prompt TEXT,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS source_materials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (template_id) REFERENCES style_templates(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS learning_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_prompt TEXT,
    error_msg VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (template_id) REFERENCES style_templates(id) ON DELETE CASCADE,
    FOREIGN KEY (material_id) REFERENCES source_materials(id) ON DELETE CASCADE
);

CREATE INDEX idx_projects_user ON projects(user_id);
CREATE INDEX idx_documents_project ON documents(project_id);
CREATE INDEX idx_documents_user ON documents(user_id);
CREATE INDEX idx_style_templates_user ON style_templates(user_id);
CREATE INDEX idx_learning_jobs_user ON learning_jobs(user_id);
CREATE INDEX idx_learning_jobs_template ON learning_jobs(template_id);
```

- [ ] **Step 2: Verify the backend starts against MySQL**

Run: `cd backend && mvn spring-boot:run`
Expected: Application starts, schema tables created. Then Ctrl+C to stop.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/schema.sql
git commit -m "feat: add database schema for all entities"
```

---

### Task 2.2: Create JPA entities

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/User.java`
- Create: `backend/src/main/java/com/aiwriter/model/Project.java`
- Create: `backend/src/main/java/com/aiwriter/model/Document.java`
- Create: `backend/src/main/java/com/aiwriter/model/StyleTemplate.java`
- Create: `backend/src/main/java/com/aiwriter/model/SourceMaterial.java`
- Create: `backend/src/main/java/com/aiwriter/model/LearningJob.java`

- [ ] **Step 1: Write User entity**

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "oauth_provider", length = 20)
    private String oauthProvider;

    @Column(name = "oauth_open_id", length = 100)
    private String oauthOpenId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Write Project entity**

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Write Document, StyleTemplate, SourceMaterial, LearningJob entities**

```java
// Document.java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

```java
// StyleTemplate.java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "style_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StyleTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

```java
// SourceMaterial.java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "source_materials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceMaterial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private StyleTemplate template;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

```java
// LearningJob.java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private StyleTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private SourceMaterial material;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "result_prompt", columnDefinition = "TEXT")
    private String resultPrompt;

    @Column(name = "error_msg", length = 1000)
    private String errorMsg;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = JobStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum JobStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
```

- [ ] **Step 4: Write Spring Data JPA repositories**

```java
// UserRepository.java
package com.aiwriter.repository;

import com.aiwriter.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
```

```java
// ProjectRepository.java
package com.aiwriter.repository;

import com.aiwriter.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
```

```java
// DocumentRepository.java
package com.aiwriter.repository;

import com.aiwriter.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
```

```java
// StyleTemplateRepository.java
package com.aiwriter.repository;

import com.aiwriter.model.StyleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StyleTemplateRepository extends JpaRepository<StyleTemplate, Long> {
    List<StyleTemplate> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

```java
// SourceMaterialRepository.java
package com.aiwriter.repository;

import com.aiwriter.model.SourceMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SourceMaterialRepository extends JpaRepository<SourceMaterial, Long> {
    List<SourceMaterial> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
```

```java
// LearningJobRepository.java
package com.aiwriter.repository;

import com.aiwriter.model.LearningJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LearningJobRepository extends JpaRepository<LearningJob, Long> {
    List<LearningJob> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
    List<LearningJob> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

- [ ] **Step 5: Verify build**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/aiwriter/model/ backend/src/main/java/com/aiwriter/repository/
git commit -m "feat: add JPA entities and repositories"
```

---

## Phase 3: Backend Authentication

### Task 3.1: JWT token provider

**Files:**
- Create: `backend/src/main/java/com/aiwriter/security/JwtTokenProvider.java`

- [ ] **Step 1: Write JwtTokenProvider**

```java
package com.aiwriter.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(
                Jwts.parser().verifyWith(key).build()
                        .parseSignedClaims(token).getPayload().getSubject()
        );
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/aiwriter/security/JwtTokenProvider.java
git commit -m "feat: add JWT token provider"
```

---

### Task 3.2: JWT authentication filter & security config

**Files:**
- Create: `backend/src/main/java/com/aiwriter/security/JwtAuthenticationFilter.java`
- Create: `backend/src/main/java/com/aiwriter/config/SecurityConfig.java`

- [ ] **Step 1: Write JwtAuthenticationFilter**

```java
package com.aiwriter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            Long userId = tokenProvider.getUserIdFromToken(token);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 2: Write SecurityConfig**

```java
package com.aiwriter.config;

import com.aiwriter.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/aiwriter/security/JwtAuthenticationFilter.java backend/src/main/java/com/aiwriter/config/SecurityConfig.java
git commit -m "feat: add JWT filter and security configuration"
```

---

### Task 3.3: Auth DTOs, service, and controller

**Files:**
- Create: `backend/src/main/java/com/aiwriter/dto/request/RegisterRequest.java`
- Create: `backend/src/main/java/com/aiwriter/dto/request/LoginRequest.java`
- Create: `backend/src/main/java/com/aiwriter/dto/response/AuthResponse.java`
- Create: `backend/src/main/java/com/aiwriter/service/AuthService.java`
- Create: `backend/src/main/java/com/aiwriter/controller/AuthController.java`

- [ ] **Step 1: Write DTOs**

```java
// RegisterRequest.java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank @Size(min = 2, max = 50)
    private String username;

    @NotBlank @Email
    private String email;

    private String phone;

    @NotBlank @Size(min = 6, max = 100)
    private String password;
}
```

```java
// LoginRequest.java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String account;

    @NotBlank
    private String password;
}
```

```java
// AuthResponse.java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private UserInfo user;

    @Data
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String phone;
        private String createdAt;
    }
}
```

- [ ] **Step 2: Write AuthService**

```java
package com.aiwriter.service;

import com.aiwriter.dto.request.LoginRequest;
import com.aiwriter.dto.request.RegisterRequest;
import com.aiwriter.dto.response.AuthResponse;
import com.aiwriter.model.User;
import com.aiwriter.repository.UserRepository;
import com.aiwriter.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        if (userRepo.existsByUsername(req.getUsername())) {
            throw new RuntimeException("用户名已被使用");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .build();
        user = userRepo.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getAccount())
                .or(() -> userRepo.findByUsername(req.getAccount()))
                .or(() -> userRepo.findByPhone(req.getAccount()))
                .orElseThrow(() -> new RuntimeException("账号不存在"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .createdAt(user.getCreatedAt().toString())
                        .build())
                .build();
    }
}
```

- [ ] **Step 3: Write AuthController**

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.LoginRequest;
import com.aiwriter.dto.request.RegisterRequest;
import com.aiwriter.dto.response.AuthResponse;
import com.aiwriter.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/aiwriter/dto/ backend/src/main/java/com/aiwriter/service/AuthService.java backend/src/main/java/com/aiwriter/controller/AuthController.java
git commit -m "feat: add auth service and controller"
```

---

## Phase 4: Backend Projects & Documents

### Task 4.1: Project CRUD

**Files:**
- Create: `backend/src/main/java/com/aiwriter/dto/request/ProjectRequest.java`
- Create: `backend/src/main/java/com/aiwriter/dto/response/ProjectResponse.java`
- Create: `backend/src/main/java/com/aiwriter/service/ProjectService.java`
- Create: `backend/src/main/java/com/aiwriter/controller/ProjectController.java`

- [ ] **Step 1: Write ProjectRequest and ProjectResponse DTOs**

ProjectRequest.java:
```java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {
    @NotBlank @Size(min = 1, max = 100)
    private String name;
    @Size(max = 500)
    private String description;
}
```

ProjectResponse.java:
```java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private int docCount;
    private String createdAt;
    private String updatedAt;
}
```

- [ ] **Step 2: Write ProjectService**

```java
package com.aiwriter.service;

import com.aiwriter.dto.request.ProjectRequest;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepo;

    public List<ProjectResponse> list(Long userId) {
        return projectRepo.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public ProjectResponse create(Long userId, ProjectRequest req) {
        Project p = Project.builder()
                .user(User.builder().id(userId).build())
                .name(req.getName()).description(req.getDescription())
                .build();
        return toResponse(projectRepo.save(p));
    }

    public ProjectResponse update(Long userId, Long projectId, ProjectRequest req) {
        Project p = findOwned(userId, projectId);
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        return toResponse(projectRepo.save(p));
    }

    @Transactional
    public void delete(Long userId, Long projectId) {
        findOwned(userId, projectId);
        projectRepo.deleteById(projectId);
    }

    private Project findOwned(Long userId, Long projectId) {
        Project p = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        if (!p.getUser().getId().equals(userId))
            throw new RuntimeException("无权操作此项目");
        return p;
    }

    private ProjectResponse toResponse(Project p) {
        return ProjectResponse.builder()
                .id(p.getId()).name(p.getName()).description(p.getDescription())
                .createdAt(p.getCreatedAt().toString())
                .updatedAt(p.getUpdatedAt().toString()).build();
    }
}
```

- [ ] **Step 3: Write ProjectController**

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.ProjectRequest;
import com.aiwriter.dto.response.ProjectResponse;
import com.aiwriter.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(Authentication auth) {
        return ResponseEntity.ok(projectService.list(getUserId(auth)));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(Authentication auth, @Valid @RequestBody ProjectRequest req) {
        return ResponseEntity.ok(projectService.create(getUserId(auth), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return ResponseEntity.ok(projectService.update(getUserId(auth), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        projectService.delete(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

- [ ] **Step 4: Commit** — `git add` the above files and commit "feat: add project CRUD"

---

### Task 4.2: Document CRUD

**Files:**
- Create: `backend/src/main/java/com/aiwriter/dto/request/DocumentRequest.java`
- Create: `backend/src/main/java/com/aiwriter/dto/response/DocumentResponse.java`
- Create: `backend/src/main/java/com/aiwriter/service/DocumentService.java`
- Create: `backend/src/main/java/com/aiwriter/controller/DocumentController.java`

- [ ] **Step 1: Write DTOs**

DocumentRequest.java:
```java
package com.aiwriter.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentRequest {
    @NotBlank @Size(min = 1, max = 200)
    private String title;
    private String content;
}
```

DocumentResponse.java:
```java
package com.aiwriter.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private Long projectId;
    private String title;
    private String content;
    private String createdAt;
    private String updatedAt;
}
```

- [ ] **Step 2: Write DocumentService**

```java
package com.aiwriter.service;

import com.aiwriter.dto.request.DocumentRequest;
import com.aiwriter.dto.response.DocumentResponse;
import com.aiwriter.model.Document;
import com.aiwriter.model.Project;
import com.aiwriter.model.User;
import com.aiwriter.repository.DocumentRepository;
import com.aiwriter.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository docRepo;
    private final ProjectRepository projectRepo;

    public List<DocumentResponse> listByProject(Long userId, Long projectId) {
        return docRepo.findByProjectIdOrderByUpdatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    public DocumentResponse create(Long userId, Long projectId, DocumentRequest req) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new RuntimeException("项目不存在"));
        Document doc = Document.builder()
                .project(project).user(User.builder().id(userId).build())
                .title(req.getTitle()).content(req.getContent() != null ? req.getContent() : "")
                .build();
        return toResponse(docRepo.save(doc));
    }

    public DocumentResponse get(Long userId, Long docId) {
        return toResponse(findOwned(userId, docId));
    }

    public DocumentResponse update(Long userId, Long docId, DocumentRequest req) {
        Document doc = findOwned(userId, docId);
        doc.setTitle(req.getTitle());
        doc.setContent(req.getContent());
        return toResponse(docRepo.save(doc));
    }

    public void delete(Long userId, Long docId) {
        findOwned(userId, docId);
        docRepo.deleteById(docId);
    }

    private Document findOwned(Long userId, Long docId) {
        Document doc = docRepo.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        if (!doc.getUser().getId().equals(userId))
            throw new RuntimeException("无权操作此文档");
        return doc;
    }

    private DocumentResponse toResponse(Document d) {
        return DocumentResponse.builder()
                .id(d.getId()).projectId(d.getProject().getId())
                .title(d.getTitle()).content(d.getContent())
                .createdAt(d.getCreatedAt().toString())
                .updatedAt(d.getUpdatedAt().toString()).build();
    }
}
```

- [ ] **Step 3: Write DocumentController**

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.DocumentRequest;
import com.aiwriter.dto.response.DocumentResponse;
import com.aiwriter.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService docService;

    @GetMapping("/projects/{projectId}/docs")
    public ResponseEntity<List<DocumentResponse>> list(Authentication auth, @PathVariable Long projectId) {
        return ResponseEntity.ok(docService.listByProject(getUserId(auth), projectId));
    }

    @PostMapping("/projects/{projectId}/docs")
    public ResponseEntity<DocumentResponse> create(Authentication auth, @PathVariable Long projectId, @Valid @RequestBody DocumentRequest req) {
        return ResponseEntity.ok(docService.create(getUserId(auth), projectId, req));
    }

    @GetMapping("/docs/{id}")
    public ResponseEntity<DocumentResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(docService.get(getUserId(auth), id));
    }

    @PutMapping("/docs/{id}")
    public ResponseEntity<DocumentResponse> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody DocumentRequest req) {
        return ResponseEntity.ok(docService.update(getUserId(auth), id, req));
    }

    @DeleteMapping("/docs/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        docService.delete(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

- [ ] **Step 4: Commit** — "feat: add document CRUD"

---

## Phase 5: Backend AI Writing

### Task 5.1: Claude integration, AI DTOs, and streaming endpoints

**Files:**
- Create: `backend/src/main/java/com/aiwriter/dto/request/AiRequest.java`
- Create: `backend/src/main/java/com/aiwriter/ai/ClaudeService.java`
- Create: `backend/src/main/java/com/aiwriter/ai/PromptBuilder.java`
- Create: `backend/src/main/java/com/aiwriter/service/AiService.java`
- Create: `backend/src/main/java/com/aiwriter/controller/AiController.java`

- [ ] **Step 1: Write AiRequest DTO**

```java
package com.aiwriter.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRequest {
    @NotBlank private String content;
    private Long styleTemplateId;
    private String instruction;
}
```

- [ ] **Step 2: Write ClaudeService**

```java
package com.aiwriter.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.List;

@Service
public class ClaudeService {
    private final AnthropicChatModel chatModel;

    public ClaudeService(AnthropicChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Flux<String> stream(String systemPrompt, String userPrompt) {
        SystemMessage sysMsg = new SystemMessage(systemPrompt);
        UserMessage usrMsg = new UserMessage(userPrompt);
        Prompt prompt = new Prompt(List.of(sysMsg, usrMsg));
        return chatModel.stream(prompt).map(chunk -> {
            String content = chunk.getResult().getOutput().getText();
            return content != null ? content : "";
        });
    }

    public String complete(String systemPrompt, String userPrompt) {
        SystemMessage sysMsg = new SystemMessage(systemPrompt);
        UserMessage usrMsg = new UserMessage(userPrompt);
        Prompt prompt = new Prompt(List.of(sysMsg, usrMsg));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }
}
```

- [ ] **Step 3: Write PromptBuilder**

```java
package com.aiwriter.ai;

import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildOutlineSystem() {
        return "你是一个专业写作助手。请根据用户提供的主题，生成详细大纲。" +
                "包含核心论点、章节划分（3-7章）、每章核心内容。使用层级编号。";
    }

    private String wrapStyle(String base, String style) {
        if (style != null && !style.isEmpty())
            return base + "\n\n写作风格要求：\n" + style;
        return base;
    }

    public String buildContinueSystem(String s) {
        return wrapStyle("你是一个专业写作助手。请续写用户提供的内容，保持一致的文风、语气和叙事节奏。", s);
    }
    public String buildPolishSystem(String s) {
        return wrapStyle("你是一个专业润色助手。请改写优化用户提供的文本，提升表达质量。保持原意不变。", s);
    }
    public String buildExpandSystem(String s) {
        return wrapStyle("你是一个专业写作助手。请扩展用户提供的内容，增加细节描写和论证，使内容更丰富饱满。", s);
    }
    public String buildShortenSystem(String s) {
        return wrapStyle("你是一个专业编辑。请精简用户提供的内容，删除冗余，保留核心信息和精华表达。", s);
    }
}
```

- [ ] **Step 4: Write AiService**

```java
package com.aiwriter.service;

import com.aiwriter.ai.ClaudeService;
import com.aiwriter.ai.PromptBuilder;
import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.model.StyleTemplate;
import com.aiwriter.repository.StyleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class AiService {
    private final ClaudeService claude;
    private final PromptBuilder prompts;
    private final StyleTemplateRepository styleRepo;

    public Flux<String> generateOutline(AiRequest req) {
        return claude.stream(prompts.buildOutlineSystem(), "主题：" + req.getContent());
    }

    public Flux<String> continueWrite(AiRequest req) {
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请续写：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildContinueSystem(style), msg);
    }

    public Flux<String> polish(AiRequest req) {
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请润色：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildPolishSystem(style), msg);
    }

    public Flux<String> expand(AiRequest req) {
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请扩展：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildExpandSystem(style), msg);
    }

    public Flux<String> shorten(AiRequest req) {
        String style = getStylePrompt(req.getStyleTemplateId());
        String msg = "请精简：\n\n" + req.getContent();
        if (req.getInstruction() != null) msg = req.getInstruction() + "\n\n" + msg;
        return claude.stream(prompts.buildShortenSystem(style), msg);
    }

    private String getStylePrompt(Long id) {
        if (id == null) return null;
        return styleRepo.findById(id).map(StyleTemplate::getSystemPrompt).orElse(null);
    }
}
```

- [ ] **Step 5: Write AiController (SSE streaming)**

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.AiRequest;
import com.aiwriter.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping(value = "/generate-outline", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateOutline(@Valid @RequestBody AiRequest req) {
        return aiService.generateOutline(req);
    }

    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(@Valid @RequestBody AiRequest req) {
        return aiService.continueWrite(req);
    }

    @PostMapping(value = "/polish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> polish(@Valid @RequestBody AiRequest req) {
        return aiService.polish(req);
    }

    @PostMapping(value = "/expand", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> expand(@Valid @RequestBody AiRequest req) {
        return aiService.expand(req);
    }

    @PostMapping(value = "/shorten", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> shorten(@Valid @RequestBody AiRequest req) {
        return aiService.shorten(req);
    }
}
```

- [ ] **Step 6: Commit** — "feat: add AI writing endpoints with Claude SSE streaming"

---

## Phase 6: Backend Style Learning

### Task 6.1: Style analyzer, learning service, style CRUD

**Files:**
- Create: `backend/src/main/java/com/aiwriter/ai/StyleAnalyzer.java`
- Create: `backend/src/main/java/com/aiwriter/service/StyleService.java`
- Create: `backend/src/main/java/com/aiwriter/service/LearningService.java`
- Create: `backend/src/main/java/com/aiwriter/controller/StyleController.java`
- Create: `backend/src/main/java/com/aiwriter/dto/request/StyleRequest.java`
- Create: `backend/src/main/java/com/aiwriter/dto/request/LearnRequest.java`
- Create: `backend/src/main/java/com/aiwriter/dto/response/StyleResponse.java`
- Create: `backend/src/main/java/com/aiwriter/dto/response/JobResponse.java`

- [ ] **Step 1: Write StyleAnalyzer**

```java
package com.aiwriter.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StyleAnalyzer {
    private final ClaudeService claude;

    public String analyzeStyle(String materialContent) {
        String systemPrompt = """
                你是一个文学风格分析专家。请分析用户提供的文本，提取写作风格特征。
                从以下维度分析：叙事视角、句式特征、用词偏好、节奏特点、情绪基调、代表性句式（3-5个）。
                最后整理成一段"写作风格指令"，以"请以以下风格写作："开头，200字以内。""";
        return claude.complete(systemPrompt, materialContent);
    }
}
```

- [ ] **Step 2: Write StyleService (CRUD)**

```java
package com.aiwriter.service;

import com.aiwriter.dto.request.StyleRequest;
import com.aiwriter.dto.response.StyleResponse;
import com.aiwriter.model.StyleTemplate;
import com.aiwriter.model.User;
import com.aiwriter.repository.StyleTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StyleService {
    private final StyleTemplateRepository styleRepo;

    public List<StyleResponse> list(Long userId) {
        return styleRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public StyleResponse create(Long userId, StyleRequest req) {
        StyleTemplate st = StyleTemplate.builder()
                .user(User.builder().id(userId).build())
                .name(req.getName()).systemPrompt(req.getSystemPrompt())
                .description(req.getDescription()).build();
        return toResponse(styleRepo.save(st));
    }

    public StyleResponse get(Long userId, Long id) { return toResponse(findOwned(userId, id)); }

    public StyleResponse update(Long userId, Long id, StyleRequest req) {
        StyleTemplate st = findOwned(userId, id);
        st.setName(req.getName());
        st.setSystemPrompt(req.getSystemPrompt());
        st.setDescription(req.getDescription());
        return toResponse(styleRepo.save(st));
    }

    public void delete(Long userId, Long id) {
        findOwned(userId, id);
        styleRepo.deleteById(id);
    }

    private StyleTemplate findOwned(Long userId, Long id) {
        StyleTemplate st = styleRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("风格模板不存在"));
        if (!st.getUser().getId().equals(userId))
            throw new RuntimeException("无权操作此模板");
        return st;
    }

    private StyleResponse toResponse(StyleTemplate st) {
        return StyleResponse.builder()
                .id(st.getId()).name(st.getName())
                .systemPrompt(st.getSystemPrompt()).description(st.getDescription())
                .createdAt(st.getCreatedAt().toString()).build();
    }
}
```

- [ ] **Step 3: Write LearningService**

```java
package com.aiwriter.service;

import com.aiwriter.ai.StyleAnalyzer;
import com.aiwriter.dto.request.LearnRequest;
import com.aiwriter.dto.response.JobResponse;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LearningService {
    private final SourceMaterialRepository materialRepo;
    private final LearningJobRepository jobRepo;
    private final StyleTemplateRepository styleRepo;
    private final StyleAnalyzer analyzer;

    @Transactional
    public JobResponse startLearning(Long userId, Long templateId, LearnRequest req) {
        StyleTemplate template = styleRepo.findById(templateId)
                .orElseThrow(() -> new RuntimeException("风格模板不存在"));

        SourceMaterial material = SourceMaterial.builder()
                .user(User.builder().id(userId).build()).template(template)
                .title(req.getTitle()).content(req.getContent()).build();
        material = materialRepo.save(material);

        LearningJob job = LearningJob.builder()
                .user(User.builder().id(userId).build()).template(template)
                .material(material).status(LearningJob.JobStatus.PENDING).build();
        job = jobRepo.save(job);

        processJobAsync(job.getId());
        return toResponse(job);
    }

    @Async
    protected void processJobAsync(Long jobId) {
        LearningJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) return;
        try {
            job.setStatus(LearningJob.JobStatus.PROCESSING);
            jobRepo.save(job);
            String result = analyzer.analyzeStyle(job.getMaterial().getContent());
            job.setResultPrompt(result);
            job.setStatus(LearningJob.JobStatus.COMPLETED);
            StyleTemplate template = job.getTemplate();
            template.setSystemPrompt(result);
            styleRepo.save(template);
        } catch (Exception e) {
            job.setStatus(LearningJob.JobStatus.FAILED);
            job.setErrorMsg(e.getMessage());
        }
        jobRepo.save(job);
    }

    public List<JobResponse> listJobs(Long templateId) {
        return jobRepo.findByTemplateIdOrderByCreatedAtDesc(templateId)
                .stream().map(this::toResponse).toList();
    }

    public JobResponse getJob(Long id) {
        LearningJob j = jobRepo.findById(id).orElseThrow(() -> new RuntimeException("任务不存在"));
        return toResponse(j);
    }

    private JobResponse toResponse(LearningJob j) {
        return JobResponse.builder()
                .id(j.getId()).templateId(j.getTemplate().getId())
                .materialId(j.getMaterial().getId()).status(j.getStatus().name())
                .resultPrompt(j.getResultPrompt()).errorMsg(j.getErrorMsg())
                .createdAt(j.getCreatedAt().toString())
                .updatedAt(j.getUpdatedAt().toString()).build();
    }
}
```

- [ ] **Step 4: Write StyleController**

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.LearnRequest;
import com.aiwriter.dto.request.StyleRequest;
import com.aiwriter.dto.response.JobResponse;
import com.aiwriter.dto.response.StyleResponse;
import com.aiwriter.service.LearningService;
import com.aiwriter.service.StyleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StyleController {
    private final StyleService styleService;
    private final LearningService learningService;

    @GetMapping("/styles")
    public ResponseEntity<List<StyleResponse>> list(Authentication auth) {
        return ResponseEntity.ok(styleService.list(getUserId(auth)));
    }

    @PostMapping("/styles")
    public ResponseEntity<StyleResponse> create(Authentication auth, @Valid @RequestBody StyleRequest req) {
        return ResponseEntity.ok(styleService.create(getUserId(auth), req));
    }

    @GetMapping("/styles/{id}")
    public ResponseEntity<StyleResponse> get(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(styleService.get(getUserId(auth), id));
    }

    @PutMapping("/styles/{id}")
    public ResponseEntity<StyleResponse> update(Authentication auth, @PathVariable Long id, @Valid @RequestBody StyleRequest req) {
        return ResponseEntity.ok(styleService.update(getUserId(auth), id, req));
    }

    @DeleteMapping("/styles/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long id) {
        styleService.delete(getUserId(auth), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/styles/{id}/learn")
    public ResponseEntity<JobResponse> learn(Authentication auth, @PathVariable Long id, @Valid @RequestBody LearnRequest req) {
        return ResponseEntity.ok(learningService.startLearning(getUserId(auth), id, req));
    }

    @GetMapping("/styles/{id}/jobs")
    public ResponseEntity<List<JobResponse>> jobs(@PathVariable Long id) {
        return ResponseEntity.ok(learningService.listJobs(id));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> job(@PathVariable Long id) {
        return ResponseEntity.ok(learningService.getJob(id));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

- [ ] **Step 5: Write remaining DTOs**

StyleRequest.java:
```java
package com.aiwriter.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StyleRequest {
    @NotBlank @Size(min = 1, max = 100)
    private String name;
    @NotBlank private String systemPrompt;
    @Size(max = 500) private String description;
}
```

LearnRequest.java:
```java
package com.aiwriter.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearnRequest {
    @NotBlank @Size(min = 1, max = 200)
    private String title;
    @NotBlank private String content;
}
```

StyleResponse.java:
```java
package com.aiwriter.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class StyleResponse {
    private Long id;
    private String name;
    private String systemPrompt;
    private String description;
    private String createdAt;
}
```

JobResponse.java:
```java
package com.aiwriter.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class JobResponse {
    private Long id;
    private Long templateId;
    private Long materialId;
    private String status;
    private String resultPrompt;
    private String errorMsg;
    private String createdAt;
    private String updatedAt;
}
```

- [ ] **Step 6: Add @EnableAsync and GlobalExceptionHandler**

Update AiWriterApplication.java:
```java
package com.aiwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AiWriterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiWriterApplication.class, args);
    }
}
```

Create GlobalExceptionHandler.java:
```java
package com.aiwriter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b).orElse("参数校验失败");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg));
    }
}
```

- [ ] **Step 7: Commit** — "feat: add style learning with async Claude analysis"

---

## Phase 7: Frontend Setup — Routing, Axios, Auth

### Task 7.1: Router, Axios client, and auth store

**Files:**
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/stores/auth.ts`

- [ ] **Step 1: Write router/index.ts**

```typescript
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login', name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/register', name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { guest: true },
    },
    {
      path: '/',
      component: () => import('@/views/DashboardLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: { name: 'workspace' } },
        { path: 'project/:id?', name: 'workspace', component: () => import('@/views/WorkspaceView.vue') },
        { path: 'project/:projectId/doc/:docId', name: 'editor', component: () => import('@/views/WorkspaceView.vue') },
        { path: 'styles', name: 'styles', component: () => import('@/views/StyleManageView.vue') },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) next('/login')
  else if (to.meta.guest && auth.isLoggedIn) next('/')
  else next()
})

export default router
```

- [ ] **Step 2: Write api/client.ts (Axios with interceptors)**

```typescript
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const client = axios.create({ baseURL: '/api', timeout: 30000 })

client.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
  return config
})

client.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401) {
      const auth = useAuthStore()
      const refreshed = await auth.refresh()
      if (refreshed) {
        error.config.headers.Authorization = `Bearer ${auth.accessToken}`
        return client(error.config)
      }
      auth.logout()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client
```

- [ ] **Step 3: Write api/auth.ts and stores/auth.ts**

auth.ts:
```typescript
import client from './client'
import type { LoginRequest, RegisterRequest, AuthResponse } from '@/types'

export const authApi = {
  login: (data: LoginRequest) => client.post<AuthResponse>('/auth/login', data),
  register: (data: RegisterRequest) => client.post<AuthResponse>('/auth/register', data),
  refresh: (refreshToken: string) => client.post<AuthResponse>('/auth/refresh', { refreshToken }),
}
```

auth.ts store:
```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest, RegisterRequest } from '@/types'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const accessToken = ref<string | null>(localStorage.getItem('access_token'))
  const refreshToken = ref<string | null>(localStorage.getItem('refresh_token'))
  const isLoggedIn = computed(() => !!accessToken.value)

  function setAuth(data: any) {
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('refresh_token', data.refreshToken)
  }

  async function login(req: LoginRequest) { const res = await authApi.login(req); setAuth(res.data) }
  async function register(req: RegisterRequest) { const res = await authApi.register(req); setAuth(res.data) }

  async function refresh(): Promise<boolean> {
    if (!refreshToken.value) return false
    try {
      const res = await authApi.refresh(refreshToken.value)
      setAuth(res.data)
      return true
    } catch { return false }
  }

  function logout() {
    user.value = null; accessToken.value = null; refreshToken.value = null
    localStorage.removeItem('access_token'); localStorage.removeItem('refresh_token')
  }

  return { user, accessToken, refreshToken, isLoggedIn, login, register, refresh, logout }
})
```

- [ ] **Step 4: Commit** — "feat: add frontend router, Axios client, and auth store"

---

### Task 7.2: Login and Register views

**Files:**
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`
- Create: `frontend/src/components/auth/LoginForm.vue`
- Create: `frontend/src/components/auth/RegisterForm.vue`

- [ ] **Step 1: Write LoginForm.vue**

```vue
<template>
  <el-form ref="formRef" :model="form" :rules="rules">
    <el-form-item prop="account">
      <el-input v-model="form.account" placeholder="邮箱 / 用户名 / 手机号" size="large" />
    </el-form-item>
    <el-form-item prop="password">
      <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" size="large" :loading="loading" @click="handleSubmit" style="width:100%">登录</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ account: '', password: '' })
const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.login({ account: form.account, password: form.password })
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 2: Write RegisterForm.vue**

```vue
<template>
  <el-form ref="formRef" :model="form" :rules="rules">
    <el-form-item prop="username">
      <el-input v-model="form.username" placeholder="用户名" size="large" />
    </el-form-item>
    <el-form-item prop="email">
      <el-input v-model="form.email" placeholder="邮箱" size="large" />
    </el-form-item>
    <el-form-item prop="phone">
      <el-input v-model="form.phone" placeholder="手机号（选填）" size="large" />
    </el-form-item>
    <el-form-item prop="password">
      <el-input v-model="form.password" type="password" placeholder="密码（6位以上）" size="large" show-password />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" size="large" :loading="loading" @click="handleSubmit" style="width:100%">注册</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', email: '', phone: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ required: true, type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await auth.register({ username: form.username, email: form.email, phone: form.phone || undefined, password: form.password })
    ElMessage.success('注册成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '注册失败')
  } finally { loading.value = false }
}
</script>
```

- [ ] **Step 3: Write LoginView.vue and RegisterView.vue**

LoginView.vue:
```vue
<template>
  <div class="auth-container">
    <el-card class="auth-card">
      <h1 class="auth-title">AI Writer</h1>
      <p class="auth-subtitle">AI 写作助手</p>
      <LoginForm />
      <p class="auth-link">还没有账号？<router-link to="/register">立即注册</router-link></p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import LoginForm from '@/components/auth/LoginForm.vue'
</script>

<style scoped>
.auth-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
.auth-card { width: 420px; padding: 20px; }
.auth-title { text-align: center; font-size: 28px; margin-bottom: 4px; }
.auth-subtitle { text-align: center; color: #909399; margin-bottom: 24px; }
.auth-link { text-align: center; margin-top: 16px; color: #909399; }
</style>
```

RegisterView.vue:
```vue
<template>
  <div class="auth-container">
    <el-card class="auth-card">
      <h1 class="auth-title">创建账号</h1>
      <p class="auth-subtitle">加入 AI Writer</p>
      <RegisterForm />
      <p class="auth-link">已有账号？<router-link to="/login">立即登录</router-link></p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import RegisterForm from '@/components/auth/RegisterForm.vue'
</script>

<style scoped>
.auth-container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f7fa; }
.auth-card { width: 420px; padding: 20px; }
.auth-title { text-align: center; font-size: 28px; margin-bottom: 4px; }
.auth-subtitle { text-align: center; color: #909399; margin-bottom: 24px; }
.auth-link { text-align: center; margin-top: 16px; color: #909399; }
</style>
```

- [ ] **Step 5: Commit** — "feat: add login and register pages"

---

## Phase 8: Frontend Dashboard & Workspace

### Task 8.1: Dashboard layout components and project store

**Files:**
- Create: `frontend/src/views/DashboardLayout.vue`
- Create: `frontend/src/components/layout/AppHeader.vue`
- Create: `frontend/src/components/layout/ProjectSidebar.vue`
- Create: `frontend/src/api/projects.ts`
- Create: `frontend/src/stores/projects.ts`

- [ ] **Step 1: Write projects API and store**

api/projects.ts:
```typescript
import client from './client'
import type { Project } from '@/types'

export const projectsApi = {
  list: () => client.get<Project[]>('/projects'),
  create: (data: { name: string; description?: string }) => client.post<Project>('/projects', data),
  update: (id: number, data: { name: string; description?: string }) => client.put<Project>(`/projects/${id}`, data),
  remove: (id: number) => client.delete(`/projects/${id}`),
}
```

stores/projects.ts:
```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project } from '@/types'
import { projectsApi } from '@/api/projects'

export const useProjectStore = defineStore('projects', () => {
  const projects = ref<Project[]>([])
  const currentId = ref<number | null>(null)
  const loading = ref(false)

  async function fetch() {
    loading.value = true
    try { const res = await projectsApi.list(); projects.value = res.data }
    finally { loading.value = false }
  }

  async function create(name: string, description?: string) {
    const res = await projectsApi.create({ name, description })
    projects.value.unshift(res.data)
    return res.data
  }

  async function update(id: number, name: string, description?: string) {
    await projectsApi.update(id, { name, description })
    const p = projects.value.find(p => p.id === id)
    if (p) { p.name = name; p.description = description }
  }

  async function remove(id: number) {
    await projectsApi.remove(id)
    projects.value = projects.value.filter(p => p.id !== id)
    if (currentId.value === id) currentId.value = null
  }

  function setCurrent(id: number | null) { currentId.value = id }

  return { projects, currentId, loading, fetch, create, update, remove, setCurrent }
})
```

- [ ] **Step 2: Write AppHeader.vue**

```vue
<template>
  <el-header class="app-header">
    <span class="logo">AI Writer</span>
    <div class="header-right">
      <router-link to="/styles" class="nav-link">风格管理</router-link>
      <el-dropdown>
        <span class="user-info">{{ auth.user?.username }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
const auth = useAuthStore()
const router = useRouter()
function handleLogout() { auth.logout(); router.push('/login') }
</script>

<style scoped>
.app-header { display: flex; align-items: center; justify-content: space-between; padding: 0 20px; background: #fff; border-bottom: 1px solid #e4e7ed; height: 56px; }
.logo { font-size: 18px; font-weight: 700; }
.header-right { display: flex; align-items: center; gap: 16px; }
.nav-link { color: #606266; text-decoration: none; font-size: 14px; }
.nav-link:hover { color: #409eff; }
.user-info { cursor: pointer; }
</style>
```

- [ ] **Step 3: Write ProjectSidebar.vue**

```vue
<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <span>我的项目</span>
      <el-button size="small" @click="showCreate = true">+ 新建</el-button>
    </div>
    <div class="project-list">
      <div v-for="p in store.projects" :key="p.id"
        :class="['project-item', { active: p.id === store.currentId }]"
        @click="selectProject(p.id)">
        <span>{{ p.name }}</span>
        <el-dropdown trigger="click" @click.stop>
          <el-icon><MoreFilled /></el-icon>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="startRename(p)">重命名</el-dropdown-item>
              <el-dropdown-item @click="handleDelete(p.id)">删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-dialog v-model="showCreate" title="新建项目" width="400px">
      <el-input v-model="newName" placeholder="项目名称" />
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRename" title="重命名" width="400px">
      <el-input v-model="renameText" placeholder="新名称" />
      <template #footer>
        <el-button @click="showRename = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/projects'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'

const router = useRouter()
const store = useProjectStore()
const showCreate = ref(false); const newName = ref('')
const showRename = ref(false); const renameText = ref(''); let renameId = 0

function selectProject(id: number) { store.setCurrent(id); router.push(`/project/${id}`) }

async function handleCreate() {
  if (!newName.value.trim()) return
  const p = await store.create(newName.value.trim())
  showCreate.value = false; newName.value = ''
  store.setCurrent(p.id); router.push(`/project/${p.id}`)
}

function startRename(p: Project) { renameId = p.id; renameText.value = p.name; showRename.value = true }

async function handleRename() {
  if (!renameText.value.trim()) return
  await store.update(renameId, renameText.value.trim())
  showRename.value = false
}

async function handleDelete(id: number) { await store.remove(id); ElMessage.success('项目已删除') }
</script>

<style scoped>
.sidebar { width: 240px; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column; background: #fafafa; }
.sidebar-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.project-list { flex: 1; overflow-y: auto; }
.project-item { padding: 10px 12px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; }
.project-item:hover { background: #ecf5ff; }
.project-item.active { background: #d9ecff; color: #409eff; font-weight: 600; }
</style>
```

- [ ] **Step 4: Write DashboardLayout.vue**

```vue
<template>
  <div class="dashboard">
    <AppHeader />
    <div class="dashboard-body">
      <ProjectSidebar />
      <main class="main-content"><router-view /></main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import AppHeader from '@/components/layout/AppHeader.vue'
import ProjectSidebar from '@/components/layout/ProjectSidebar.vue'
import { useProjectStore } from '@/stores/projects'
const projects = useProjectStore()
onMounted(() => projects.fetch())
</script>

<style scoped>
.dashboard { height: 100vh; display: flex; flex-direction: column; }
.dashboard-body { flex: 1; display: flex; overflow: hidden; }
.main-content { flex: 1; overflow-y: auto; background: #fff; }
</style>
```

- [ ] **Step 5: Commit** — "feat: add dashboard layout with project sidebar"

---

## Phase 9: Frontend Editor & AI Panel

### Task 9.1: Documents store, API, DocList, and WorkspaceView

**Files:**
- Create: `frontend/src/api/documents.ts`
- Create: `frontend/src/stores/documents.ts`
- Create: `frontend/src/views/WorkspaceView.vue`
- Create: `frontend/src/components/editor/DocList.vue`

- [ ] **Step 1: Write documents API and store**

api/documents.ts:
```typescript
import client from './client'
import type { Document } from '@/types'

export const docsApi = {
  list: (projectId: number) => client.get<Document[]>(`/projects/${projectId}/docs`),
  get: (id: number) => client.get<Document>(`/docs/${id}`),
  create: (projectId: number, data: { title: string; content?: string }) =>
    client.post<Document>(`/projects/${projectId}/docs`, data),
  update: (id: number, data: { title: string; content: string }) =>
    client.put<Document>(`/docs/${id}`, data),
  remove: (id: number) => client.delete(`/docs/${id}`),
}
```

stores/documents.ts:
```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Document } from '@/types'
import { docsApi } from '@/api/documents'

export const useDocStore = defineStore('documents', () => {
  const docs = ref<Document[]>([])
  const currentDoc = ref<Document | null>(null)
  const loading = ref(false)

  async function fetchByProject(projectId: number) {
    loading.value = true
    try { const res = await docsApi.list(projectId); docs.value = res.data }
    finally { loading.value = false }
  }

  async function fetchOne(id: number) {
    const res = await docsApi.get(id); currentDoc.value = res.data
  }

  async function create(projectId: number, title: string) {
    const res = await docsApi.create(projectId, { title, content: '' })
    docs.value.unshift(res.data); return res.data
  }

  async function save(id: number, title: string, content: string) {
    await docsApi.update(id, { title, content })
    const d = docs.value.find(d => d.id === id)
    if (d) { d.title = title; d.content = content }
    if (currentDoc.value?.id === id) { currentDoc.value.title = title; currentDoc.value.content = content }
  }

  async function remove(id: number) {
    await docsApi.remove(id)
    docs.value = docs.value.filter(d => d.id !== id)
    if (currentDoc.value?.id === id) currentDoc.value = null
  }

  return { docs, currentDoc, loading, fetchByProject, fetchOne, create, save, remove }
})
```

- [ ] **Step 2: Write WorkspaceView.vue and DocList.vue**

WorkspaceView.vue:
```vue
<template>
  <div class="workspace">
    <DocList v-if="projectId" :project-id="projectId" />
    <div class="editor-area">
      <DocEditor v-if="currentId" :doc-id="currentId" />
      <div v-else class="empty-state">选择一篇文档开始写作</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useProjectStore } from '@/stores/projects'
import DocList from '@/components/editor/DocList.vue'
import DocEditor from '@/components/editor/DocEditor.vue'

const route = useRoute()
const projects = useProjectStore()
const projectId = computed(() => Number(route.params.projectId) || projects.currentId)
const currentId = computed(() => Number(route.params.docId) || null)

watch(projectId, (id) => { if (id) projects.setCurrent(id) }, { immediate: true })
</script>

<style scoped>
.workspace { display: flex; height: 100%; }
.editor-area { flex: 1; }
.empty-state { display: flex; align-items: center; justify-content: center; height: 100%; color: #909399; font-size: 16px; }
</style>
```

DocList.vue:
```vue
<template>
  <div class="doc-list">
    <div class="doc-list-header">
      <span>文档</span>
      <el-button size="small" @click="handleCreate">+ 新建</el-button>
    </div>
    <div v-for="doc in store.docs" :key="doc.id"
      :class="['doc-item', { active: doc.id === activeId }]"
      @click="$router.push(`/project/${projectId}/doc/${doc.id}`)">
      <span class="doc-title">{{ doc.title }}</span>
      <span class="doc-date">{{ doc.updatedAt.slice(0, 10) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDocStore } from '@/stores/documents'

const props = defineProps<{ projectId: number }>()
const route = useRoute(); const router = useRouter()
const store = useDocStore()
const activeId = computed(() => Number(route.params.docId))

onMounted(() => store.fetchByProject(props.projectId))

async function handleCreate() {
  const doc = await store.create(props.projectId, '未命名文档')
  router.push(`/project/${props.projectId}/doc/${doc.id}`)
}
</script>

<style scoped>
.doc-list { width: 240px; border-right: 1px solid #e4e7ed; background: #fafafa; overflow-y: auto; }
.doc-list-header { padding: 12px; display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.doc-item { padding: 10px 12px; cursor: pointer; }
.doc-item:hover { background: #ecf5ff; }
.doc-item.active { background: #d9ecff; }
.doc-title { display: block; font-size: 14px; }
.doc-date { font-size: 11px; color: #909399; }
</style>
```

- [ ] **Step 3: Commit** — "feat: add document list and workspace view"

---

### Task 9.2: Tiptap editor and AI panel

**Files:**
- Create: `frontend/src/api/ai.ts`
- Create: `frontend/src/stores/editor.ts`
- Create: `frontend/src/components/editor/TiptapEditor.vue`
- Create: `frontend/src/components/editor/EditorToolbar.vue`
- Create: `frontend/src/components/editor/AIPanel.vue`
- Create: `frontend/src/components/editor/DocEditor.vue`

- [ ] **Step 1: Write AI API**

```typescript
import client from './client'
import type { AiRequest } from '@/types'

export const aiApi = {
  generateOutline: (data: AiRequest) =>
    client.post('/ai/generate-outline', data, { responseType: 'stream', adapter: 'fetch' }),
  continue: (data: AiRequest) =>
    client.post('/ai/continue', data, { responseType: 'stream', adapter: 'fetch' }),
  polish: (data: AiRequest) =>
    client.post('/ai/polish', data, { responseType: 'stream', adapter: 'fetch' }),
  expand: (data: AiRequest) =>
    client.post('/ai/expand', data, { responseType: 'stream', adapter: 'fetch' }),
  shorten: (data: AiRequest) =>
    client.post('/ai/shorten', data, { responseType: 'stream', adapter: 'fetch' }),
}
```

- [ ] **Step 2: Write TiptapEditor.vue**

```vue
<template>
  <div class="tiptap-wrapper">
    <EditorToolbar :editor="editor" />
    <editor-content :editor="editor" class="tiptap-content" />
  </div>
</template>

<script setup lang="ts">
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Placeholder from '@tiptap/extension-placeholder'
import EditorToolbar from './EditorToolbar.vue'
import { watch, onBeforeUnmount } from 'vue'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits(['update:modelValue'])

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit, Underline,
    Placeholder.configure({ placeholder: '开始写作...' }),
  ],
  onUpdate: ({ editor }) => { emit('update:modelValue', editor.getHTML()) },
})

watch(() => props.modelValue, (val) => {
  if (editor.value && editor.value.getHTML() !== val) {
    editor.value.commands.setContent(val)
  }
})

onBeforeUnmount(() => editor.value?.destroy())
</script>

<style>
.tiptap-wrapper { border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden; min-height: 400px; flex: 1; display: flex; flex-direction: column; }
.tiptap-content { padding: 16px 24px; flex: 1; }
.tiptap-content .ProseMirror { outline: none; min-height: 300px; line-height: 1.8; }
.tiptap-content .ProseMirror p.is-editor-empty:first-child::before { content: attr(data-placeholder); color: #c0c4cc; float: left; height: 0; pointer-events: none; }
.tiptap-content h1 { font-size: 24px; margin: 12px 0 8px; }
.tiptap-content h2 { font-size: 20px; margin: 10px 0 6px; }
.tiptap-content blockquote { border-left: 3px solid #409eff; padding-left: 12px; color: #606266; }
.tiptap-content pre { background: #f5f7fa; padding: 12px; border-radius: 4px; overflow-x: auto; }
</style>
```

- [ ] **Step 3: Write EditorToolbar.vue**

```vue
<template>
  <div class="toolbar" v-if="editor">
    <button @click="editor.chain().focus().toggleBold().run()" :class="{ active: editor.isActive('bold') }"><b>B</b></button>
    <button @click="editor.chain().focus().toggleItalic().run()" :class="{ active: editor.isActive('italic') }"><i>I</i></button>
    <button @click="editor.chain().focus().toggleUnderline().run()" :class="{ active: editor.isActive('underline') }"><u>U</u></button>
    <span class="sep" />
    <button @click="editor.chain().focus().toggleHeading({ level: 1 }).run()" :class="{ active: editor.isActive('heading', { level: 1 }) }">H1</button>
    <button @click="editor.chain().focus().toggleHeading({ level: 2 }).run()" :class="{ active: editor.isActive('heading', { level: 2 }) }">H2</button>
    <span class="sep" />
    <button @click="editor.chain().focus().toggleBlockquote().run()" :class="{ active: editor.isActive('blockquote') }">引用</button>
    <button @click="editor.chain().focus().toggleBulletList().run()">列表</button>
    <button @click="editor.chain().focus().toggleCodeBlock().run()">代码</button>
  </div>
</template>

<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3'
defineProps<{ editor: Editor | null }>()
</script>

<style scoped>
.toolbar { display: flex; gap: 2px; padding: 8px 12px; border-bottom: 1px solid #e4e7ed; background: #fafafa; flex-wrap: wrap; }
.toolbar button { padding: 4px 10px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; cursor: pointer; font-size: 13px; }
.toolbar button:hover { border-color: #409eff; color: #409eff; }
.toolbar button.active { background: #409eff; color: #fff; border-color: #409eff; }
.sep { width: 1px; background: #dcdfe6; margin: 0 4px; }
</style>
```

- [ ] **Step 4: Write AIPanel.vue**

```vue
<template>
  <div class="ai-panel">
    <div class="ai-panel-header">AI 助手</div>
    <div class="ai-tools">
      <el-select v-model="styleId" placeholder="选择风格" clearable size="small" style="width:100%;margin-bottom:8px;">
        <el-option v-for="s in styleStore.styles" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>

      <div class="ai-btn-group">
        <el-button size="small" @click="callAi('generateOutline')">大纲生成</el-button>
        <el-button size="small" @click="callAi('continue')">续写</el-button>
        <el-button size="small" @click="callAi('polish')">改写润色</el-button>
        <el-button size="small" @click="callAi('expand')">扩写</el-button>
        <el-button size="small" @click="callAi('shorten')">缩写</el-button>
      </div>

      <el-input v-model="instruction" type="textarea" :rows="2" placeholder="额外指令（可选）" size="small" style="margin-top:8px;" />
    </div>

    <div v-if="streaming" class="ai-output">
      <div class="output-header">AI 生成中...</div>
      <div class="output-content" v-html="outputText"></div>
      <el-button size="small" type="success" @click="insertText" style="margin-top:8px;">插入到文档</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiApi } from '@/api/ai'
import { useStyleStore } from '@/stores/styles'

const props = defineProps<{ editorContent: string }>()
const emit = defineEmits(['insert'])

const styleStore = useStyleStore()
const styleId = ref<number | undefined>()
const instruction = ref('')
const streaming = ref(false)
const outputText = ref('')

onMounted(() => styleStore.fetch())

async function callAi(endpoint: string) {
  if (!props.editorContent.trim()) { ElMessage.warning('请先输入一些内容'); return }
  streaming.value = true; outputText.value = ''
  try {
    const fn = (aiApi as any)[endpoint]
    const response = await fn({
      content: props.editorContent,
      styleTemplateId: styleId.value,
      instruction: instruction.value || undefined,
    })
    const reader = response.data.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      outputText.value += decoder.decode(value, { stream: true })
    }
  } catch (e: any) { ElMessage.error('AI 调用失败') }
  streaming.value = false
}

function insertText() { emit('insert', outputText.value); outputText.value = '' }
</script>

<style scoped>
.ai-panel { width: 300px; border-left: 1px solid #e4e7ed; background: #fafafa; padding: 12px; overflow-y: auto; display: flex; flex-direction: column; }
.ai-panel-header { font-weight: 600; margin-bottom: 12px; }
.ai-btn-group { display: flex; flex-wrap: wrap; gap: 4px; }
.ai-output { margin-top: 12px; }
.output-header { font-size: 13px; color: #409eff; margin-bottom: 8px; }
.output-content { background: #fff; border: 1px solid #e4e7ed; border-radius: 4px; padding: 8px; min-height: 80px; max-height: 300px; overflow-y: auto; font-size: 14px; line-height: 1.7; }
</style>
```

- [ ] **Step 5: Write DocEditor.vue**

```vue
<template>
  <div class="doc-editor" v-if="doc">
    <div class="doc-header">
      <el-input v-model="title" size="large" placeholder="文档标题" @blur="autoSave" />
    </div>
    <div class="doc-body">
      <TiptapEditor v-model="content" />
      <AIPanel :editor-content="plainText" @insert="handleInsert" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useDocStore } from '@/stores/documents'
import TiptapEditor from './TiptapEditor.vue'
import AIPanel from './AIPanel.vue'

const props = defineProps<{ docId: number }>()
const store = useDocStore()

const title = ref('')
const content = ref('')

const doc = computed(() => store.currentDoc)

const plainText = computed(() => {
  const div = document.createElement('div')
  div.innerHTML = content.value
  return div.textContent || ''
})

onMounted(async () => {
  await store.fetchOne(props.docId)
  if (store.currentDoc) {
    title.value = store.currentDoc.title
    content.value = store.currentDoc.content || ''
  }
})

let saveTimer: ReturnType<typeof setTimeout>
function autoSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    store.save(props.docId, title.value, content.value)
  }, 1000)
}

watch([title, content], autoSave)

function handleInsert(text: string) { content.value = content.value + text }
</script>

<style scoped>
.doc-editor { height: 100%; display: flex; flex-direction: column; }
.doc-header { padding: 12px 16px; border-bottom: 1px solid #e4e7ed; }
.doc-body { flex: 1; display: flex; overflow: hidden; }
</style>
```

- [ ] **Step 6: Commit** — "feat: add Tiptap editor with AI writing panel"

---

## Phase 10: Frontend Style Management

### Task 10.1: Style store, API, and management views

**Files:**
- Create: `frontend/src/api/styles.ts`
- Create: `frontend/src/stores/styles.ts`
- Create: `frontend/src/views/StyleManageView.vue`
- Create: `frontend/src/components/styles/StyleList.vue`
- Create: `frontend/src/components/styles/StyleCreateDialog.vue`
- Create: `frontend/src/components/styles/StyleLearnDialog.vue`

- [ ] **Step 1: Write styles API and store**

api/styles.ts:
```typescript
import client from './client'
import type { StyleTemplate, LearningJob } from '@/types'

export const stylesApi = {
  list: () => client.get<StyleTemplate[]>('/styles'),
  create: (data: { name: string; systemPrompt: string; description?: string }) =>
    client.post<StyleTemplate>('/styles', data),
  remove: (id: number) => client.delete(`/styles/${id}`),
  learn: (id: number, data: { title: string; content: string }) =>
    client.post<LearningJob>(`/styles/${id}/learn`, data),
  getJobs: (id: number) => client.get<LearningJob[]>(`/styles/${id}/jobs`),
}
```

stores/styles.ts:
```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { StyleTemplate } from '@/types'
import { stylesApi } from '@/api/styles'

export const useStyleStore = defineStore('styles', () => {
  const styles = ref<StyleTemplate[]>([])

  async function fetch() {
    const res = await stylesApi.list()
    styles.value = res.data
  }

  async function create(name: string, systemPrompt: string, description?: string) {
    const res = await stylesApi.create({ name, systemPrompt, description })
    styles.value.unshift(res.data)
    return res.data
  }

  async function remove(id: number) {
    await stylesApi.remove(id)
    styles.value = styles.value.filter(s => s.id !== id)
  }

  return { styles, fetch, create, remove }
})
```

- [ ] **Step 2: Write StyleManageView.vue**

```vue
<template>
  <div class="style-page">
    <div class="style-header">
      <h2>写作风格管理</h2>
      <el-button type="primary" @click="showCreate = true">+ 新建风格</el-button>
    </div>
    <StyleList />
    <StyleCreateDialog v-model="showCreate" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import StyleList from '@/components/styles/StyleList.vue'
import StyleCreateDialog from '@/components/styles/StyleCreateDialog.vue'

const showCreate = ref(false)
</script>

<style scoped>
.style-page { padding: 24px; max-width: 900px; margin: 0 auto; }
.style-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
</style>
```

- [ ] **Step 3: Write StyleList.vue**

```vue
<template>
  <div v-if="store.styles.length === 0" class="empty">还没有写作风格，创建一个开始吧</div>
  <div v-else class="style-grid">
    <el-card v-for="s in store.styles" :key="s.id" class="style-card">
      <div class="card-header">
        <h3>{{ s.name }}</h3>
        <el-button type="danger" size="small" text @click="handleDelete(s.id)">删除</el-button>
      </div>
      <p class="card-desc">{{ s.description || '无描述' }}</p>
      <p class="card-prompt">{{ s.systemPrompt?.slice(0, 200) }}{{ s.systemPrompt?.length > 200 ? '...' : '' }}</p>
      <StyleLearnDialog :style-id="s.id" :style-name="s.name" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useStyleStore } from '@/stores/styles'
import { ElMessage } from 'element-plus'
import StyleLearnDialog from './StyleLearnDialog.vue'

const store = useStyleStore()
onMounted(() => store.fetch())

async function handleDelete(id: number) { await store.remove(id); ElMessage.success('已删除') }
</script>

<style scoped>
.empty { text-align: center; color: #909399; padding: 60px; }
.style-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header h3 { margin: 0; }
.card-desc { color: #909399; font-size: 13px; margin: 8px 0; }
.card-prompt { background: #f5f7fa; padding: 10px; border-radius: 4px; font-size: 13px; color: #606266; white-space: pre-wrap; }
</style>
```

- [ ] **Step 4: Write StyleCreateDialog.vue**

```vue
<template>
  <el-dialog :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" title="新建风格" width="500px">
    <el-form>
      <el-form-item label="名称"><el-input v-model="form.name" placeholder="例如：鲁迅风" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" placeholder="简短描述" /></el-form-item>
      <el-form-item label="System Prompt">
        <el-input v-model="form.systemPrompt" type="textarea" :rows="6"
          placeholder="直接填写写作风格指令，或留空后通过上传素材让 AI 自动生成" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="handleCreate">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useStyleStore } from '@/stores/styles'
import { ElMessage } from 'element-plus'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue'])
const store = useStyleStore()
const form = reactive({ name: '', systemPrompt: '', description: '' })

async function handleCreate() {
  if (!form.name.trim() || !form.systemPrompt.trim()) { ElMessage.warning('名称和 System Prompt 为必填'); return }
  await store.create(form.name, form.systemPrompt, form.description)
  ElMessage.success('风格创建成功')
  form.name = ''; form.systemPrompt = ''; form.description = ''
  emit('update:modelValue', false)
}
</script>
```

- [ ] **Step 5: Write StyleLearnDialog.vue**

```vue
<template>
  <el-dialog v-model="visible" :title="'上传素材学习 — ' + styleName" width="600px">
    <el-form>
      <el-form-item label="素材标题"><el-input v-model="title" placeholder="例如：《百年孤独》节选" /></el-form-item>
      <el-form-item label="文本内容">
        <el-input v-model="content" type="textarea" :rows="10" placeholder="粘贴小说原文或文本片段..." />
      </el-form-item>
    </el-form>
    <div v-if="job" class="job-status">
      <el-tag v-if="job.status === 'PENDING'" type="info">等待中...</el-tag>
      <el-tag v-else-if="job.status === 'PROCESSING'" type="warning">AI 正在分析...</el-tag>
      <el-tag v-else-if="job.status === 'COMPLETED'" type="success">学习完成！</el-tag>
      <el-tag v-else-if="job.status === 'FAILED'" type="danger">失败：{{ job.errorMsg }}</el-tag>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="handleLearn">开始学习</el-button>
    </template>
  </el-dialog>

  <el-button size="small" @click="visible = true" style="margin-top: 8px;">📖 上传素材学习</el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { stylesApi } from '@/api/styles'
import type { LearningJob } from '@/types'

const props = defineProps<{ styleId: number; styleName: string }>()

const visible = ref(false); const loading = ref(false)
const title = ref(''); const content = ref('')
const job = ref<LearningJob | null>(null)

async function handleLearn() {
  if (!title.value.trim() || !content.value.trim()) { ElMessage.warning('请填写标题和内容'); return }
  loading.value = true
  try {
    const res = await stylesApi.learn(props.styleId, { title: title.value, content: content.value })
    job.value = res.data
    const timer = setInterval(async () => {
      const jobs = await stylesApi.getJobs(props.styleId)
      const latest = jobs.data[0]
      if (latest) {
        job.value = latest
        if (latest.status === 'COMPLETED' || latest.status === 'FAILED') {
          clearInterval(timer)
          ElMessage.success('风格学习完成！')
        }
      }
    }, 2000)
  } catch (e: any) { ElMessage.error('学习请求失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.job-status { margin-top: 12px; }
</style>
```

- [ ] **Step 6: Commit** — "feat: add style management and learning UI"

---

## Phase 11: Integration Verification

### Task 11.1: Full stack integration test

- [ ] **Step 1: Start backend and test auth endpoints**

Start backend: `cd backend && mvn spring-boot:run`

Test register:
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@test.com","password":"123456"}'
```

Expected: 200 with accessToken, refreshToken, user.

Test login:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"test@test.com","password":"123456"}'
```

Expected: 200 with tokens.

- [ ] **Step 2: Test project and document CRUD**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"account":"test@test.com","password":"123456"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# Create project
curl -X POST http://localhost:8080/api/projects -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"name":"小说创作"}'

# Create document
curl -X POST http://localhost:8080/api/projects/1/docs -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"title":"第一章"}'

# List documents
curl http://localhost:8080/api/projects/1/docs -H "Authorization: Bearer $TOKEN"
```

Expected: All return success.

- [ ] **Step 3: Start frontend and test full flow**

Start: `cd frontend && npm run dev`

Manual test in browser at http://localhost:3000:
1. Register new account
2. Login
3. Create project "小说创作"
4. Create document
5. Type some content in editor
6. Open AI panel, select "续写"
7. Verify AI streaming response
8. Go to /styles → Create style
9. Upload material for learning
10. Verify job completes

- [ ] **Step 4: Fix any issues and final commit**

```bash
git add . && git commit -m "fix: integration fixes and final polish"
```

---

## Plan Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| 1 | 1.1-1.2 | Project scaffolding (Maven + Vite) |
| 2 | 2.1-2.2 | Database schema + JPA entities |
| 3 | 3.1-3.3 | JWT auth (provider + filter + service) |
| 4 | 4.1-4.2 | Project & Document CRUD |
| 5 | 5.1 | AI writing endpoints (SSE streaming) |
| 6 | 6.1 | Style learning system |
| 7 | 7.1-7.2 | Frontend auth + routing |
| 8 | 8.1 | Dashboard layout + sidebar |
| 9 | 9.1-9.2 | Tiptap editor + AI panel |
| 10 | 10.1 | Style management UI |
| 11 | 11.1 | Integration verification |
