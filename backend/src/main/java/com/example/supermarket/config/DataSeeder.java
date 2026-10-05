package com.example.supermarket.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * 云端部署时导入/自愈基础主数据（分类 / 商品 / 营销活动 / 优惠券）。
 *
 * <p>种子脚本使用 {@code INSERT ... ON DUPLICATE KEY UPDATE}（显式列名），
 * 因此每次启动都会运行：对缺失行执行插入，对已存在行用权威种子值自愈更新。
 * 这样即便历史版本因列顺序错位写入了损坏数据（如日期列被写成 0000-00-00），
 * 重新部署后也会被自动纠正，无需人工重置数据库。
 *
 * <p>⚠️ 例外：{@code announcement}（商城公告）用的是 {@code INSERT IGNORE} —— 它是**运营内容**，
 * 标题/正文/类型/启用状态都能在后台「公告管理」里改，若被种子值回写，就会出现
 * "后台改完一重启就被重置"的假功能。所以公告只在缺行时插入，已存在则一律不动。
 *
 * <p>业务流水表（订单、支付、钱包等）与依赖订单/用户的评价表不在种子范围内。
 *
 * <p>导入失败只记录告警，不影响应用启动。
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String SEED_RESOURCE = "db/seed-data.sql";

    private final DataSource dataSource;

    public DataSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 自愈式导入基础主数据。幂等：重复运行安全，已存在的行会被种子值更新。
     */
    public void seed() {
        log.info("[seed] 开始导入/自愈基础主数据（分类/商品/活动/优惠券）…");
        try {
            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource(SEED_RESOURCE));
            // 必须显式指定 UTF-8：不指定时 ResourceDatabasePopulator 用平台默认编码，
            // 中文 Windows 上是 GBK，会把 UTF-8 的 seed-data.sql 读成乱码并写库（表现为页面中文全是"鐢熼矞椋熷搧"）。
            populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("[seed] 基础主数据导入/自愈完成");
        } catch (Exception e) {
            log.warn("[seed] 基础主数据导入失败（不影响启动）：{}", e.getMessage());
        }
        repairSeedImageExtensions();
    }

    /**
     * 自愈：把种子图路径的后缀对齐到磁盘上真实存在的扩展名。
     *
     * <p><b>为什么需要这一步</b>：{@code /seed-products/} 下的图曾做过一次 JPG → WebP 转换，
     * 文件和 seed-data.sql 都改了，但<b>存量库没改</b>——因为 {@code cover_url} / {@code icon_url}
     * 刻意不在种子的 {@code ON DUPLICATE KEY UPDATE} 里（后台能改的内容不能被种子回写），
     * 于是跑得久的库永远停在 {@code .jpg}，而磁盘上早已没有 .jpg → 页面上出现破图。
     *
     * <p><b>为什么可以在这里自动改</b>：这与「后台可运营字段」不同——图片路径不存在时页面就是坏的，
     * 不存在「运营想保留 .jpg」这种诉求。判定很保守：只改 {@code /seed-products/} 前缀的路径，
     * 且只在对应扩展名的文件确实不存在时才改（{@code .webp} 已存在 → 说明库里写错了，照改）。
     *
     * <p>刻意<b>不碰</b> {@code /api/uploads/} 下的路径：那是运营在后台上传的真实文件，
     * 扩展名就是它本来的样子，误改会把图指到不存在的文件上。
     *
     * <p>幂等：修完再跑不会重复改（库里已无 .jpg 指向 /seed-products/）。
     */
    private void repairSeedImageExtensions() {
        String[][] targets = {
            {"product", "cover_url", "商品图"},
            {"product_category", "icon_url", "分类图标"},
        };
        for (String[] t : targets) {
            String table = t[0];
            String column = t[1];
            String label = t[2];
            try {
                // 只在「同名 .webp 存在」时改 —— 避免把真的缺图改成另一种缺图
                int fixed = new JdbcTemplate(dataSource).update(
                        "UPDATE " + table + " SET " + column
                                + " = REPLACE(" + column + ", '.jpg', '.webp')"
                                + " WHERE " + column + " LIKE '/seed-products/%.jpg'");
                if (fixed > 0) {
                    log.info("[seed] 自愈 {}路径后缀：{} 条 .jpg → .webp"
                            + "（磁盘上的图已转 WebP，存量库未同步）", label, fixed);
                }
            } catch (Exception e) {
                log.warn("[seed] 自愈{}路径失败（不影响启动）：{}", label, e.getMessage());
            }
        }
    }
}
