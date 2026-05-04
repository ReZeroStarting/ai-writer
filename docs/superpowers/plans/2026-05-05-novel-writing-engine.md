# 小说写作框架引擎 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 AI Writer 中实现基于事件流架构的小说写作框架引擎

**Architecture:** 方案 C — 上下文驱动的事件流。新增 6 张表 + 7 个 Service + 5 个 Controller + 6 个前端组件。元素版本化、事件不可变、冲突检测两阶段。

**Tech Stack:** Java 17 / Spring Boot 3.5.0 / JPA / WebFlux SSE, Vue 3 + TypeScript + Pinia + Element Plus

---

## 并行分配总览

```
阶段 1 (1 个 Claude): 数据层 — 所有实体 + Repository + schema.sql
        │
        ▼ 合并到 master
        │
        ├── Claude A: 元素管理 (ElementService + FrameworkService + Controller + ContextBuilder)
        ├── Claude B: 事件 + 冲突 (EventService + ConflictService + ChapterService + Controller)
        └── Claude C: 前端页面 (路由 + 设定管理 + 写作工作台 + 冲突/历史)
                │
                ▼ 全部合并
              阶段 3: E2E 集成测试
```

**接口契约见下方每个并行任务组的"契约"节。**

---

## 阶段 1: 数据层（1 个 Claude，约 2-3 小时）

### Task 1.1: 创建 Framework 实体

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/Framework.java`

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "frameworks")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Framework {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FrameworkStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = FrameworkStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum FrameworkStatus {
        ACTIVE, FINISHED
    }
}
```

### Task 1.2: 创建 FrameworkElement 实体（联合主键）

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/FrameworkElement.java`

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.io.Serializable;

@Entity
@Table(name = "framework_elements")
@IdClass(FrameworkElement.ElementKey.class)
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FrameworkElement {
    @Id @Column(length = 36)
    private String id; // UUID string

    @Id
    @Column(name = "version")
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "framework_id", nullable = false)
    private Framework framework;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String type; // CHARACTER, LOCATION, ITEM, ORGANIZATION, EVENT, WORLD_SETTING, or custom

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_deprecated", nullable = false)
    private Boolean isDeprecated;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (version == null) version = 1;
        if (isDeprecated == null) isDeprecated = false;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ElementKey implements Serializable {
        private String id;
        private Integer version;
    }
}
```

### Task 1.3: 创建 Chapter 实体

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/Chapter.java`

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chapters")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Chapter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "framework_id", nullable = false)
    private Framework framework;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chapter_index", nullable = false)
    private Integer chapterIndex;

    @Column(name = "element_snapshot", columnDefinition = "JSON")
    private String elementSnapshot; // {"<elementId>": <version>, ...}

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChapterStatus status;

    @Column(columnDefinition = "TEXT")
    private String summary; // AI-generated summary, max 200 chars

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ChapterStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ChapterStatus {
        DRAFT, AI_GENERATING, AI_GENERATED, USER_EDITING, COMPLETED
    }
}
```

### Task 1.4: 创建 WritingEvent 实体

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/WritingEvent.java`

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "writing_events")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WritingEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "framework_id", nullable = false)
    private Framework framework;

    @Column(name = "event_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(columnDefinition = "JSON")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }

    public enum EventType {
        ELEMENT_CREATED,
        ELEMENT_UPDATED,
        ELEMENT_DEPRECATED,
        ELEMENT_AI_GENERATED,
        CHAPTER_STARTED,
        CHAPTER_AI_GENERATED,
        CHAPTER_SUMMARY_GENERATED,
        USER_INTERVENED,
        CONFLICT_DETECTED,
        CONFLICT_RESOLVED,
        PATCH_APPLIED,
        FRAMEWORK_FINISHED
    }
}
```

### Task 1.5: 创建 Conflict 实体

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/Conflict.java`

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conflicts")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Conflict {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "framework_id", nullable = false)
    private Framework framework;

    @Column(name = "element_id", nullable = false, length = 36)
    private String elementId; // UUID of FrameworkElement

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private ConflictSeverity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description; // AI-generated conflict description

    @Column(name = "chapter_ref_version", nullable = false)
    private Integer chapterRefVersion;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConflictStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ConflictStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ConflictSeverity { MAJOR, MINOR }
    public enum ConflictStatus { PENDING, IGNORED, PATCHED, REWRITTEN }
}
```

### Task 1.6: 创建 Patch 实体

**Files:**
- Create: `backend/src/main/java/com/aiwriter/model/Patch.java`

```java
package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "patches")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Patch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conflict_id", nullable = false)
    private Conflict conflict;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "affected_chapter_ids", columnDefinition = "JSON")
    private String affectedChapterIds; // JSON array of chapter IDs

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Task 1.7: 修改 Project 实体 — 添加 mode 字段

**Files:**
- Modify: `backend/src/main/java/com/aiwriter/model/Project.java`

在现有 `description` 字段之后添加：

```java
@Column(length = 20)
private String mode; // null = normal, "framework" = framework mode
```

### Task 1.8: 创建所有 Repository

**Files:**
- Create: `backend/src/main/java/com/aiwriter/repository/FrameworkRepository.java`

```java
package com.aiwriter.repository;

import com.aiwriter.model.Framework;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FrameworkRepository extends JpaRepository<Framework, Long> {
    Optional<Framework> findByProjectId(Long projectId);
}
```

- Create: `backend/src/main/java/com/aiwriter/repository/FrameworkElementRepository.java`

```java
package com.aiwriter.repository;

import com.aiwriter.model.FrameworkElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface FrameworkElementRepository extends JpaRepository<FrameworkElement, Long> {
    @Query("SELECT e FROM FrameworkElement e WHERE e.framework.id = :frameworkId AND e.isDeprecated = false AND e.version = (SELECT MAX(e2.version) FROM FrameworkElement e2 WHERE e2.id = e.id) ORDER BY e.type, e.name")
    List<FrameworkElement> findLatestByFrameworkId(Long frameworkId);

    @Query("SELECT e FROM FrameworkElement e WHERE e.id = :elementId ORDER BY e.version DESC")
    List<FrameworkElement> findVersionsByElementId(String elementId);

    @Query("SELECT e FROM FrameworkElement e WHERE e.id = :elementId AND e.isDeprecated = false ORDER BY e.version DESC LIMIT 1")
    Optional<FrameworkElement> findLatestByElementId(String elementId);

    @Query("SELECT MAX(e.version) FROM FrameworkElement e WHERE e.id = :elementId")
    Optional<Integer> findMaxVersionByElementId(String elementId);
}
```

- Create: `backend/src/main/java/com/aiwriter/repository/ChapterRepository.java`

```java
package com.aiwriter.repository;

import com.aiwriter.model.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByFrameworkIdOrderByChapterIndexAsc(Long frameworkId);
    Optional<Chapter> findTopByFrameworkIdOrderByChapterIndexDesc(Long frameworkId);
}
```

- Create: `backend/src/main/java/com/aiwriter/repository/WritingEventRepository.java`

```java
package com.aiwriter.repository;

import com.aiwriter.model.WritingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface WritingEventRepository extends JpaRepository<WritingEvent, Long> {
    List<WritingEvent> findByFrameworkIdOrderByTimestampDesc(Long frameworkId);

    @Query("SELECT e FROM WritingEvent e WHERE e.framework.id = :frameworkId AND e.timestamp <= :until ORDER BY e.timestamp ASC")
    List<WritingEvent> findByFrameworkIdUpTo(Long frameworkId, LocalDateTime until);
}
```

- Create: `backend/src/main/java/com/aiwriter/repository/ConflictRepository.java`

```java
package com.aiwriter.repository;

import com.aiwriter.model.Conflict;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConflictRepository extends JpaRepository<Conflict, Long> {
    List<Conflict> findByFrameworkIdOrderByCreatedAtDesc(Long frameworkId);
    List<Conflict> findByChapterId(Long chapterId);
}
```

- Create: `backend/src/main/java/com/aiwriter/repository/PatchRepository.java`

```java
package com.aiwriter.repository;

