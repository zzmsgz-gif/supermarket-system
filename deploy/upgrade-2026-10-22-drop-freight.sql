-- ============================================================================
-- 2026-10-22 移除「运费承担方」概念（freightBorneBy 全站删除）
-- ----------------------------------------------------------------------------
-- 背景：售后流程不再区分退货运费由谁承担（SELLER / BUYER），
--       退货运费统一按平台规则处理。后端实体 / DTO / Service、前端管理端与用户端
--       均已删除该字段与所有相关 UI（审核下拉、列表运费列、用户端运费提示）。
--       本脚本负责把生产库 return_request 表上残留的列与约束清理掉。
--
-- 风险：低风险。该列仅由旧代码的审核逻辑写入/读取，新代码已不再引用；
--       应用层在 delete-column 之后再无对 freight_borne_by 的 SQL，可安全移除。
--       执行前请确认已部署不含该字段的新 jar（旧 jar 仍会 INSERT/UPDATE 该列，
--       列不存在时会报 Unknown column，故务必先停旧服务再跑本脚本）。
-- ============================================================================

-- 1) 先删依赖该列的 CHECK 约束（MySQL 8 用 DROP CHECK 删 CHECK 约束）
ALTER TABLE return_request DROP CHECK chk_return_freight;

-- 2) 再删列
ALTER TABLE return_request DROP COLUMN freight_borne_by;
