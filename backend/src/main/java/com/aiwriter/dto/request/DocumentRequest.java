package com.aiwriter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentRequest {

    @NotBlank
    @Size(min = 1, max = 200)
    private String title;

    /**
     * 正文上限 20 万字：够一部长章节，同时挡住 1MB 级别的异常输入。
     * 没有上限时，一条请求就能把超大正文塞进数据库和 prompt。
     */
    @Size(max = 200000, message = "正文过长")
    private String content;
}
