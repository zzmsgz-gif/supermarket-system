package com.example.supermarket.dto;

/**
 * 建单入参的公共形状：{@link CreateOrderRequest}（来自购物车）与
 * {@link QuickBuyRequest}（单品立即购买，不落购物车）都实现本接口，
 * 这样 {@code OrderService.buildOrderFromItems} 可以复用同一套建单逻辑，
 * 而「查/删购物车」只发生在购物车下单路径。
 */
public interface OrderRequestLike {

    /** 履约类型：DELIVERY / PICKUP / EXPRESS（null 时后端按默认履约处理） */
    String getFulfillmentType();

    /** 门店自提时必填 */
    Long getPickupStoreId();

    /** 送货上门时必填 */
    Long getAddressId();

    /** 同城即时配送可选时段 */
    String getDeliverySlot();

    String getRemark();

    Long getUserCouponId();

    Boolean getUsePoints();

    Long getPointsToUse();
}
