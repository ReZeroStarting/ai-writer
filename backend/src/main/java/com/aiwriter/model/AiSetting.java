package com.aiwriter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用户自己的 AI 上游配置：用哪个 provider、自己的 key、可选模型与地址。
 *
 * <p>一个用户一条记录，主键就是 userId。没有记录 = 用服务端默认配置。
 * Key 只存密文（{@code api_key_enc}）和掩码（{@code api_key_hint}），
 * 明文不进数据库、不出接口。
 */
@Entity
@Table(name = "ai_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** DEEPSEEK / ANTHROPIC */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "api_key_enc", columnDefinition = "TEXT")
    private String apiKeyEnc;

    @Column(name = "api_key_hint", length = 40)
    private String apiKeyHint;

    @Column(length = 100)
    private String model;

    @Column(name = "base_url", length = 200)
    private String baseUrl;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
