-- 即时配送服务范围（门店维度）
--
-- 背景：此前即时配送没有任何范围校验，任何地址都能下单（包括隔壁城市），
-- 而页脚那个「配送范围」链接只是装饰性文字。
--
-- 判定模型：**即时配送可送达范围 = 所有营业中门店 service_areas 的并集**。
--   - 门店本来就已有结构化的 city / district，无需引入经纬度或地理编码；
--   - service_areas 留空时回退为「仅本店 city + district」，所以开箱即有效；
--   - 与「门店状态」耦合是有意为之：门店打烊/下线，其区域自然退出即时配送范围。
--   - 快递配送**不受影响**（全国可达，成本已由运费承担）。
-- 格式：逗号分隔的「城市/区县」，区县可省略表示全城，例如：
--   '深圳市/南山区,深圳市/福田区'   或   '深圳市'
ALTER TABLE store
    ADD COLUMN service_areas VARCHAR(255) NULL COMMENT '即时配送服务区域，逗号分隔；留空=仅本店city+district' AFTER district;

-- 存量门店显式回填成自身所在区域：与「留空回退」行为一致，但让后台表单里看得见、可编辑。
UPDATE store
SET service_areas = CONCAT(city, '/', district)
WHERE (service_areas IS NULL OR service_areas = '')
  AND city IS NOT NULL AND city <> ''
  AND district IS NOT NULL AND district <> '';
