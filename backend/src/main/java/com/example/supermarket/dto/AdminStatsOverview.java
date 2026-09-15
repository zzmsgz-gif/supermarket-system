package com.example.supermarket.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 后台数据统计概览（仪表盘）。
 * 全量聚合口径，与分页列表解耦；statusDistribution 的 key 为订单状态码。
 */
public record AdminStatsOverview(
        long productTotal,
        long stockTotal,
        long userTotal,
        long orderTotal,
        BigDecimal salesAmount,
        long todayOrderCount,
        BigDecimal todaySalesAmount,
        long pendingShipCount,
        long pendingPayCount,
        long refundApplyingCount,
        Map<String, Long> statusDistribution,
        List<TrendPoint> salesTrend
) {

    /** 近 7 天成交趋势的单日数据点 */
    public record TrendPoint(LocalDate date, long orderCount, BigDecimal salesAmount) {
    }
}
