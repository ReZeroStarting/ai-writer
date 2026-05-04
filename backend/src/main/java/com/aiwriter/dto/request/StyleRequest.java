package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StyleRequest {
    @NotBlank @Size(min = 1, max = 100)
    private String name;
    @Size(max = 5000)
    private String systemPrompt;
    @Size(max = 500)
    private String description;
}
