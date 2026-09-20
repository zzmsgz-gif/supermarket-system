-- 存量库升级：把首页顶部利益条的「新人文案」从「公告」搬到「营销活动」
--
-- 背景：这条文案原本是前端硬编码（且数字写错：写着「立减 ¥20 + 3 张券」，实际是注册发 1 张满10减10 券），
-- 后来临时挂在 announcement(type=PROMOTION) 上 → 同一句话在「商城公告」栏与顶栏各显示一遍。
-- 现统一到 activity 表：
--   activity.type = 'PROMOTION' 表示「纯文案活动」—— 只占一个展示位、不参与计价
--   （ActivityService.computeDiscount 对未知 type 直接返回 0，因此它永远不会被选为「最优活动」，
--    满减/折扣的计价链路不受影响）。
--
-- 本脚本做两件事：
--   1) 插入这条纯文案活动（name 即顶栏滚动的那句话；threshold / discount 为 NULL）
--   2) 删除那条重复的公告，避免同一内容两处显示
--
-- 幂等：可重复执行（INSERT IGNORE + 带 type 条件的 DELETE）。

INSERT IGNORE INTO `activity`
    (id, name, type, scope, category_id, product_id, threshold, discount,
     start_time, end_time, status, priority, deleted, created_at, updated_at)
VALUES
    (28, '新人专享 满10减10', 'PROMOTION', 'ALL', NULL, NULL, NULL, NULL,
     '2026-01-01 00:00:00', '2030-12-31 23:59:59', 1, 0, 0, NOW(), NOW());

-- 删除「商城公告」栏里那条重复的新人福利公告（内容已由上面的活动承载）。
-- 带 type 条件是为了只删「促销类」那条，避免误删同名但已改作他用的公告。
DELETE FROM `announcement` WHERE id = 1 AND type = 'PROMOTION';
