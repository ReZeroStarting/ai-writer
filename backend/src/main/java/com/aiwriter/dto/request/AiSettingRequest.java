package com.aiwriter.dto.request;

import lombok.Data;

@Data
public class AiSettingRequest {
    /** DEEPSEEK / ANTHROPIC；传空表示用服务端默认 */
    private String provider;
    /** 留空表示「不改动已保存的 key」；只有传了新 key 才会覆盖 */
    private String apiKey;
    private String model;
    private String baseUrl;
}
