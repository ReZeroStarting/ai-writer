package com.aiwriter.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("乐观锁冲突映射成 409，并带可读原因与错误码")
    void mapsOptimisticLockTo409() {
        var res = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("Document", 1L));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody()).containsEntry("code", "CONCURRENT_MODIFICATION");
        assertThat(res.getBody().get("error")).contains("已被其他会话修改");
    }

    @Test
    @DisplayName("参数类型不对映射成 400，而不是 500")
    void mapsTypeMismatchTo400() {
        var e = new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, null);
        var res = handler.handleTypeMismatch(e);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("error")).contains("参数格式不正确");
    }
}
