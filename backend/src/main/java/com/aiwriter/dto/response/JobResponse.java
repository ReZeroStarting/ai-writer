package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class JobResponse {
    private Long id;
    private Long templateId;
    private Long materialId;
    private String status;
    private String resultPrompt;
    private String errorMsg;
    private String createdAt;
    private String updatedAt;
}
