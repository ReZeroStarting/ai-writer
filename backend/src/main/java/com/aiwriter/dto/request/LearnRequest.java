package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LearnRequest {

    @NotBlank
    @Size(min = 1, max = 200)
    private String title;

    /** 素材上限 100 万字：超出请分段学习，避免一次请求把长文本整段送进模型。 */
    @NotBlank
    @Size(max = 1000000, message = "素材过长，请分段学习")
    private String content;
}
