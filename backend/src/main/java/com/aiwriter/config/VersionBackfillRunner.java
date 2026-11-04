package com.aiwriter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 给已有文档补 {@code version} 默认值。
 *
 * <p>背景：`documents` 表是在没有乐观锁时建的，新增 `version` 列后老数据是 NULL，
 * 而 `@Version` 要求非空。这里在启动时做一次幂等回填（`WHERE version IS NULL`），
 * 之后新写入的行由 Hibernate 自己维护版本号。
 *
 * <p>为什么不用 Flyway：项目当前仍是 `ddl-auto: update`，引入迁移工具是另一件事；
 * 这个回填只做一次、可重复执行、失败只记日志（不阻塞启动）。
 */
@Component
public class VersionBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VersionBackfillRunner.class);

    private final JdbcTemplate jdbc;

    public VersionBackfillRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int fixed = jdbc.update("UPDATE documents SET version = 0 WHERE version IS NULL");
            if (fixed > 0) log.info("已为 {} 条历史文档回填 version=0", fixed);
        } catch (Exception e) {
            log.warn("文档 version 回填跳过（表或列尚不存在？）: {}", e.getMessage());
        }
    }
}