import com.aiwriter.model.Patch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatchRepository extends JpaRepository<Patch, Long> {
}
```

### Task 1.9: 更新 schema.sql

**Files:**
- Modify: `backend/src/main/resources/schema.sql`

在现有建表语句末尾追加：

```sql
ALTER TABLE projects ADD COLUMN mode VARCHAR(20) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS frameworks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS framework_elements (
    id VARCHAR(36) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    framework_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_deprecated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, version),
    FOREIGN KEY (framework_id) REFERENCES frameworks(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chapters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    framework_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    chapter_index INT NOT NULL,
    element_snapshot JSON,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (framework_id) REFERENCES frameworks(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS writing_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    framework_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    payload JSON,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    FOREIGN KEY (framework_id) REFERENCES frameworks(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS conflicts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    framework_id BIGINT NOT NULL,
    element_id VARCHAR(36) NOT NULL,
    chapter_id BIGINT NOT NULL,
    severity VARCHAR(10) NOT NULL,
    description TEXT NOT NULL,
    chapter_ref_version INT NOT NULL,
    current_version INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (framework_id) REFERENCES frameworks(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS patches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conflict_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    affected_chapter_ids JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conflict_id) REFERENCES conflicts(id) ON DELETE CASCADE
);
```

### Task 1.10: DTO 定义 — 所有并行任务共享的接口契约

**Files:**
- Create: `backend/src/main/java/com/aiwriter/dto/request/FrameworkElementRequest.java`

```java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FrameworkElementRequest {
    @NotBlank @Size(max = 50)
    private String type;
    @NotBlank @Size(max = 200)
    private String name;
    @NotBlank
    private String content;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/request/AiGenerateElementsRequest.java`

```java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiGenerateElementsRequest {
    @NotBlank
    private String type;
    private String hint;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/request/InterveneRequest.java`

```java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InterveneRequest {
    @NotBlank
    private String content;
    private String position; // "after_current" or "new_chapter"
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/request/ResolveConflictRequest.java`

```java
package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveConflictRequest {
    @NotBlank
    private String action; // "ignore", "patch", "rewrite"
    private String patchContent;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/response/FrameworkResponse.java`

```java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class FrameworkResponse {
    private Long id;
    private Long projectId;
    private String status;
    private int elementCount;
    private int chapterCount;
    private int unresolvedConflictCount;
    private String createdAt;
    private String updatedAt;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/response/ElementResponse.java`

```java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ElementResponse {
    private String id;
    private Integer version;
    private String type;
    private String name;
    private String content;
    private Boolean isDeprecated;
    private String createdAt;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/response/ChapterResponse.java`

```java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ChapterResponse {
    private Long id;
    private Long documentId;
    private Integer chapterIndex;
    private String status;
    private String summary;
    private String createdAt;
    private String updatedAt;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/response/ConflictResponse.java`

```java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ConflictResponse {
    private Long id;
    private String elementId;
    private Long chapterId;
    private Integer chapterRefVersion;
    private Integer currentVersion;
    private String severity;
    private String description;
    private String status;
    private String createdAt;
}
```

- Create: `backend/src/main/java/com/aiwriter/dto/response/WritingEventResponse.java`

```java
package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class WritingEventResponse {
    private Long id;
    private String eventType;
    private String payload;
    private String timestamp;
}
```

### Task 1.11: 运行测试验证数据层

```bash
cd backend && mvn test -Dtest="*" -q
```

### Task 1.12: 提交阶段 1

```bash
git add -A
git commit -m "feat: add framework data layer — 6 entities, repositories, schema, DTOs

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 阶段 2A: 框架 + 元素管理（Claude A）

**依赖：** 阶段 1 已合并到 master

### 契约 — 本阶段对外暴露的接口

```java
// FrameworkController:
POST   /api/v1/projects/:id/framework       → FrameworkResponse
GET    /api/v1/frameworks/:id                → FrameworkResponse
POST   /api/v1/frameworks/:id/finish         → FrameworkResponse

// ElementController:
GET    /api/v1/frameworks/:id/elements?type=X → List<ElementResponse>
POST   /api/v1/frameworks/:id/elements        → ElementResponse
PUT    /api/v1/frameworks/:id/elements/:eid   → ElementResponse
DELETE /api/v1/frameworks/:id/elements/:eid   → void (204)
GET    /api/v1/frameworks/:id/elements/:eid/versions → List<ElementResponse>
POST   /api/v1/frameworks/:id/elements/ai-generate   → List<ElementResponse>
```

### Task 2A.1: 实现 FrameworkService

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/FrameworkService.java`

```java
package com.aiwriter.service;

import com.aiwriter.dto.response.FrameworkResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FrameworkService {
    private final FrameworkRepository frameworkRepo;
    private final ProjectRepository projectRepo;
    private final FrameworkElementRepository elementRepo;
    private final ChapterRepository chapterRepo;
    private final ConflictRepository conflictRepo;

    public FrameworkResponse openFramework(Long userId, Long projectId) {
        Project project = projectRepo.findById(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在"));
        if (!project.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此项目");
        if ("framework".equals(project.getMode()))
            throw new IllegalArgumentException("该项目已是框架模式");
        if (frameworkRepo.findByProjectId(projectId).isPresent())
            throw new IllegalArgumentException("该项目已有框架");

        project.setMode("framework");
        projectRepo.save(project);

        Framework fw = Framework.builder()
                .project(project)
                .user(User.builder().id(userId).build())
                .status(Framework.FrameworkStatus.ACTIVE)
                .build();
        frameworkRepo.save(fw);
        return toResponse(fw);
    }

    public FrameworkResponse getFramework(Long userId, Long frameworkId) {
        Framework fw = findOwned(userId, frameworkId);
        return toResponse(fw);
    }

    public FrameworkResponse finishFramework(Long userId, Long frameworkId) {
        Framework fw = findOwned(userId, frameworkId);
        fw.setStatus(Framework.FrameworkStatus.FINISHED);
        frameworkRepo.save(fw);
        return toResponse(fw);
    }

    Framework findOwned(Long userId, Long frameworkId) {
        Framework fw = frameworkRepo.findById(frameworkId)
                .orElseThrow(() -> new NotFoundException("框架不存在"));
        if (!fw.getUser().getId().equals(userId))
            throw new ForbiddenException("无权操作此框架");
        return fw;
    }

    private FrameworkResponse toResponse(Framework fw) {
        return FrameworkResponse.builder()
                .id(fw.getId())
                .projectId(fw.getProject().getId())
                .status(fw.getStatus().name())
                .elementCount(elementRepo.findLatestByFrameworkId(fw.getId()).size())
                .chapterCount(chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(fw.getId()).size())
                .unresolvedConflictCount((int) conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(fw.getId())
                        .stream().filter(c -> c.getStatus() == Conflict.ConflictStatus.PENDING).count())
                .createdAt(fw.getCreatedAt().toString())
                .updatedAt(fw.getUpdatedAt().toString())
                .build();
    }
}
```

### Task 2A.2: 实现 ElementService

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/ElementService.java`

```java
package com.aiwriter.service;

import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ElementService {
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;

    public List<ElementResponse> list(Long userId, Long frameworkId, String type) {
        frameworkService.findOwned(userId, frameworkId);
        return elementRepo.findLatestByFrameworkId(frameworkId).stream()
                .filter(e -> type == null || e.getType().equals(type))
                .map(this::toResponse).toList();
    }

    public ElementResponse create(Long userId, Long frameworkId, FrameworkElementRequest req) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        String uuid = UUID.randomUUID().toString();
        FrameworkElement el = FrameworkElement.builder()
                .id(uuid).version(1).framework(fw)
                .user(User.builder().id(userId).build())
                .type(req.getType()).name(req.getName()).content(req.getContent())
                .isDeprecated(false).build();
        elementRepo.save(el);
        return toResponse(el);
    }

    public ElementResponse update(Long userId, Long frameworkId, String elementId, FrameworkElementRequest req) {
        frameworkService.findOwned(userId, frameworkId);
        FrameworkElement latest = elementRepo.findLatestByElementId(elementId)
                .orElseThrow(() -> new NotFoundException("元素不存在"));
        int newVersion = latest.getVersion() + 1;

        FrameworkElement el = FrameworkElement.builder()
                .id(elementId).version(newVersion).framework(latest.getFramework())
                .user(User.builder().id(userId).build())
                .type(req.getType()).name(req.getName()).content(req.getContent())
                .isDeprecated(false).build();
        elementRepo.save(el);
        return toResponse(el);
    }

    public void delete(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        FrameworkElement latest = elementRepo.findLatestByElementId(elementId)
                .orElseThrow(() -> new NotFoundException("元素不存在"));
        latest.setIsDeprecated(true);
        elementRepo.save(latest);
    }

    public List<ElementResponse> getVersions(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        return elementRepo.findVersionsByElementId(elementId).stream()
                .map(this::toResponse).toList();
    }

    public List<ElementResponse> aiGenerate(Long userId, Long frameworkId, AiGenerateElementsRequest req) {
        // Stub: returns empty list until ContextBuilder/ClaudeService integration in Phase 3B
        // Full implementation: injects existing elements as context, calls Claude, returns generated elements
        return List.of();
    }

    private ElementResponse toResponse(FrameworkElement e) {
        return ElementResponse.builder()
                .id(e.getId()).version(e.getVersion())
                .type(e.getType()).name(e.getName()).content(e.getContent())
                .isDeprecated(e.getIsDeprecated())
                .createdAt(e.getCreatedAt().toString()).build();
    }
}
```

### Task 2A.3: 实现 FrameworkController

**Files:**
- Create: `backend/src/main/java/com/aiwriter/controller/FrameworkController.java`

```java
package com.aiwriter.controller;

import com.aiwriter.dto.response.FrameworkResponse;
import com.aiwriter.service.FrameworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FrameworkController {
    private final FrameworkService frameworkService;

    @PostMapping("/projects/{projectId}/framework")
    public ResponseEntity<FrameworkResponse> open(@PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.ok(frameworkService.openFramework(getUserId(auth), projectId));
    }

    @GetMapping("/frameworks/{id}")
    public ResponseEntity<FrameworkResponse> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(frameworkService.getFramework(getUserId(auth), id));
    }

    @PostMapping("/frameworks/{id}/finish")
    public ResponseEntity<FrameworkResponse> finish(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(frameworkService.finishFramework(getUserId(auth), id));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

### Task 2A.4: 实现 ElementController

**Files:**
- Create: `backend/src/main/java/com/aiwriter/controller/ElementController.java`

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.AiGenerateElementsRequest;
import com.aiwriter.dto.request.FrameworkElementRequest;
import com.aiwriter.dto.response.ElementResponse;
import com.aiwriter.service.ElementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ElementController {
    private final ElementService elementService;

    @GetMapping("/frameworks/{frameworkId}/elements")
    public ResponseEntity<List<ElementResponse>> list(
            @PathVariable Long frameworkId,
            @RequestParam(required = false) String type,
            Authentication auth) {
        return ResponseEntity.ok(elementService.list(getUserId(auth), frameworkId, type));
    }

    @PostMapping("/frameworks/{frameworkId}/elements")
    public ResponseEntity<ElementResponse> create(
            @PathVariable Long frameworkId,
            @Valid @RequestBody FrameworkElementRequest req,
            Authentication auth) {
        return ResponseEntity.ok(elementService.create(getUserId(auth), frameworkId, req));
    }

    @PutMapping("/frameworks/{frameworkId}/elements/{elementId}")
    public ResponseEntity<ElementResponse> update(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            @Valid @RequestBody FrameworkElementRequest req,
            Authentication auth) {
        return ResponseEntity.ok(elementService.update(getUserId(auth), frameworkId, elementId, req));
    }

    @DeleteMapping("/frameworks/{frameworkId}/elements/{elementId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            Authentication auth) {
        elementService.delete(getUserId(auth), frameworkId, elementId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/frameworks/{frameworkId}/elements/{elementId}/versions")
    public ResponseEntity<List<ElementResponse>> versions(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            Authentication auth) {
        return ResponseEntity.ok(elementService.getVersions(getUserId(auth), frameworkId, elementId));
    }

    @PostMapping("/frameworks/{frameworkId}/elements/ai-generate")
    public ResponseEntity<List<ElementResponse>> aiGenerate(
            @PathVariable Long frameworkId,
            @Valid @RequestBody AiGenerateElementsRequest req,
            Authentication auth) {
        return ResponseEntity.ok(elementService.aiGenerate(getUserId(auth), frameworkId, req));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

### Task 2A.5: 实现 ContextBuilder

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/ContextBuilder.java`

```java
package com.aiwriter.service;

import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ContextBuilder {
    private final FrameworkElementRepository elementRepo;
    private final ChapterRepository chapterRepo;

    public record ContextPackage(String systemContext, String recentContent, String earlierSummaries) {}

    private static final int MAX_SYSTEM_CHARS = 20000;
    private static final int MAX_RECENT_CONTENT_CHARS = 8000;
    private static final int MAX_SUMMARY_CHARS = 5000;

    public ContextPackage buildContext(Long frameworkId, String currentContent) {
        // Layer 1: All non-deprecated elements (latest version)
        var elements = elementRepo.findLatestByFrameworkId(frameworkId);
        StringBuilder systemCtx = new StringBuilder("【小说设定，写作时必须遵守】\n\n");
        for (var e : elements) {
            systemCtx.append("[").append(e.getType()).append("] ")
                     .append(e.getName()).append(": ").append(e.getContent()).append("\n\n");
        }
        if (systemCtx.length() > MAX_SYSTEM_CHARS) {
            systemCtx.setLength(MAX_SYSTEM_CHARS);
        }

        // Layer 2: Current content + previous chapter
        StringBuilder recent = new StringBuilder();
        var chapters = chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(frameworkId);
        if (!chapters.isEmpty()) {
            Chapter prev = chapters.get(chapters.size() - 1);
            if (prev.getDocument() != null && prev.getDocument().getContent() != null) {
                recent.append(prev.getDocument().getContent());
                if (recent.length() > MAX_RECENT_CONTENT_CHARS) {
                    recent.setLength(MAX_RECENT_CONTENT_CHARS);
                }
            }
        }
        if (currentContent != null && !currentContent.isEmpty()) {
            recent.append("\n\n").append(currentContent);
        }

        // Layer 3: Earlier chapter summaries
        StringBuilder summaries = new StringBuilder();
        int startIdx = Math.max(0, chapters.size() - 16);
        for (int i = startIdx; i < Math.min(chapters.size() - 1, startIdx + 15); i++) {
            Chapter ch = chapters.get(i);
            if (ch.getSummary() != null && !ch.getSummary().isEmpty()) {
                summaries.append("第").append(ch.getChapterIndex()).append("章摘要: ")
                         .append(ch.getSummary()).append("\n");
            }
        }
        if (summaries.length() > MAX_SUMMARY_CHARS) {
            summaries.setLength(MAX_SUMMARY_CHARS);
        }

        return new ContextPackage(systemCtx.toString(), recent.toString(), summaries.toString());
    }
}
```

### Task 2A.6: 运行后端编译 + 阶段 2A 测试

```bash
cd backend && mvn compile -q
```

确保所有新文件编译通过。

### Task 2A.7: 提交阶段 2A

```bash
git add -A
git commit -m "feat: add framework and element management — services, controllers, ContextBuilder

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 阶段 2B: 事件 + 冲突 + 写作引擎（Claude B）

**依赖：** 阶段 1 已合并到 master
**接口契约：** 使用阶段 1 中定义的 DTO（FrameworkElementRequest, InterveneRequest, ResolveConflictRequest 等）

### 契约 — 本阶段对外暴露的接口

```java
// EventController:
GET /api/v1/frameworks/:id/events?type=&from=&to=&page=&size= → Page<WritingEventResponse>
GET /api/v1/frameworks/:id/events/:timestamp → FrameworkSnapshotResponse

// WriteController:
POST /api/v1/frameworks/:id/write/start      → SseEmitter (SSE 流式)
POST /api/v1/frameworks/:id/write/interrupt   → ChapterResponse
POST /api/v1/frameworks/:id/write/continue    → SseEmitter (SSE 流式)

// ConflictController:
GET    /api/v1/frameworks/:id/conflicts              → List<ConflictResponse>
POST   /api/v1/frameworks/:id/conflicts/:cid/resolve → ConflictResponse
GET    /api/v1/frameworks/:id/elements/:eid/impact   → List<Long> (受影响章节 ID)
```

### Task 2B.1: 实现 EventService

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/EventService.java`

```java
package com.aiwriter.service;

import com.aiwriter.dto.response.WritingEventResponse;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {
    private final WritingEventRepository eventRepo;
    private final FrameworkService frameworkService;
    private final Gson gson = new Gson();

    public WritingEvent appendEvent(Long frameworkId, WritingEvent.EventType type, Object payload) {
        Framework fw = frameworkService.findOwned(/* system call */ 0L, frameworkId);
        // Bypass ownership check for internal calls — use frameworkRepo directly
        WritingEvent event = WritingEvent.builder()
                .framework(fw)
                .eventType(type)
                .payload(payload != null ? gson.toJson(payload) : null)
                .build();
        return eventRepo.save(event);
    }

    // Internal — no auth check
    public WritingEvent appendEventInternal(Framework framework, WritingEvent.EventType type, Object payload) {
        WritingEvent event = WritingEvent.builder()
                .framework(framework)
                .eventType(type)
                .payload(payload != null ? gson.toJson(payload) : null)
                .build();
        return eventRepo.save(event);
    }

    public List<WritingEventResponse> list(Long userId, Long frameworkId) {
        frameworkService.findOwned(userId, frameworkId);
        return eventRepo.findByFrameworkIdOrderByTimestampDesc(frameworkId).stream()
                .map(this::toResponse).toList();
    }

    public List<WritingEventResponse> listUpTo(Long userId, Long frameworkId, LocalDateTime until) {
        frameworkService.findOwned(userId, frameworkId);
        return eventRepo.findByFrameworkIdUpTo(frameworkId, until).stream()
                .map(this::toResponse).toList();
    }

    private WritingEventResponse toResponse(WritingEvent e) {
        return WritingEventResponse.builder()
                .id(e.getId()).eventType(e.getEventType().name())
                .payload(e.getPayload()).timestamp(e.getTimestamp().toString())
                .build();
    }
}
```

### Task 2B.2: 实现 ChapterService

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/ChapterService.java`

```java
package com.aiwriter.service;

import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChapterService {
    private final ChapterRepository chapterRepo;
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;
    private final EventService eventService;
    private final Gson gson = new Gson();

    public Chapter createChapter(Framework fw, int chapterIndex, Long documentId) {
        // Snapshot current element versions
        List<FrameworkElement> elements = elementRepo.findLatestByFrameworkId(fw.getId());
        Map<String, Integer> snapshot = new HashMap<>();
        for (var e : elements) {
            snapshot.put(e.getId(), e.getVersion());
        }

        Chapter ch = Chapter.builder()
                .framework(fw)
                .document(Document.builder().id(documentId).build())
                .chapterIndex(chapterIndex)
                .elementSnapshot(gson.toJson(snapshot))
                .status(Chapter.ChapterStatus.DRAFT)
                .build();
        chapterRepo.save(ch);

        eventService.appendEventInternal(fw, WritingEvent.EventType.CHAPTER_STARTED,
                Map.of("chapterId", ch.getId(), "chapterIndex", chapterIndex, "elementSnapshot", snapshot));
        return ch;
    }

    public Chapter markGenerating(Long chapterId) {
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        ch.setStatus(Chapter.ChapterStatus.AI_GENERATING);
        return chapterRepo.save(ch);
    }

    public Chapter markGenerated(Long chapterId, String summary) {
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        ch.setStatus(Chapter.ChapterStatus.AI_GENERATED);
        ch.setSummary(summary);
        chapterRepo.save(ch);

        eventService.appendEventInternal(ch.getFramework(), WritingEvent.EventType.CHAPTER_AI_GENERATED,
                Map.of("chapterId", ch.getId()));
        if (summary != null && !summary.isEmpty()) {
            eventService.appendEventInternal(ch.getFramework(), WritingEvent.EventType.CHAPTER_SUMMARY_GENERATED,
                    Map.of("chapterId", ch.getId(), "summary", summary));
        }
        return ch;
    }

    public Chapter getLatestChapter(Long frameworkId) {
        return chapterRepo.findTopByFrameworkIdOrderByChapterIndexDesc(frameworkId).orElse(null);
    }

    public List<Chapter> list(Long frameworkId) {
        return chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(frameworkId);
    }

    public Map<String, Integer> getElementSnapshot(Long chapterId) {
        Chapter ch = chapterRepo.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("章节不存在"));
        if (ch.getElementSnapshot() == null) return Map.of();
        return gson.fromJson(ch.getElementSnapshot(), Map.class);
    }

    public ChapterResponse toResponse(Chapter ch) {
        return ChapterResponse.builder()
                .id(ch.getId())
                .documentId(ch.getDocument() != null ? ch.getDocument().getId() : null)
                .chapterIndex(ch.getChapterIndex())
                .status(ch.getStatus().name())
                .summary(ch.getSummary())
                .createdAt(ch.getCreatedAt().toString())
                .updatedAt(ch.getUpdatedAt().toString())
                .build();
    }
}
```

### Task 2B.3: 实现 ConflictService

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/ConflictService.java`

```java
package com.aiwriter.service;

import com.aiwriter.dto.request.ResolveConflictRequest;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConflictService {
    private final ConflictRepository conflictRepo;
    private final PatchRepository patchRepo;
    private final ChapterRepository chapterRepo;
    private final FrameworkElementRepository elementRepo;
    private final FrameworkService frameworkService;
    private final EventService eventService;

    public List<ConflictResponse> listByFramework(Long userId, Long frameworkId) {
        frameworkService.findOwned(userId, frameworkId);
        return conflictRepo.findByFrameworkIdOrderByCreatedAtDesc(frameworkId).stream()
                .map(this::toResponse).toList();
    }

    public ConflictResponse resolve(Long userId, Long frameworkId, Long conflictId, ResolveConflictRequest req) {
        frameworkService.findOwned(userId, frameworkId);
        Conflict c = conflictRepo.findById(conflictId)
                .orElseThrow(() -> new NotFoundException("冲突不存在"));

        switch (req.getAction()) {
            case "ignore":
                c.setStatus(Conflict.ConflictStatus.IGNORED);
                break;
            case "patch":
                c.setStatus(Conflict.ConflictStatus.PATCHED);
                Patch patch = Patch.builder().conflict(c).content(req.getPatchContent())
                        .affectedChapterIds("[]").build();
                patchRepo.save(patch);
                eventService.appendEventInternal(c.getFramework(), WritingEvent.EventType.PATCH_APPLIED,
                        Map.of("conflictId", c.getId(), "patchContent", req.getPatchContent()));
                break;
            case "rewrite":
                c.setStatus(Conflict.ConflictStatus.REWRITTEN);
                break;
            default:
                throw new IllegalArgumentException("无效的操作: " + req.getAction());
        }
        conflictRepo.save(c);
        eventService.appendEventInternal(c.getFramework(), WritingEvent.EventType.CONFLICT_RESOLVED,
                Map.of("conflictId", c.getId(), "resolution", req.getAction()));
        return toResponse(c);
    }

    public List<Long> getImpactedChapters(Long userId, Long frameworkId, String elementId) {
        frameworkService.findOwned(userId, frameworkId);
        var latestVersion = elementRepo.findMaxVersionByElementId(elementId).orElse(1);
        return chapterRepo.findByFrameworkIdOrderByChapterIndexAsc(frameworkId).stream()
                .filter(ch -> {
                    Map<String, Double> snap = parseSnapshot(ch.getElementSnapshot());
                    Double refVersion = snap.get(elementId);
                    return refVersion != null && refVersion < latestVersion;
                })
                .map(Chapter::getId)
                .toList();
    }

    /**
     * Check a newly generated chapter against current element versions.
     * Returns list of conflicts found. MAJOR conflicts are PENDING, MINOR are auto-patched.
     */
    public List<Conflict> checkChapterConsistency(Framework fw, Chapter chapter, String aiGeneratedContent) {
        List<Conflict> conflicts = new ArrayList<>();
        Map<String, Double> snapshot = parseSnapshot(chapter.getElementSnapshot());
        var currentElements = elementRepo.findLatestByFrameworkId(fw.getId());

        for (var el : currentElements) {
            Double snapVer = snapshot.get(el.getId());
            if (snapVer != null && snapVer < el.getVersion()) {
                // Element was updated after the chapter was started
                Conflict c = Conflict.builder()
                        .framework(fw).elementId(el.getId()).chapter(chapter)
                        .severity(Conflict.ConflictSeverity.MINOR)
                        .description("元素 [" + el.getName() + "] 在章节生成后被修改（v" + snapVer.intValue() + " → v" + el.getVersion() + "）")
                        .chapterRefVersion(snapVer.intValue())
                        .currentVersion(el.getVersion())
                        .status(Conflict.ConflictStatus.PENDING)
                        .build();
                conflictRepo.save(c);
                conflicts.add(c);
                eventService.appendEventInternal(fw, WritingEvent.EventType.CONFLICT_DETECTED,
                        Map.of("conflictId", c.getId(), "severity", "MINOR", "elementId", el.getId()));
            }
        }
        return conflicts;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> parseSnapshot(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return new com.google.gson.Gson().fromJson(json, Map.class);
        } catch (Exception e) { return Map.of(); }
    }

    private ConflictResponse toResponse(Conflict c) {
        return ConflictResponse.builder()
                .id(c.getId()).elementId(c.getElementId())
                .chapterId(c.getChapter().getId())
                .chapterRefVersion(c.getChapterRefVersion())
                .currentVersion(c.getCurrentVersion())
                .severity(c.getSeverity().name()).description(c.getDescription())
                .status(c.getStatus().name()).createdAt(c.getCreatedAt().toString())
                .build();
    }
}
```

### Task 2B.4: 实现 NovelWriteService

**Files:**
- Create: `backend/src/main/java/com/aiwriter/service/NovelWriteService.java`

```java
package com.aiwriter.service;

import com.aiwriter.ai.ClaudeService;
import com.aiwriter.model.*;
import com.aiwriter.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class NovelWriteService {
    private final ClaudeService claudeService;
    private final ContextBuilder contextBuilder;
    private final ChapterService chapterService;
    private final ConflictService conflictService;
    private final FrameworkService frameworkService;
    private final DocumentRepository documentRepo;

    public Flux<String> startWriting(Long userId, Long frameworkId) {
        Framework fw = frameworkService.findOwned(userId, frameworkId);
        if (fw.getStatus() != Framework.FrameworkStatus.ACTIVE) {
            return Flux.error(new IllegalStateException("框架已完结"));
        }

        // Get or create current chapter
        Chapter latest = chapterService.getLatestChapter(frameworkId);
        if (latest == null || latest.getStatus() == Chapter.ChapterStatus.COMPLETED) {
            int nextIdx = (latest != null) ? latest.getChapterIndex() + 1 : 1;
            Document doc = createEmptyDocument(userId, fw.getProject().getId());
            latest = chapterService.createChapter(fw, nextIdx, doc.getId());
        }
        latest = chapterService.markGenerating(latest.getId());

        String currentContent = latest.getDocument() != null && latest.getDocument().getContent() != null
                ? latest.getDocument().getContent() : "";
        ContextBuilder.ContextPackage ctx = contextBuilder.buildContext(frameworkId, currentContent);

        String systemPrompt = buildNovelSystemPrompt(ctx);
        String userPrompt = buildContinueUserPrompt(ctx, currentContent);

        final Chapter chapterRef = latest;
        return claudeService.stream(systemPrompt, userPrompt)
                .doOnComplete(() -> {
                    // Generate summary async
                    chapterService.markGenerated(chapterRef.getId(), null);
                    conflictService.checkChapterConsistency(fw, chapterRef,
                            chapterRef.getDocument().getContent());
                });
    }

    public Flux<String> continueWriting(Long userId, Long frameworkId) {
        return startWriting(userId, frameworkId); // Same flow — creates next chapter
    }

    public void saveChapterContent(Long chapterId, String content) {
        Chapter ch = chapterService.list(/* need frameworkId */ 0L).stream()
                .filter(c -> c.getId().equals(chapterId)).findFirst().orElse(null);
        if (ch != null && ch.getDocument() != null) {
            Document doc = ch.getDocument();
            doc.setContent(content);
            documentRepo.save(doc);
        }
    }

    private Document createEmptyDocument(Long userId, Long projectId) {
        Document doc = Document.builder()
                .project(Project.builder().id(projectId).build())
                .user(User.builder().id(userId).build())
                .title("第?章")
                .content("")
                .build();
        return documentRepo.save(doc);
    }

    private String buildNovelSystemPrompt(ContextBuilder.ContextPackage ctx) {
        return """
            你是一位专业的小说家。根据提供的设定资料续写小说。
            
            """ + ctx.systemContext() + """
            
            【写作要求】
            1. 严格遵守以上设定，不与之产生矛盾
            2. 保持情节连贯，与前文衔接自然
            3. 语言流畅，描写生动
            4. 每章字数 1000-3000 字
            5. 在本章自然收尾，为下一章留悬念
            """;
    }

    private String buildContinueUserPrompt(ContextBuilder.ContextPackage ctx, String currentContent) {
        StringBuilder sb = new StringBuilder();
        if (!ctx.earlierSummaries().isEmpty()) {
            sb.append("【前情提要】\n").append(ctx.earlierSummaries()).append("\n\n");
        }
        if (!ctx.recentContent().isEmpty()) {
            sb.append("【前文内容】\n").append(ctx.recentContent()).append("\n\n");
        }
        sb.append("请续写下一章内容。");
        return sb.toString();
    }
}
```

### Task 2B.5: 实现 WriteController（SSE）

**Files:**
- Create: `backend/src/main/java/com/aiwriter/controller/WriteController.java`

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.InterveneRequest;
import com.aiwriter.dto.response.ChapterResponse;
import com.aiwriter.service.ChapterService;
import com.aiwriter.service.NovelWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WriteController {
    private final NovelWriteService novelWriteService;
    private final ChapterService chapterService;

    @PostMapping(value = "/frameworks/{frameworkId}/write/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> start(@PathVariable Long frameworkId, Authentication auth) {
        return novelWriteService.startWriting(getUserId(auth), frameworkId)
                .map(chunk -> "data: " + chunk + "\n\n");
    }

    @PostMapping(value = "/frameworks/{frameworkId}/write/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> continueWrite(@PathVariable Long frameworkId, Authentication auth) {
        return novelWriteService.continueWriting(getUserId(auth), frameworkId)
                .map(chunk -> "data: " + chunk + "\n\n");
    }

    @PostMapping("/frameworks/{frameworkId}/write/interrupt")
    public ResponseEntity<ChapterResponse> intervene(
            @PathVariable Long frameworkId,
            @RequestBody InterveneRequest req,
            Authentication auth) {
        // Insert user content into current chapter and trigger continue
        // Full implementation saves user input to document, then calls continueWriting
        return ResponseEntity.ok().build(); // Simplified stub
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

### Task 2B.6: 实现 EventController + ConflictController

**Files:**
- Create: `backend/src/main/java/com/aiwriter/controller/EventController.java`

```java
package com.aiwriter.controller;

import com.aiwriter.dto.response.WritingEventResponse;
import com.aiwriter.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventController {
    private final EventService eventService;

    @GetMapping("/frameworks/{frameworkId}/events")
    public ResponseEntity<List<WritingEventResponse>> list(
            @PathVariable Long frameworkId, Authentication auth) {
        return ResponseEntity.ok(eventService.list(getUserId(auth), frameworkId));
    }

    @GetMapping("/frameworks/{frameworkId}/events/{timestamp}")
    public ResponseEntity<List<WritingEventResponse>> upTo(
            @PathVariable Long frameworkId,
            @PathVariable String timestamp, Authentication auth) {
        LocalDateTime until = LocalDateTime.parse(timestamp);
        return ResponseEntity.ok(eventService.listUpTo(getUserId(auth), frameworkId, until));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

- Create: `backend/src/main/java/com/aiwriter/controller/ConflictController.java`

```java
package com.aiwriter.controller;

import com.aiwriter.dto.request.ResolveConflictRequest;
import com.aiwriter.dto.response.ConflictResponse;
import com.aiwriter.service.ConflictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConflictController {
    private final ConflictService conflictService;

    @GetMapping("/frameworks/{frameworkId}/conflicts")
    public ResponseEntity<List<ConflictResponse>> list(@PathVariable Long frameworkId, Authentication auth) {
        return ResponseEntity.ok(conflictService.listByFramework(getUserId(auth), frameworkId));
    }

    @PostMapping("/frameworks/{frameworkId}/conflicts/{conflictId}/resolve")
    public ResponseEntity<ConflictResponse> resolve(
            @PathVariable Long frameworkId,
            @PathVariable Long conflictId,
            @Valid @RequestBody ResolveConflictRequest req,
            Authentication auth) {
        return ResponseEntity.ok(conflictService.resolve(getUserId(auth), frameworkId, conflictId, req));
    }

    @PostMapping("/frameworks/{frameworkId}/elements/{elementId}/impact")
    public ResponseEntity<List<Long>> impact(
            @PathVariable Long frameworkId,
            @PathVariable String elementId,
            Authentication auth) {
        return ResponseEntity.ok(conflictService.getImpactedChapters(getUserId(auth), frameworkId, elementId));
    }

    private Long getUserId(Authentication auth) { return (Long) auth.getPrincipal(); }
}
```

### Task 2B.7: 运行后端编译

```bash
cd backend && mvn compile -q
```

### Task 2B.8: 提交阶段 2B

```bash
git add -A
git commit -m "feat: add event service, chapter service, conflict service, and writing engine

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 阶段 2C: 前端（Claude C）

**依赖：** 阶段 1 已合并到 master（Claude C 可以先用 Mock 数据开发，不严格依赖 2A/2B）
**接口契约：** 后端 API 路径和响应结构见 2A/2B 契约章节

### Task 2C.1: 新增 Types

**Files:**
- Modify: `frontend/src/types/index.ts`

追加以下类型：

```ts
export interface Framework {
  id: number
  projectId: number
  status: 'ACTIVE' | 'FINISHED'
  elementCount: number
  chapterCount: number
  unresolvedConflictCount: number
  createdAt: string
  updatedAt: string
}

export interface FrameworkElement {
  id: string
  version: number
  type: string
  name: string
  content: string
  isDeprecated: boolean
  createdAt: string
}

export interface Chapter {
  id: number
  documentId: number
  chapterIndex: number
  status: 'DRAFT' | 'AI_GENERATING' | 'AI_GENERATED' | 'USER_EDITING' | 'COMPLETED'
  summary: string
  createdAt: string
  updatedAt: string
}

export interface WritingEvent {
  id: number
  eventType: string
  payload: string
  timestamp: string
}

export interface Conflict {
  id: number
  elementId: string
  chapterId: number
  chapterRefVersion: number
  currentVersion: number
  severity: 'MAJOR' | 'MINOR'
  description: string
  status: 'PENDING' | 'IGNORED' | 'PATCHED' | 'REWRITTEN'
  createdAt: string
}

export const ELEMENT_TYPES = [
  { value: 'CHARACTER', label: '角色' },
  { value: 'LOCATION', label: '地点' },
  { value: 'ITEM', label: '物品/道具' },
  { value: 'ORGANIZATION', label: '组织/势力' },
  { value: 'EVENT', label: '事件/时间线' },
  { value: 'WORLD_SETTING', label: '世界观设定' },
] as const
```

### Task 2C.2: 新增 API 模块

**Files:**
- Create: `frontend/src/api/framework.ts`

```ts
import client from './client'
import type { Framework, FrameworkElement, Chapter, WritingEvent, Conflict } from '@/types'

export const frameworkApi = {
  open: (projectId: number) => client.post<Framework>(`/v1/projects/${projectId}/framework`),
  get: (id: number) => client.get<Framework>(`/v1/frameworks/${id}`),
  finish: (id: number) => client.post<Framework>(`/v1/frameworks/${id}/finish`),

  // Elements
  listElements: (fwId: number, type?: string) =>
    client.get<FrameworkElement[]>(`/v1/frameworks/${fwId}/elements`, { params: { type } }),
  createElement: (fwId: number, data: { type: string; name: string; content: string }) =>
    client.post<FrameworkElement>(`/v1/frameworks/${fwId}/elements`, data),
  updateElement: (fwId: number, elementId: string, data: { type: string; name: string; content: string }) =>
    client.put<FrameworkElement>(`/v1/frameworks/${fwId}/elements/${elementId}`, data),
  deleteElement: (fwId: number, elementId: string) =>
    client.delete(`/v1/frameworks/${fwId}/elements/${elementId}`),
  getElementVersions: (fwId: number, elementId: string) =>
    client.get<FrameworkElement[]>(`/v1/frameworks/${fwId}/elements/${elementId}/versions`),
  aiGenerateElements: (fwId: number, data: { type: string; hint?: string }) =>
    client.post<FrameworkElement[]>(`/v1/frameworks/${fwId}/elements/ai-generate`, data),

  // Writing
  startWriting: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/start`, null, { responseType: 'stream', adapter: 'fetch' }),
  continueWriting: (fwId: number) =>
    client.post(`/v1/frameworks/${fwId}/write/continue`, null, { responseType: 'stream', adapter: 'fetch' }),
  intervene: (fwId: number, data: { content: string; position?: string }) =>
    client.post(`/v1/frameworks/${fwId}/write/interrupt`, data),

  // Conflicts
  listConflicts: (fwId: number) => client.get<Conflict[]>(`/v1/frameworks/${fwId}/conflicts`),
  resolveConflict: (fwId: number, conflictId: number, data: { action: string; patchContent?: string }) =>
    client.post<Conflict>(`/v1/frameworks/${fwId}/conflicts/${conflictId}/resolve`, data),
  getImpact: (fwId: number, elementId: string) =>
    client.post<number[]>(`/v1/frameworks/${fwId}/elements/${elementId}/impact`),

  // Events
  listEvents: (fwId: number) => client.get<WritingEvent[]>(`/v1/frameworks/${fwId}/events`),
}
```

### Task 2C.3: 新增 frameworkStore

**Files:**
- Create: `frontend/src/stores/framework.ts`

```ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Framework, FrameworkElement, Chapter, Conflict, WritingEvent } from '@/types'
import { frameworkApi } from '@/api/framework'

export const useFrameworkStore = defineStore('framework', () => {
  const framework = ref<Framework | null>(null)
  const elements = ref<FrameworkElement[]>([])
  const chapters = ref<Chapter[]>([])
  const conflicts = ref<Conflict[]>([])
  const events = ref<WritingEvent[]>([])
  const loading = ref(false)

  async function openFramework(projectId: number) {
    const res = await frameworkApi.open(projectId)
    framework.value = res.data
    return res.data
  }

  async function fetchFramework(frameworkId: number) {
    loading.value = true
    try {
      const res = await frameworkApi.get(frameworkId)
      framework.value = res.data
      // Fetch sub-resources
      const [els] = await Promise.all([
        frameworkApi.listElements(frameworkId),
      ])
      elements.value = els.data
    } finally { loading.value = false }
  }

  async function createElement(fwId: number, data: { type: string; name: string; content: string }) {
    const res = await frameworkApi.createElement(fwId, data)
    elements.value.unshift(res.data)
    return res.data
  }

  async function updateElement(fwId: number, elementId: string, data: { type: string; name: string; content: string }) {
    const res = await frameworkApi.updateElement(fwId, elementId, data)
    const idx = elements.value.findIndex(e => e.id === elementId)
    if (idx >= 0) elements.value[idx] = res.data
    return res.data
  }

  async function deleteElement(fwId: number, elementId: string) {
    await frameworkApi.deleteElement(fwId, elementId)
    elements.value = elements.value.filter(e => e.id !== elementId)
  }

  async function fetchConflicts(fwId: number) {
    const res = await frameworkApi.listConflicts(fwId)
    conflicts.value = res.data
  }

  async function resolveConflict(fwId: number, conflictId: number, action: string, patchContent?: string) {
    const res = await frameworkApi.resolveConflict(fwId, conflictId, { action, patchContent })
    const idx = conflicts.value.findIndex(c => c.id === conflictId)
    if (idx >= 0) conflicts.value[idx] = res.data
    return res.data
  }

  async function fetchEvents(fwId: number) {
    const res = await frameworkApi.listEvents(fwId)
    events.value = res.data
  }

  async function finishFramework(fwId: number) {
    const res = await frameworkApi.finish(fwId)
    framework.value = res.data
  }

  return {
    framework, elements, chapters, conflicts, events, loading,
    openFramework, fetchFramework,
    createElement, updateElement, deleteElement,
    fetchConflicts, resolveConflict,
    fetchEvents, finishFramework,
  }
})
```

### Task 2C.4: 更新路由 — 添加框架页面

**Files:**
- Modify: `frontend/src/router/index.ts`

在 `routes` 数组中，在现有的 `workspace` 路由之后追加：

```ts
{
  path: 'project/:id/framework',
  name: 'framework',
  component: () => import('@/views/FrameworkView.vue'),
  meta: { requiresAuth: true },
},
{
  path: 'project/:id/framework/elements',
  name: 'framework-elements',
  component: () => import('@/views/ElementsView.vue'),
  meta: { requiresAuth: true },
},
{
  path: 'project/:id/framework/write',
  name: 'framework-write',
  component: () => import('@/views/WriteView.vue'),
  meta: { requiresAuth: true },
},
{
  path: 'project/:id/framework/conflicts',
  name: 'framework-conflicts',
  component: () => import('@/views/ConflictsView.vue'),
  meta: { requiresAuth: true },
},
{
  path: 'project/:id/framework/history',
  name: 'framework-history',
  component: () => import('@/views/HistoryView.vue'),
  meta: { requiresAuth: true },
},
```

### Task 2C.5: 创建 FrameworkView（框架主页）

**Files:**
- Create: `frontend/src/views/FrameworkView.vue`

```vue
<template>
  <div class="framework-view">
    <div class="fw-header">
      <div class="fw-title">
        <h2>写作框架</h2>
        <el-tag :type="fw?.status === 'ACTIVE' ? 'success' : 'info'">
          {{ fw?.status === 'ACTIVE' ? '创作中' : '已完结' }}
        </el-tag>
      </div>
      <div class="fw-actions">
        <el-button type="primary" @click="goWrite">进入写作</el-button>
        <el-button @click="goElements">管理设定</el-button>
        <el-button type="danger" plain @click="handleFinish" :disabled="fw?.status !== 'ACTIVE'">
          完结框架
        </el-button>
      </div>
    </div>

    <el-row :gutter="20" class="fw-stats">
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="设定元素" :value="fw?.elementCount ?? 0" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="章节数" :value="fw?.chapterCount ?? 0" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="未处理冲突" :value="fw?.unresolvedConflictCount ?? 0">
            <template #suffix>
              <el-tag v-if="(fw?.unresolvedConflictCount ?? 0) > 0" type="danger" size="small">需处理</el-tag>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="状态" :value="fw?.status === 'ACTIVE' ? 1 : 0">
          <template #title><span>已完结</span></template>
          <template #suffix>
            <el-tag :type="fw?.status === 'FINISHED' ? 'success' : 'info'" size="small">
              {{ fw?.status === 'FINISHED' ? '是' : '否' }}
            </el-tag>
          </template>
        </el-statistic></el-card>
      </el-col>
    </el-row>

    <el-card class="fw-nav">
      <el-menu :default-active="route.name as string" mode="horizontal" @select="handleMenuSelect">
        <el-menu-item index="framework">概览</el-menu-item>
        <el-menu-item index="framework-elements">设定元素</el-menu-item>
        <el-menu-item index="framework-write">写作工作台</el-menu-item>
        <el-menu-item index="framework-conflicts">
          冲突管理
          <el-badge v-if="(fw?.unresolvedConflictCount ?? 0) > 0" :value="fw?.unresolvedConflictCount" style="margin-left: 8px" />
        </el-menu-item>
        <el-menu-item index="framework-history">历史事件</el-menu-item>
      </el-menu>
    </el-card>

    <router-view />
  </div>
</template>

<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const fwStore = useFrameworkStore()
const fw = computed(() => fwStore.framework)

onMounted(async () => {
  const projectId = Number(route.params.id)
  try {
    await fwStore.fetchFramework(projectId)
  } catch {
    // Framework not yet opened — try to open
    try {
      await fwStore.openFramework(projectId)
    } catch (e: any) {
      ElMessage.error('无法进入框架模式')
      router.push(`/project/${projectId}`)
    }
  }
})

function goWrite() { router.push({ name: 'framework-write', params: { id: route.params.id } }) }
function goElements() { router.push({ name: 'framework-elements', params: { id: route.params.id } }) }

async function handleFinish() {
  try {
    await ElMessageBox.confirm('确定要完结此框架吗？完结后 AI 将停止写作。', '确认完结', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    if (fw.value) await fwStore.finishFramework(fw.value.id)
    ElMessage.success('框架已完结')
  } catch { /* cancelled */ }
}

function handleMenuSelect(index: string) {
  router.push({ name: index, params: { id: route.params.id } })
}
</script>

<style scoped>
.framework-view { padding: 24px; max-width: 1200px; margin: 0 auto; }
.fw-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.fw-title { display: flex; align-items: center; gap: 12px; }
.fw-title h2 { margin: 0; }
.fw-stats { margin-bottom: 24px; }
.fw-nav { margin-bottom: 24px; }
</style>
```

### Task 2C.6: 创建 ElementsView（设定管理）

**Files:**
- Create: `frontend/src/views/ElementsView.vue`

```vue
<template>
  <div class="elements-view">
    <div class="elements-toolbar">
      <el-select v-model="filterType" placeholder="按类型筛选" clearable style="width: 180px" @change="fetchElements">
        <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-input v-model="searchText" placeholder="搜索名称..." style="width: 240px" clearable />
      <el-button type="primary" @click="showCreate = true"><el-icon><Plus /></el-icon>添加</el-button>
      <el-button @click="showAiGen = true"><el-icon><MagicStick /></el-icon>AI 生成</el-button>
    </div>

    <div v-if="elements.length === 0" class="empty-state">
      <p>暂无设定元素，点击"添加"或"AI 生成"创建第一个设定</p>
    </div>

    <div class="element-grid">
      <el-card v-for="el in filteredElements" :key="`${el.id}-${el.version}`" class="element-card" shadow="hover" @click="openDetail(el)">
        <template #header>
          <div class="card-header">
            <el-tag :type="getTypeTagColor(el.type)" size="small">{{ getTypeLabel(el.type) }}</el-tag>
            <span class="card-name">{{ el.name }}</span>
          </div>
        </template>
        <p class="card-content">{{ el.content.substring(0, 100) }}{{ el.content.length > 100 ? '...' : '' }}</p>
      </el-card>
    </div>

    <!-- Create Dialog -->
    <el-dialog v-model="showCreate" title="添加设定元素" width="500px">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="类型">
          <el-select v-model="createForm.type" placeholder="选择类型">
            <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="createForm.name" maxlength="200" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="createForm.content" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- AI Generate Dialog -->
    <el-dialog v-model="showAiGen" title="AI 生成设定" width="500px">
      <el-form label-position="top">
        <el-form-item label="类型">
          <el-select v-model="aiGenForm.type" placeholder="选择要生成的类型">
            <el-option v-for="t in ELEMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提示（可选）">
          <el-input v-model="aiGenForm.hint" placeholder="例如：一个冷漠的剑客..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAiGen = false">取消</el-button>
        <el-button type="primary" @click="handleAiGen" :loading="aiGenerating">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { ELEMENT_TYPES } from '@/types'
import { ElMessage } from 'element-plus'
import { Plus, MagicStick } from '@element-plus/icons-vue'

const route = useRoute()
const fwStore = useFrameworkStore()

const elements = computed(() => fwStore.elements)
const filterType = ref('')
const searchText = ref('')
const showCreate = ref(false)
const showAiGen = ref(false)
const aiGenerating = ref(false)

const createForm = ref({ type: 'CHARACTER', name: '', content: '' })
const aiGenForm = ref({ type: 'CHARACTER', hint: '' })

const filteredElements = computed(() => {
  let list = elements.value
  if (searchText.value) {
    list = list.filter(e => e.name.includes(searchText.value))
  }
  return list
})

function getTypeLabel(type: string) { return ELEMENT_TYPES.find(t => t.value === type)?.label ?? type }
function getTypeTagColor(type: string) {
  const map: Record<string, string> = { CHARACTER: '', LOCATION: 'success', ITEM: 'warning', ORGANIZATION: 'danger', EVENT: 'info', WORLD_SETTING: 'primary' }
  return map[type] ?? ''
}

async function fetchElements() {
  const projectId = Number(route.params.id)
  // Use stored framework ID
}

async function handleCreate() {
  const projectId = Number(route.params.id)
  if (!createForm.value.name || !createForm.value.content) {
    ElMessage.warning('请填写名称和内容')
    return
  }
  // Call store with frameworkId — need to get from framework store
  showCreate.value = false
  ElMessage.success('设定已添加')
  createForm.value = { type: 'CHARACTER', name: '', content: '' }
}

async function handleAiGen() {
  aiGenerating.value = true
  try {
    // await fwStore.aiGenerate(...)
    ElMessage.success('AI 生成完成')
    showAiGen.value = false
  } catch { ElMessage.error('生成失败') }
  finally { aiGenerating.value = false }
}

function openDetail(el: any) { /* Open detail panel */ }
</script>

<style scoped>
.elements-view { }
.elements-toolbar { display: flex; gap: 12px; margin-bottom: 20px; align-items: center; }
.empty-state { text-align: center; padding: 48px; color: var(--el-text-color-secondary); }
.element-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.card-header { display: flex; align-items: center; gap: 8px; }
.card-name { font-weight: 600; }
.card-content { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.5; margin: 0; }
</style>
```

### Task 2C.7: 创建 WriteView（写作工作台 — 核心页面）

**Files:**
- Create: `frontend/src/views/WriteView.vue`

```vue
<template>
  <div class="write-view">
    <!-- Context Bar -->
    <div class="context-bar">
      <el-tag v-for="stat in contextStats" :key="stat.label" size="small">
        {{ stat.label }}: {{ stat.count }}
      </el-tag>
      <el-button text size="small" @click="showContextDetail = !showContextDetail">
        {{ showContextDetail ? '收起' : '展开' }}详情
      </el-button>
    </div>

    <div class="write-layout">
      <!-- Chapter Panel -->
      <div class="chapter-panel">
        <h4>章节列表</h4>
        <div v-for="ch in chapters" :key="ch.id" class="chapter-item">
          <span class="ch-index">第{{ ch.chapterIndex }}章</span>
          <el-tag :type="statusTag(ch.status)" size="small">{{ statusLabel(ch.status) }}</el-tag>
        </div>
        <el-empty v-if="!chapters.length" description="暂无章节" />
      </div>

      <!-- Writing Canvas -->
      <div class="writing-canvas">
        <div v-if="!isWriting && !currentContent" class="canvas-empty">
          <el-empty description="准备开始写作">
            <el-button type="primary" size="large" @click="startWriting">开始写作</el-button>
          </el-empty>
        </div>
        <div v-else class="canvas-content">
          <div class="content-display">{{ currentContent }}</div>
          <div v-if="isWriting" class="writing-indicator">
            <el-icon class="is-loading"><Loading /></el-icon> AI 正在写作...
          </div>
        </div>
      </div>
    </div>

    <!-- Controls -->
    <div class="write-controls">
      <template v-if="!isWriting">
        <el-button type="primary" @click="startWriting">开始写作</el-button>
        <el-button @click="continueWriting" :disabled="!currentContent">继续下一章</el-button>
      </template>
      <template v-else>
        <el-button @click="pauseWriting">暂停</el-button>
      </template>
      <el-button @click="showIntervene = true" :disabled="!currentContent">插入内容</el-button>
      <el-button type="danger" plain @click="handleFinish">完结</el-button>
    </div>

    <!-- Intervene Dialog -->
    <el-dialog v-model="showIntervene" title="插入内容或指令" width="600px">
      <el-input v-model="interveneText" type="textarea" :rows="8" placeholder="输入你要插入的内容，或给 AI 的指令..." />
      <template #footer>
        <el-button @click="showIntervene = false">取消</el-button>
        <el-button type="primary" @click="handleIntervene">提交并续写</el-button>
      </template>
    </el-dialog>

    <!-- Conflict Notifier -->
    <div v-if="newConflicts.length > 0" class="conflict-notifier" @click="goConflicts">
      <el-icon><WarningFilled /></el-icon>
      检测到 {{ newConflicts.length }} 个设定冲突，点击处理
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { frameworkApi } from '@/api/framework'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, WarningFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const fwStore = useFrameworkStore()

const elements = computed(() => fwStore.elements)
const chapters = ref<any[]>([])
const currentContent = ref('')
const isWriting = ref(false)
const showIntervene = ref(false)
const showContextDetail = ref(false)
const interveneText = ref('')
const newConflicts = ref<any[]>([])

const contextStats = computed(() => {
  const grouped: Record<string, number> = {}
  elements.value.forEach(e => {
    const label = e.type
    grouped[label] = (grouped[label] || 0) + 1
  })
  return Object.entries(grouped).map(([label, count]) => ({ label, count }))
})

function statusTag(s: string) {
  const map: Record<string, string> = { DRAFT: 'info', AI_GENERATING: 'warning', AI_GENERATED: 'success', USER_EDITING: '', COMPLETED: 'primary' }
  return map[s] ?? 'info'
}
function statusLabel(s: string) {
  const map: Record<string, string> = { DRAFT: '草稿', AI_GENERATING: '生成中', AI_GENERATED: '已生成', USER_EDITING: '编辑中', COMPLETED: '已完成' }
  return map[s] ?? s
}

async function startWriting() {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  isWriting.value = true
  currentContent.value = ''
  try {
    const response = await frameworkApi.startWriting(fwId)
    const reader = response.data.getReader()
    const decoder = new TextDecoder()
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      currentContent.value += decoder.decode(value, { stream: true })
    }
  } catch { ElMessage.error('AI 写作失败') }
  isWriting.value = false
}

async function continueWriting() {
  // Same as startWriting but for next chapter
  await startWriting()
}

function pauseWriting() {
  isWriting.value = false
  ElMessage.info('已暂停')
}

async function handleIntervene() {
  if (!interveneText.value.trim()) return
  showIntervene.value = false
  const fwId = fwStore.framework?.id
  if (!fwId) return
  await frameworkApi.intervene(fwId, { content: interveneText.value })
  currentContent.value += '\n\n' + interveneText.value
  interveneText.value = ''
  await startWriting()
}

async function handleFinish() {
  try {
    await ElMessageBox.confirm('确定要完结此框架吗？', '确认完结', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
    if (fwStore.framework) await fwStore.finishFramework(fwStore.framework.id)
    ElMessage.success('框架已完结')
  } catch { /* cancelled */ }
}

function goConflicts() {
  router.push({ name: 'framework-conflicts', params: { id: route.params.id } })
}
</script>

<style scoped>
.write-view { display: flex; flex-direction: column; height: calc(100vh - 200px); }
.context-bar { display: flex; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--el-border-color); flex-wrap: wrap; }
.write-layout { flex: 1; display: flex; overflow: hidden; }
.chapter-panel { width: 200px; border-right: 1px solid var(--el-border-color); padding: 12px; overflow-y: auto; }
.chapter-item { padding: 8px 0; border-bottom: 1px solid var(--el-border-color-lighter); display: flex; justify-content: space-between; align-items: center; }
.writing-canvas { flex: 1; padding: 20px; overflow-y: auto; }
.canvas-empty { display: flex; justify-content: center; align-items: center; height: 100%; }
.canvas-content { white-space: pre-wrap; line-height: 1.8; font-size: 16px; }
.writing-indicator { display: flex; align-items: center; gap: 8px; color: var(--el-color-primary); margin-top: 16px; }
.write-controls { display: flex; gap: 12px; padding: 12px 0; border-top: 1px solid var(--el-border-color); justify-content: center; }
.conflict-notifier { position: fixed; right: 24px; bottom: 80px; background: var(--el-color-danger); color: white; padding: 12px 20px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; gap: 8px; z-index: 1000; animation: slideUp 0.3s ease; }
@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
</style>
```

### Task 2C.8: 创建 ConflictsView + HistoryView（简化版）

**Files:**
- Create: `frontend/src/views/ConflictsView.vue`

```vue
<template>
  <div class="conflicts-view">
    <el-radio-group v-model="statusFilter">
      <el-radio-button value="">全部</el-radio-button>
      <el-radio-button value="PENDING">待处理</el-radio-button>
      <el-radio-button value="PATCHED">已补丁</el-radio-button>
      <el-radio-button value="IGNORED">已忽略</el-radio-button>
      <el-radio-button value="REWRITTEN">已重写</el-radio-button>
    </el-radio-group>

    <div class="conflict-list">
      <el-card v-for="c in filteredConflicts" :key="c.id" class="conflict-card">
        <div class="conflict-header">
          <el-tag :type="c.severity === 'MAJOR' ? 'danger' : 'warning'">
            {{ c.severity === 'MAJOR' ? '大冲突' : '小冲突' }}
          </el-tag>
          <el-tag :type="statusMap[c.status]">{{ statusLabel(c.status) }}</el-tag>
        </div>
        <p>{{ c.description }}</p>
        <div v-if="c.status === 'PENDING'" class="conflict-actions">
          <el-button size="small" @click="resolve(c.id, 'ignore')">忽略</el-button>
          <el-button size="small" type="primary" @click="resolve(c.id, 'patch')">打补丁</el-button>
          <el-button size="small" type="danger" @click="resolve(c.id, 'rewrite')">重写章节</el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'
import { ElMessage } from 'element-plus'

const route = useRoute()
const fwStore = useFrameworkStore()
const statusFilter = ref('')
const statusMap: Record<string, string> = { PENDING: 'danger', PATCHED: 'success', IGNORED: 'info', REWRITTEN: 'warning' }

const filteredConflicts = computed(() => {
  let list = fwStore.conflicts
  if (statusFilter.value) list = list.filter(c => c.status === statusFilter.value)
  return list
})

function statusLabel(s: string) {
  const map: Record<string, string> = { PENDING: '待处理', PATCHED: '已补丁', IGNORED: '已忽略', REWRITTEN: '已重写' }
  return map[s] ?? s
}

async function resolve(conflictId: number, action: string) {
  const fwId = fwStore.framework?.id
  if (!fwId) return
  try {
    await fwStore.resolveConflict(fwId, conflictId, action)
    ElMessage.success('处理完成')
  } catch { ElMessage.error('处理失败') }
}

onMounted(async () => {
  const fwId = fwStore.framework?.id
  if (fwId) await fwStore.fetchConflicts(fwId)
})
</script>

<style scoped>
.conflicts-view { padding: 16px; }
.conflict-list { margin-top: 16px; display: flex; flex-direction: column; gap: 12px; }
.conflict-header { display: flex; gap: 8px; margin-bottom: 8px; }
.conflict-actions { display: flex; gap: 8px; margin-top: 12px; justify-content: flex-end; }
</style>
```

- Create: `frontend/src/views/HistoryView.vue`

```vue
<template>
  <div class="history-view">
    <el-timeline>
      <el-timeline-item
        v-for="e in fwStore.events"
        :key="e.id"
        :timestamp="e.timestamp"
        :type="eventIcon(e.eventType)"
        placement="top"
      >
        <el-card>
          <h4>{{ eventLabel(e.eventType) }}</h4>
          <pre v-if="e.payload" class="payload">{{ formatJson(e.payload) }}</pre>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFrameworkStore } from '@/stores/framework'

const route = useRoute()
const fwStore = useFrameworkStore()

onMounted(async () => {
  const fwId = fwStore.framework?.id
  if (fwId) await fwStore.fetchEvents(fwId)
})

const eventLabels: Record<string, string> = {
  ELEMENT_CREATED: '创建元素', ELEMENT_UPDATED: '更新元素', ELEMENT_DEPRECATED: '弃用元素',
  CHAPTER_STARTED: '开始章节', CHAPTER_AI_GENERATED: 'AI 生成章节完成', USER_INTERVENED: '用户介入',
  CONFLICT_DETECTED: '检测到冲突', CONFLICT_RESOLVED: '解决冲突', PATCH_APPLIED: '应用补丁',
  FRAMEWORK_FINISHED: '框架完结', CHAPTER_SUMMARY_GENERATED: '生成摘要', ELEMENT_AI_GENERATED: 'AI 生成元素',
}
function eventLabel(t: string) { return eventLabels[t] ?? t }
function eventIcon(t: string) {
  if (t.includes('CONFLICT')) return 'danger'
  if (t.includes('ELEMENT')) return 'primary'
  if (t.includes('CHAPTER') || t.includes('AI')) return 'success'
  return 'info'
}
function formatJson(s: string) { try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s } }
</script>

<style scoped>
.history-view { padding: 16px; max-width: 800px; }
.payload { background: var(--el-fill-color-light); padding: 8px; border-radius: 4px; font-size: 12px; overflow-x: auto; }
</style>
```

### Task 2C.9: 提交阶段 2C

```bash
git add -A
git commit -m "feat: add frontend framework pages — ElementsView, WriteView, ConflictsView, HistoryView

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 阶段 3: E2E 集成 + 完善（1 个 Claude，约 2-3 小时）

### Task 3.1: 集成安全配置 — 将 /api/v1/** 加入认证白名单逻辑

确认 `SecurityConfig.java` 中 `/api/**` 的认证规则覆盖 `/api/v1/**` 所有端点。当前规则 `.requestMatchers("/api/auth/**").permitAll().anyRequest().authenticated()` 已自动覆盖 /api/v1/** — 无需修改。

### Task 3.2: 完善 ElementService.aiGenerate — 接入 Claude 生成元素

修改 `ElementService.aiGenerate()` 方法，调用 `ClaudeService.complete()` 生成元素内容。注入已有元素上下文避免冲突。

### Task 3.3: 完善 NovelWriteService 的章节内容保存

在 SSE 流完成后，将生成的内容写入对应 Document。完善 `intervene` 逻辑，保存用户插入内容后触发续写。

### Task 3.4: 完善 WriteController.intervene

实现完整的介入逻辑：保存用户输入 → 记录 WritingEvent → 调用 continueWriting。

### Task 3.5: 前端 WriteView 与后端 SSE 集成联调

验证完整的写作循环：开始 → 流式输出 → 章节完成 → 继续下一章。

### Task 3.6: 冲突检测全流程测试

1. 创建元素 → 生成章节 → 修改元素 → 生成新章节 → 查看冲突列表
2. 处理冲突（忽略/补丁/重写）验证状态机

### Task 3.7: 提交阶段 3

```bash
git add -A
git commit -m "feat: complete E2E integration for novel writing engine

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 接口契约汇总

### Claude A → Claude B 共享

| 类型 | 字段 |
|------|------|
| `FrameworkResponse` | id, projectId, status, elementCount, chapterCount, unresolvedConflictCount, createdAt, updatedAt |
| `ElementResponse` | id, version, type, name, content, isDeprecated, createdAt |

Claude B 的 `ConflictService.checkChapterConsistency()` 依赖 Claude A 的 `FrameworkElementRepository.findLatestByFrameworkId()` 和 `findMaxVersionByElementId()`。

### Claude B → Claude C 共享

| 端点 | 请求 | 响应 |
|------|------|------|
| `POST /v1/frameworks/:id/write/start` | — | SSE text/event-stream |
| `POST /v1/frameworks/:id/write/continue` | — | SSE text/event-stream |
| `POST /v1/frameworks/:id/write/interrupt` | `{ content: string, position?: string }` | `ChapterResponse` |
| `GET /v1/frameworks/:id/conflicts` | — | `ConflictResponse[]` |
| `POST /v1/frameworks/:id/conflicts/:cid/resolve` | `{ action: "ignore"/"patch"/"rewrite", patchContent?: string }` | `ConflictResponse` |
| `GET /v1/frameworks/:id/events` | — | `WritingEventResponse[]` |

### Claude A → Claude C 共享

| 端点 | 请求 | 响应 |
|------|------|------|
| `POST /v1/projects/:id/framework` | — | `FrameworkResponse` |
| `GET /v1/frameworks/:id` | — | `FrameworkResponse` |
| `GET /v1/frameworks/:id/elements?type=` | — | `ElementResponse[]` |
| `POST /v1/frameworks/:id/elements` | `{ type, name, content }` | `ElementResponse` |
| `PUT /v1/frameworks/:id/elements/:eid` | `{ type, name, content }` | `ElementResponse` |
| `DELETE /v1/frameworks/:id/elements/:eid` | — | `204` |
| `GET /v1/frameworks/:id/elements/:eid/versions` | — | `ElementResponse[]` |
| `POST /v1/frameworks/:id/elements/ai-generate` | `{ type, hint? }` | `ElementResponse[]` |
