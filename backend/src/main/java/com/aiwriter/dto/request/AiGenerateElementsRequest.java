package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiGenerateElementsRequest {
    @NotBlank
    private String type;
    private String hint;
}
