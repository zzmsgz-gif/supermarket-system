package com.example.supermarket.service;

import com.example.supermarket.entity.Activity;
import java.math.BigDecimal;

/**
 * 一次订单/购物车内，针对当前商品清单「最优营销活动」的评估结果。
 * activity 为 null 表示没有命中任何活动；discount 为命中活动可减免的金额（>=0）。
 */
public class ActivityEvaluation {

    private final Activity activity;
    private final BigDecimal discount;

    public ActivityEvaluation(Activity activity, BigDecimal discount) {
        this.activity = activity;
        this.discount = discount;
    }

    public Activity getActivity() {
        return activity;
    }

    public BigDecimal getDiscount() {
        return discount;
    }
}
