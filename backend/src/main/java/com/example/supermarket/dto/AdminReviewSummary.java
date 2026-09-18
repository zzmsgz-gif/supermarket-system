package com.example.supermarket.dto;

import java.util.List;

/**
 * 后台评价管理的汇总，给页面顶部用。
 *
 * <p>{@code unrepliedCount} 是这一页真正的行动信号 —— 没有它，商家得自己翻页找没回过的评价。
 */
public record AdminReviewSummary(
        long total,
        long hiddenCount,
        long unrepliedCount,
        Double avgRating,
        List<StarSlice> stars
) {

    /** 单个星级的评价数 */
    public record StarSlice(int rating, long count) {
    }
}
