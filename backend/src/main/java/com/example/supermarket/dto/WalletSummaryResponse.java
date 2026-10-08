package com.example.supermarket.dto;

import java.math.BigDecimal;

/**
 * 金额明细汇总：累计支出 / 累计收入。
 *
 * <p><b>刻意只有两项，且不接筛选</b>：
 * <ul>
 *   <li>只统计**真正的资金进出**（订单支付 / 退款 / 充值），
 *       <b>不含运费、优惠、积分</b> —— 那些已经包含在订单实付金额里，
 *       再单拎出来加一遍就是**重复计算**（2026-10-09 用户指出的问题）。</li>
 *   <li>不随筛选变化：用户看的是总账。</li>
 * </ul>
 *
 * <p>「这单省了多少」属于**订单的金额构成**，在订单详情里看，
 * 不是资金流水的一项。
 */
public class WalletSummaryResponse {

    /** 累计支出（订单支付总额，已含运费、已扣优惠） */
    private BigDecimal totalOut;

    /** 累计收入（退款 + 充值） */
    private BigDecimal totalIncome;

    public WalletSummaryResponse() {
    }

    public WalletSummaryResponse(BigDecimal totalOut, BigDecimal totalIncome) {
        this.totalOut = totalOut;
        this.totalIncome = totalIncome;
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
}
