package com.example.supermarket.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * 云端空库首次部署时导入基础主数据（分类 / 商品 / 营销活动 / 优惠券）。
 *
 * <p>仅在 {@code product_category} 为空时才导入，因此重复部署不会覆盖用户后续维护的数据。
 * 种子脚本使用 INSERT IGNORE，即使重复执行也不会产生重复行。
 * 业务流水表（订单、支付、钱包等）与依赖订单/用户的评价表不在种子范围内。
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

    public void seedIfEmpty() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM product_category")) {
            if (rs.next() && rs.getLong(1) > 0) {
                return;
            }
        } catch (Exception e) {
            log.warn("[seed] 检查种子数据条件失败，跳过导入：{}", e.getMessage());
            return;
        }

        log.info("[seed] 检测到空库，开始导入初始数据（分类/商品/活动/优惠券）…");
        try {
            ResourceDatabasePopulator populator =
                    new ResourceDatabasePopulator(new ClassPathResource(SEED_RESOURCE));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
            log.info("[seed] 初始数据导入完成");
        } catch (Exception e) {
            log.warn("[seed] 初始数据导入失败（不影响启动）：{}", e.getMessage());
        }
    }
}
