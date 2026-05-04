package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InterveneRequest {
    @NotBlank
    private String content;
    private String position;
}
