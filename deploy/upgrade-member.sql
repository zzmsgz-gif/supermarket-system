-- 会员积分体系迁移（存量库升级）
-- 执行：mysql -u root -p<密码> supermarket_system < deploy/upgrade-member.sql

ALTER TABLE sys_user
  ADD COLUMN points BIGINT NOT NULL DEFAULT 0 COMMENT '会员积分余额',
  ADD COLUMN member_level INT NOT NULL DEFAULT 0 COMMENT '会员等级 0普通/1银卡/2金卡/3钻石',
  ADD COLUMN total_spent DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费金额(用于升级)';

ALTER TABLE product
  ADD COLUMN member_price DECIMAL(10,2) DEFAULT NULL COMMENT '会员价(选填,低于售价时会员按此价结算)';

ALTER TABLE orders
  ADD COLUMN member_discount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '会员等级折扣金额',
  ADD COLUMN points_used BIGINT NOT NULL DEFAULT 0 COMMENT '本单抵扣积分',
  ADD COLUMN points_earned BIGINT NOT NULL DEFAULT 0 COMMENT '本单获得积分',
  ADD COLUMN member_level INT NOT NULL DEFAULT 0 COMMENT '下单时会员等级';

CREATE TABLE IF NOT EXISTS point_ledger (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL COMMENT 'EARN/REDEEM/REFUND/ADJUST',
  amount BIGINT NOT NULL COMMENT '本次变动绝对值',
  balance_after BIGINT NOT NULL COMMENT '变动后积分余额',
  ref_order_id BIGINT DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_point_ledger_user (user_id),
  INDEX idx_point_ledger_order (ref_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员积分流水';
