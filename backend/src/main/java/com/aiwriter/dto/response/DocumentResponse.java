package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private Long projectId;
    private String title;
    private String content;
    private String createdAt;
    private String updatedAt;
}
