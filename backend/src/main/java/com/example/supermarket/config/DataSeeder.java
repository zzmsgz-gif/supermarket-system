package com.example.supermarket.config;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
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
    }
}
