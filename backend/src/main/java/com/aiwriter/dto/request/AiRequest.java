package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiRequest {

    /** 上限 5 万字：再长应该走章节续写，而不是把整篇塞进一次调用。 */
    @NotBlank
    @Size(max = 50000, message = "提交给 AI 的正文过长")
    private String content;

    private Long styleTemplateId;

    @Size(max = 2000, message = "指令过长")
    private String instruction;
}
