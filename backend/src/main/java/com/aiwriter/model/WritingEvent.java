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
