-- 2026-10-04: orders 表按 (status, created_at) 加索引
-- 目的：加速超时关单定时任务（findByStatusAndCreatedAtBefore）与后台看板统计
--       （countByStatus / countByStatusInAndCreatedAtBetween）。
-- 注意：MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS（那是 MariaDB 扩展），
--       改用 information_schema.STATISTICS 计数 + PREPARE/EXECUTE 实现幂等。

SET @db = 'supermarket_system';
SET @tbl = 'orders';
SET @idx = 'idx_orders_status_created';

SELECT COUNT(*) INTO @c
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND INDEX_NAME = @idx;

SET @sql = IF(@c = 0,
    CONCAT('CREATE INDEX ', @idx, ' ON ', @tbl, ' (status, created_at)'),
    'SELECT ''index already exists''');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
