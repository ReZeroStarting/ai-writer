package com.aiwriter.service;

import com.aiwriter.exception.ForbiddenException;
import com.aiwriter.exception.NotFoundException;

import java.util.Optional;
import java.util.function.Function;

/**
 * 实体归属校验的统一入口。
 *
 * <p>多数 Service 里都有一段完全相同的「取记录 → 不存在报 404 → 不属于自己报 403」三段式，
 * 分散复制容易出现漏写归属判断（历史上就发生过越权读取）。这里收敛成一处，
 * 保证「先判存在、再判归属」的顺序与错误语义在所有入口保持一致。
 *
 * <p>用法：
 * <pre>{@code
 * private Project findOwned(Long userId, Long projectId) {
 *     return OwnershipGuard.requireOwned(
 *             projectRepo.findById(projectId), userId,
 *             p -> p.getUser().getId(), "项目", "项目");
 * }
 * }</pre>
 *
 * <p>注意：本类只负责「实体直接归属于某个用户」这一类校验。
 * 「子资源是否属于某个已校验归属的父资源」是另一类语义（例如元素是否属于某框架），
 * 不适用此工具，仍应各自显式校验。
 */
public final class OwnershipGuard {

    private OwnershipGuard() {
    }

    /**
     * 校验实体存在且归属于指定用户。
     *
     * @param found       仓储查询结果
     * @param userId      当前登录用户 id
     * @param ownerGetter 从实体取出归属用户 id
     * @param name        实体名词，用于「XX不存在」提示
     * @param pronoun     实体代词，用于「无权操作此XX」提示
     * @return 已校验归属的实体
     * @throws NotFoundException  实体不存在
     * @throws ForbiddenException 实体存在但不属于该用户
     */
    public static <T> T requireOwned(Optional<T> found,
                                     Long userId,
                                     Function<T, Long> ownerGetter,
                                     String name,
                                     String pronoun) {
        T entity = found.orElseThrow(() -> new NotFoundException(name + "不存在"));
        Long ownerId = ownerGetter.apply(entity);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new ForbiddenException("无权操作此" + pronoun);
        }
        return entity;
    }
}
