package com.example.supermarket.dto;

import java.util.List;

/**
 * 「再来一单」的结果。
 *
 * <p>不能只回一个"成功/失败"：历史订单里往往有商品已经下架或售罄，而那些**能**买的必须照买。
 * 所以逐件处理、逐件汇报，让用户清楚"加进来几件、哪几件没加、为什么"。
 */
public class ReorderResultResponse {

    /** 成功加入购物车的商品种类数（同商品不同规格算多条） */
    private int addedCount;
    /** 没能加入的商品及原因，空列表代表全部成功 */
    private List<ReorderSkippedResponse> skipped;

    public ReorderResultResponse() {
    }

    public ReorderResultResponse(int addedCount, List<ReorderSkippedResponse> skipped) {
        this.addedCount = addedCount;
        this.skipped = skipped;
    }

    public int getAddedCount() {
        return addedCount;
    }

    public void setAddedCount(int addedCount) {
        this.addedCount = addedCount;
    }

    public List<ReorderSkippedResponse> getSkipped() {
        return skipped;
    }

    public void setSkipped(List<ReorderSkippedResponse> skipped) {
        this.skipped = skipped;
    }

    /** 前端提示文案要用到，省得自己算长度 */
    public int getSkippedCount() {
        return skipped == null ? 0 : skipped.size();
    }
}
