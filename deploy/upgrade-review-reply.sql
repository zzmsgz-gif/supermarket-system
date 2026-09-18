-- 评价管理：商家回复 + 违规隐藏
--
-- 背景：评价此前是**只写不用**的 —— 用户能写（晒图评价），但后台 15 个菜单里没有任何评价入口，
-- 也没有评价查询接口，评价只在商品详情页展示。商家看不到、回不了差评，闭环断在商家这一侧。
--
-- 三列的分工：
--   reply_content  商家回复正文，空 = 未回复。前台商品详情页要展示出来（否则回了也白回）。
--   reply_at       回复时间。
--   hidden         违规隐藏。**刻意不物理删除**：删掉会连带让 `existsByOrderId` 判定失效，
--                  用户就能对同一张订单重复评价；隐藏则保留"已评价"状态但前台不再展示。
-- ⚠️ 前台评价列表与**平均分聚合**都必须过滤 hidden=0 —— 否则会出现"列表里看不到、
--    平均分却被它拉低"的口径打架（ratingSummaryMap 与列表同源，必须一起改）。
ALTER TABLE product_review
    ADD COLUMN reply_content VARCHAR(500) DEFAULT NULL COMMENT '商家回复正文，空=未回复' AFTER image_urls,
    ADD COLUMN reply_at DATETIME DEFAULT NULL COMMENT '回复时间' AFTER reply_content,
    ADD COLUMN hidden TINYINT NOT NULL DEFAULT 0 COMMENT '1 前台不展示（违规隐藏，保留已评价状态）' AFTER reply_at;
