package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class FrameworkResponse {
    private Long id;
    private Long projectId;
    private String status;
    private int elementCount;
    private int chapterCount;
    private int unresolvedConflictCount;
    private String createdAt;
    private String updatedAt;
}
