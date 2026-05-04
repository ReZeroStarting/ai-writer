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
