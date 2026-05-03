package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRequest {
    @NotBlank
    private String content;
    private Long styleTemplateId;
    private String instruction;
}
