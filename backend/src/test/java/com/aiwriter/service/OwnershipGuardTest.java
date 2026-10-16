package com.aiwriter.service;

import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;
import com.aiwriter.model.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OwnershipGuard 单元测试。
 *
 * 重点有两处：
 * 1. 错误文案必须与重构前逐字一致（前端与既有测试都依赖这两句话）；
 * 2. 归属判断缺失（owner 为 null）时必须拒绝，不能因为 NPE 侥幸放过或误放行。
 */
class OwnershipGuardTest {

    static class Entity {
        private final Long ownerId;
        Entity(Long ownerId) { this.ownerId = ownerId; }
        Long ownerId() { return ownerId; }
    }

    private static final Long USER = 1L;
    private static final Long OTHER = 2L;

    @Test
    void shouldReturnEntityWhenOwned() {
        Entity e = new Entity(USER);
        Entity got = OwnershipGuard.requireOwned(Optional.of(e), USER, Entity::ownerId, "项目", "项目");
        assertSame(e, got);
    }

    @Test
    void shouldThrowNotFoundWhenAbsent() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> OwnershipGuard.requireOwned(Optional.empty(), USER, Entity::ownerId, "项目", "项目"));
        assertEquals("项目不存在", ex.getMessage());
    }

    @Test
    void shouldThrowForbiddenWhenOwnedByOther() {
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(new Entity(OTHER)), USER,
                        Entity::ownerId, "项目", "项目"));
        assertEquals("无权操作此项目", ex.getMessage());
    }

    /** owner 为 null 属于数据异常，必须拒绝而不是放行。 */
    @Test
    void shouldRejectWhenOwnerIsNull() {
        assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(new Entity(null)), USER,
                        Entity::ownerId, "文档", "文档"));
    }

    /** 逐字锁定各调用点使用的文案，防止重构悄悄改变前端提示。 */
    @Test
    void errorMessagesMustMatchLegacyWording() {
        assertEquals("项目不存在", assertThrows(NotFoundException.class,
                () -> OwnershipGuard.requireOwned(Optional.empty(), USER, Entity::ownerId, "项目", "项目"))
                .getMessage());
        assertEquals("框架不存在", assertThrows(NotFoundException.class,
                () -> OwnershipGuard.requireOwned(Optional.empty(), USER, Entity::ownerId, "框架", "框架"))
                .getMessage());
        assertEquals("文档不存在", assertThrows(NotFoundException.class,
                () -> OwnershipGuard.requireOwned(Optional.empty(), USER, Entity::ownerId, "文档", "文档"))
                .getMessage());
        assertEquals("风格模板不存在", assertThrows(NotFoundException.class,
                () -> OwnershipGuard.requireOwned(Optional.empty(), USER, Entity::ownerId, "风格模板", "模板"))
                .getMessage());

        Entity other = new Entity(OTHER);
        assertEquals("无权操作此项目", assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(other), USER, Entity::ownerId, "项目", "项目"))
                .getMessage());
        assertEquals("无权操作此框架", assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(other), USER, Entity::ownerId, "框架", "框架"))
                .getMessage());
        assertEquals("无权操作此文档", assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(other), USER, Entity::ownerId, "文档", "文档"))
                .getMessage());
        assertEquals("无权操作此模板", assertThrows(ForbiddenException.class,
                () -> OwnershipGuard.requireOwned(Optional.of(other), USER, Entity::ownerId, "风格模板", "模板"))
                .getMessage());
    }

    /** 与真实 User 实体结合的一次冒烟，确认取值链路正确。 */
    @Test
    void shouldWorkWithRealUserEntity() {
        User owner = User.builder().id(USER).build();
        assertTrue(OwnershipGuard.requireOwned(Optional.of(owner), USER, User::getId, "用户", "用户") == owner);
    }
}
