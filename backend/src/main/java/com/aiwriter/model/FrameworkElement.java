package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "framework_elements")
@IdClass(FrameworkElement.ElementKey.class)
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class FrameworkElement {
    @Id @Column(length = 36)
    private String id;

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
    private String type;

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
