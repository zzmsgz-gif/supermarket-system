-- 找回密码（忘记密码）+ 强制改密（存量库升级）
--
-- 内容：
--   1) sys_user.must_change_password —— 管理员重置后的强制改密标记
--   2) password_reset_request 表 —— 找回密码申请（人工审核路线）
--
-- 为什么走人工审核而不是自助重置：本项目没有邮件 / 短信通道。
-- 任何"填个手机号就能重置"的实现在没有验证码校验时，等于把账号送给知道用户名的人。
-- 因此只受理申请，管理员核对身份后生成一次性临时密码，用户首次登录强制改密。
--
-- 执行：mysql -uroot -p<密码> --default-character-set=utf8mb4 -D supermarket_system < deploy/upgrade-password-reset.sql
-- 注意：必须带 --default-character-set=utf8mb4，否则中文注释会报 ERROR 1366

ALTER TABLE sys_user
  ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0
  COMMENT '1=管理员重置成临时密码后待强制改密' AFTER last_login_at;

-- 临时密码只在重置响应里返回一次，库里只存 BCrypt 哈希，不保存明文（管理员事后也查不到）。
-- 账号不存在时接口同样返回成功文案，但**不落库**（user_id 为空是为将来接邮件通道预留）。
CREATE TABLE IF NOT EXISTS password_reset_request (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT DEFAULT NULL COMMENT '命中的账号 id；账号不存在时为 NULL',
  username VARCHAR(50) NOT NULL COMMENT '用户提交的账号',
  contact VARCHAR(50) DEFAULT NULL COMMENT '用户留下的联系电话（客服核对/回拨用）',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / DONE / REJECTED',
  handled_by BIGINT DEFAULT NULL COMMENT '处理人（管理员 id）',
  handled_at DATETIME DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL COMMENT '处理备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_prr_status_created (status, created_at),
  KEY idx_prr_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='找回密码申请';
