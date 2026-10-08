package com.example.supermarket.dto;

import java.math.BigDecimal;

/**
 * 金额明细的三项汇总（累计支出 / 累计退回 / 优惠省下）。
 *
 * <p><b>为什么单独一个接口而不是塞进分页返回</b>：汇总的含义是「总账」，
 * 刻意不随筛选 tab 变化（切到「退款」标签时，累计支出仍应是全部支出）。
 * 塞进分页响应里就会被当成「当前筛选的小计」，反而误导。
 */
public class AmountSummaryResponse {

    /** 累计支出（订单支付 + 运费） */
    private BigDecimal totalOut;

    /** 累计退回（退款到账） */
    private BigDecimal totalIncome;

    /** 优惠省下（券 + 活动 + 会员 + 积分抵扣，不含退款） */
    private BigDecimal totalSaved;

    public AmountSummaryResponse() {
    }

    public AmountSummaryResponse(BigDecimal totalOut, BigDecimal totalIncome, BigDecimal totalSaved) {
        this.totalOut = totalOut;
        this.totalIncome = totalIncome;
        this.totalSaved = totalSaved;
    }

    public BigDecimal getTotalOut() {
        return totalOut;
    }

    public void setTotalOut(BigDecimal totalOut) {
        this.totalOut = totalOut;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalSaved() {
        return totalSaved;
    }

    public void setTotalSaved(BigDecimal totalSaved) {
        this.totalSaved = totalSaved;
    }
}
