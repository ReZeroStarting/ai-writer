package com.aiwriter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data @Builder @AllArgsConstructor
public class ChapterResponse {
    private Long id;
    private Long documentId;
    private Integer chapterIndex;
    private String status;
    private String summary;
    private String createdAt;
    private String updatedAt;
}
