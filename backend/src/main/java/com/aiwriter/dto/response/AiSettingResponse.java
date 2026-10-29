package com.aiwriter.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiSettingResponse {
    /** 已保存的 provider；没配置时为空 */
    private String provider;
    /** 厂商中文名，界面直接显示 */
    private String providerLabel;
    /** 掩码后的 key，例如 sk-1a2b…9f0e */
    private String maskedKey;
    private String model;
    private String baseUrl;
    /** 是否已保存自己的 key */
    private boolean hasKey;
    /** 服务端默认的 provider，用于界面上提示 */
    private String defaultProvider;
    private String updatedAt;
}
