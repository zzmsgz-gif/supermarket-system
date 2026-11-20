package com.example.supermarket.dto;

import java.math.BigDecimal;

/**
 * 结算金额预览（**只算不落库**）：给「立即购买」这类没有 {@code GET /cart} 可依的路径
 * 提供与下单同口径的权威金额。
 *
 * <p>为什么需要它（2026-11-19）：小程序的 quick 分支（商品详情「立即购买」）拿不到
 * {@code activityDiscount} —— 只有购物车接口会跑 {@code evaluateBestActivity}，
 * 于是结算页只减了券、没减活动。实测薯片×30=207 时活动「满200减50」生效，
 * 结算页显示 197、收银台 157，差 40 元。让前端自己复刻门槛计算必然与服务端分叉，
 * 所以把「金额怎么算」这件事留在一个地方（{@code OrderService.resolveOrderLines}），
 * 预览与真实下单共用。
 *
 * <p>口径与 {@code orders} 表完全一致：
 * {@code payAmount = goodsAmount - max(couponDiscount, activityDiscount) + freightAmount}，
 * 券与活动互斥取大、绝不双减；券门槛与活动门槛都只按 {@code nonFlashAmount} 判定。
 */
public class OrderPreviewResponse {

    /** 商品金额（秒杀价 / 会员价 / 等级折扣已逐件取优，含全部行） */
    private BigDecimal goodsAmount;
    /** 非秒杀部分小计 —— 活动与优惠券的门槛基数（秒杀行不参与） */
    private BigDecimal nonFlashAmount;
    /** 活动减免（按非秒杀基数匹配最优活动；无活动为 0） */
    private BigDecimal activityDiscount;
    private String activityName;
    /** 该券"若使用"能减多少（门槛已校验；**不消耗**） */
    private BigDecimal couponDiscount;
    /** 实际生效的那一档：max(券, 活动) */
    private BigDecimal bestPromo;
    /** COUPON / ACTIVITY / NONE —— 决定前端明细行显示哪一档的名字 */
    private String promoType;
    /** 运费（仅快递配送产生，按商品金额判 99 免邮） */
    private BigDecimal freightAmount;
    /** 本次会用掉的积分数（usePoints 为 false 时为 0）；**只是算，不冻结不扣减** */
    private Long pointsUsed;
    /** 积分抵扣金额 = pointsUsed / 100（100 积分 = 1 元） */
    private BigDecimal pointsDiscount;
    /** 应付 = goodsAmount − bestPromo − pointsDiscount + freightAmount */
    private BigDecimal payAmount;

    public BigDecimal getGoodsAmount() {
        return goodsAmount;
    }

    public void setGoodsAmount(BigDecimal goodsAmount) {
        this.goodsAmount = goodsAmount;
    }

    public BigDecimal getNonFlashAmount() {
        return nonFlashAmount;
    }

    public void setNonFlashAmount(BigDecimal nonFlashAmount) {
        this.nonFlashAmount = nonFlashAmount;
    }

    public BigDecimal getActivityDiscount() {
        return activityDiscount;
    }

    public void setActivityDiscount(BigDecimal activityDiscount) {
        this.activityDiscount = activityDiscount;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public BigDecimal getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(BigDecimal couponDiscount) {
        this.couponDiscount = couponDiscount;
    }


    public BigDecimal getBestPromo() {
        return bestPromo;
    }

    public void setBestPromo(BigDecimal bestPromo) {
        this.bestPromo = bestPromo;
    }

    public String getPromoType() {
        return promoType;
    }

    public void setPromoType(String promoType) {
        this.promoType = promoType;
    }

    public BigDecimal getFreightAmount() {
        return freightAmount;
    }

    public void setFreightAmount(BigDecimal freightAmount) {
        this.freightAmount = freightAmount;
    }

    public Long getPointsUsed() {
        return pointsUsed;
    }

    public void setPointsUsed(Long pointsUsed) {
        this.pointsUsed = pointsUsed;
    }

    public BigDecimal getPointsDiscount() {
        return pointsDiscount;
    }

    public void setPointsDiscount(BigDecimal pointsDiscount) {
        this.pointsDiscount = pointsDiscount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }
}
