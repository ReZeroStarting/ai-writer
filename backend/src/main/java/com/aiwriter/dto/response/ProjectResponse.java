package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ProjectResponse {
    private Long id;
    private String name;
    private String description;
    private int docCount;
    private String createdAt;
    private String updatedAt;
}
