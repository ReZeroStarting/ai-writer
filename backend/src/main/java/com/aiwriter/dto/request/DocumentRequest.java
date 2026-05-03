package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentRequest {
    @NotBlank @Size(min = 1, max = 200)
    private String title;
    private String content;
}
