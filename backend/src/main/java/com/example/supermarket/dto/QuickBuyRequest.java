package com.example.supermarket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 「立即购买」下单入参：只买一件，且不写购物车表（与购物车下单解耦）。
 * 其余配送 / 优惠字段与 {@link CreateOrderRequest} 对齐，复用同一套建单逻辑。
 */
public class QuickBuyRequest implements OrderRequestLike {

    @NotNull(message = "商品 id 不能为空")
    private Long productId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少 1 件")
    private Integer quantity = 1;

    /** 规格描述（如「红色 / XL」），可为空 */
    private String skuSpec;

    private Long addressId;

    private String fulfillmentType;

    private Long pickupStoreId;

    @Size(max = 60, message = "Delivery slot must be at most 60 characters")
    private String deliverySlot;

    @Size(max = 500, message = "Remark must be at most 500 characters")
    private String remark;

    private Long userCouponId;

    private Boolean usePoints = Boolean.FALSE;

    private Long pointsToUse;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getSkuSpec() {
        return skuSpec;
    }

    public void setSkuSpec(String skuSpec) {
        this.skuSpec = skuSpec;
    }

    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public void setFulfillmentType(String fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public Long getPickupStoreId() {
        return pickupStoreId;
    }

    public void setPickupStoreId(Long pickupStoreId) {
        this.pickupStoreId = pickupStoreId;
    }

    public String getDeliverySlot() {
        return deliverySlot;
    }

    public void setDeliverySlot(String deliverySlot) {
        this.deliverySlot = deliverySlot;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public void setUserCouponId(Long userCouponId) {
        this.userCouponId = userCouponId;
    }

    public Boolean getUsePoints() {
        return usePoints;
    }

    public void setUsePoints(Boolean usePoints) {
        this.usePoints = usePoints;
    }

    public Long getPointsToUse() {
        return pointsToUse;
    }

    public void setPointsToUse(Long pointsToUse) {
        this.pointsToUse = pointsToUse;
    }
}
