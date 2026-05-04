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
    private String elementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", nullable = false)
    private Chapter chapter;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private ConflictSeverity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

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
