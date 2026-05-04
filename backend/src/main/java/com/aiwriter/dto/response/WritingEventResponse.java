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
