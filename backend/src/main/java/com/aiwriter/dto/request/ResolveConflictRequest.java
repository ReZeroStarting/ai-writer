package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveConflictRequest {
    @NotBlank
    private String action; // "ignore", "patch", "rewrite"
    private String patchContent;
}
