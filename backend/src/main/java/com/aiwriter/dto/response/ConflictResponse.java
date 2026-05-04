package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ConflictResponse {
    private Long id;
    private String elementId;
    private Long chapterId;
    private Integer chapterRefVersion;
    private Integer currentVersion;
    private String severity;
    private String description;
    private String status;
    private String createdAt;
}
