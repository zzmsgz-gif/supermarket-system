-- 售后「退款原因」活人感回填（2026-10-12）
--
-- 背景：后台「售后管理」的退款原因列读 orders.refund_reason，而历史数据（生成器写入）
-- 只把原因落在 return_request.reason 上、且仅有 4 种通用文案，导致后台几乎全部显示「未填写」。
-- 本脚本做两件事：
--   1) 把 4 种通用原因按大类细化为多条真人语气的具体原因（按 id 取模 → 确定性、可重复执行）；
--   2) 把 return_request.reason 同步进 orders.refund_reason，保证两张表口径一致。
-- ⚠️ 幂等：只改 reason 仍为那 4 种通用值的行、以及 refund_reason 为空的行；
--    用户此后自己填写的真实原因不会被覆盖。

START TRANSACTION;

UPDATE return_request
SET reason = CASE
  WHEN reason = '不喜欢/不想要了' THEN ELT(1 + (id MOD 10),
    '突然不想要了，家里还有存货',
    '冲动下单，回头想想没必要',
    '凑单买的，优惠够了这份就不需要了',
    '家里人不吃这个口味，买了也是浪费',
    '拍错人数，多出来的这份用不上',
    '换成别的牌子了，这个先退掉',
    '看完评价心里没底，还是退了吧',
    '收到后发现跟我以为的不一样',
    '买给朋友的，她说用不上',
    '下单才发现家里还剩大半袋')
  WHEN reason = '质量问题' THEN ELT(1 + (id MOD 10),
    '拆开就闻到一股酸味，不敢吃',
    '还在保质期内却已经结块变质',
    '刚用两次就坏了，质量太差',
    '瓶口密封不严，漏了一半出来',
    '打开后发现里面混了异物',
    '颜色发暗，看着就不像新鲜的',
    '跟宣传的完全不是一个档次',
    '同一批次上次买的就没这个问题',
    '做工粗糙，边角处理得很差',
    '用了根本达不到描述的效果')
  WHEN reason = '尺寸不合适' THEN ELT(1 + (id MOD 6),
    '买小了，穿着有点紧',
    '买大了，家里地方放不下',
    '量过才发现尺寸对不上',
    '比家里现有的大了两号',
    '孩子戴不了，尺寸偏大',
    '拆开发现发错规格了')
  WHEN reason = '收到时破损' THEN ELT(1 + (id MOD 5),
    '快递箱被压变形，里面也瘪了',
    '到手就裂了一角，完全没法用',
    '外包装看着是好的，里面却碎了',
    '瓶身裂了一道，液体都漏出来了',
    '包装盒压扁了，送人拿不出手')
  ELSE reason
END
WHERE reason IN ('不喜欢/不想要了', '质量问题', '尺寸不合适', '收到时破损');

-- 同步到后台列表真正读取的列
UPDATE orders o
JOIN return_request r ON r.order_id = o.id
SET o.refund_reason = r.reason
WHERE o.refund_status <> 'NONE'
  AND (o.refund_reason IS NULL OR o.refund_reason = '');

-- 兜底：有退款状态但没有售后单的老订单（历史直接改 refund_status 留下的）
UPDATE orders
SET refund_reason = ELT(1 + (id MOD 10),
  '突然不想要了，麻烦取消吧',
  '拍错了商品，想重新下单',
  '发货太慢，实在等不了了',
  '别的地方便宜不少，去那边买',
  '商品和描述对不上，不敢要',
  '家里人已经买过了，买重了',
  '临时改主意，想换个牌子',
  '收货地址填错了，打算重拍',
  '优惠券没用上，这单先退掉',
  '凑单没凑齐，这几件都不想要了')
WHERE refund_status <> 'NONE'
  AND (refund_reason IS NULL OR refund_reason = '');

COMMIT;

-- 复核用
SELECT reason, COUNT(*) AS cnt FROM return_request GROUP BY reason ORDER BY cnt DESC;
SELECT SUM(refund_reason IS NULL OR refund_reason = '') AS orders_still_empty FROM orders WHERE refund_status <> 'NONE';
