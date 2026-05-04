package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FrameworkElementRequest {
    @NotBlank @Size(max = 50)
    private String type;
    @NotBlank @Size(max = 200)
    private String name;
    @NotBlank
    private String content;
}
