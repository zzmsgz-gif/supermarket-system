package com.example.supermarket.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 修复存量表缺失的时间列默认值。
 *
 * <p>背景：实体的 {@code created_at/updated_at} 为 {@code insertable=false}，插入时不带这两列，
 * 完全依赖数据库默认值。而 Hibernate 的 {@code ddl-auto=update} 属于增量更新，
 * <b>不会修改已存在列的定义</b>——若表是在实体尚未声明 {@code columnDefinition} 的旧版本下建出来的，
 * 这两列就没有 DEFAULT，任何插入都会报 {@code Field 'created_at' doesn't have a default value}。
 *
 * <p>本组件在启动时扫描 information_schema，只对确实缺默认值的列下发 ALTER，幂等且无副作用
 * （表结构本来正确时查询为空，直接跳过）。
 */
@Component
public class SchemaTimestampFixer {

    private static final Logger log = LoggerFactory.getLogger(SchemaTimestampFixer.class);

    /** 找出当前库中「非空、但没有默认值」的 created_at / updated_at 列。 */
    private static final String FIND_BROKEN_COLUMNS =
            "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() "
                    + "AND COLUMN_NAME IN ('created_at', 'updated_at') "
                    + "AND IS_NULLABLE = 'NO' "
                    + "AND COLUMN_DEFAULT IS NULL";

    private final DataSource dataSource;

    public SchemaTimestampFixer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 扫描并修复缺失默认值的时间列。表结构正常时为空操作。
     */
    public void fix() {
        List<String> alters = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(FIND_BROKEN_COLUMNS)) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                String column = rs.getString("COLUMN_NAME");
                if ("created_at".equals(column)) {
                    alters.add("ALTER TABLE `" + table + "` MODIFY COLUMN `created_at` "
                            + "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP");
                } else {
                    alters.add("ALTER TABLE `" + table + "` MODIFY COLUMN `updated_at` "
                            + "datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                }
            }
        } catch (Exception e) {
            log.warn("[schema-fix] 扫描时间列默认值失败，跳过自动修复：{}", e.getMessage());
            return;
        }

        if (alters.isEmpty()) {
            return;
        }

        log.warn("[schema-fix] 检测到 {} 个时间列缺少默认值，正在修复存量表结构…", alters.size());
        int fixed = 0;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            for (String sql : alters) {
                try {
                    st.executeUpdate(sql);
                    fixed++;
                } catch (Exception e) {
                    log.warn("[schema-fix] 修复失败（跳过该列）{} -> {}", sql, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[schema-fix] 获取连接失败，跳过自动修复：{}", e.getMessage());
            return;
        }
        log.info("[schema-fix] 时间列默认值修复完成：{}/{}", fixed, alters.size());
    }
}
