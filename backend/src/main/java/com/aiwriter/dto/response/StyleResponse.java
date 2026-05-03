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
