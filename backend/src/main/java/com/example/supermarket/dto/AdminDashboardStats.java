package com.example.supermarket.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 经营数据看板。
 *
 * <p><b>口径说明（必须与「数据统计」首页保持一致，否则两个页面会互相打架）</b>：
 * 成交额/订单数一律按<b>订单创建时间</b>落在所选区间、且订单状态属于已支付集合来统计，
 * 与 {@code AdminStatsService.buildSalesTrend()} 的口径完全相同 —— 不按 paid_at 统计，
 * 因为历史订单可能缺 paid_at，会让两处数字对不上。
 *
 * <p>环比对照的是「紧邻的、等长的上一区间」（如近 7 天 vs 前 7 天）。
 */
public record AdminDashboardStats(
        String range,
        LocalDate fromDate,
        LocalDate toDate,
        int days,
        Trade trade,
        Growth growth,
        Users users,
        List<MemberLevelSlice> memberLevels,
        List<ProductSalesSlice> topProducts,
        List<CategorySlice> categoryShare,
        List<AdminStatsOverview.TrendPoint> trend,
        List<FlashSaleSlice> flashSales
) {

    /** 交易面 */
    public record Trade(
            BigDecimal gmv,
            long paidOrderCount,
            BigDecimal avgOrderValue,
            long refundCount,
            BigDecimal refundAmount,
            long pendingShipCount,
            long pendingPayCount
    ) {
    }

    /** 环比（百分比，保留一位小数；上期为 0 时返回 null 表示不可比） */
    public record Growth(Double gmvPercent, Double orderPercent, Double avgOrderPercent) {
    }

    /** 用户面。复购率＝区间内下单 ≥2 次的用户 / 区间内下过单的用户 */
    public record Users(
            long newUserCount,
            long buyerCount,
            long repeatBuyerCount,
            Double repurchaseRate
    ) {
    }

    public record MemberLevelSlice(int level, String name, long userCount) {
    }

    public record ProductSalesSlice(Long productId, String productName, long quantity, BigDecimal amount) {
    }

    public record CategorySlice(Long categoryId, String categoryName, long quantity,
                                BigDecimal amount, Double percent) {
    }

    public record FlashSaleSlice(Long id, String name, String productName, BigDecimal flashPrice,
                                 int totalQuota, int soldQuota, Double soldPercent,
                                 long soldQuantity, BigDecimal amount) {
    }
}
