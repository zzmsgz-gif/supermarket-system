package com.example.supermarket.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CreateOrderRequest implements OrderRequestLike {

    @NotEmpty(message = "Cart item ids are required")
    private List<Long> cartItemIds;

    /** 送货上门时必填；门店自提时可为空（由 service 按 fulfillmentType 条件校验） */
    private Long addressId;

    /** DELIVERY（默认）送货上门 / PICKUP 门店自提 */
    private String fulfillmentType;

    /** 门店自提时必填：自提门店 id */
    private Long pickupStoreId;

    /** 送货上门可选：期望配送时段，如 2026-09-15 09:00-11:00 */
    @Size(max = 60, message = "Delivery slot must be at most 60 characters")
    private String deliverySlot;

    @Size(max = 500, message = "Remark must be at most 500 characters")
    private String remark;

    private Long userCouponId;

    /** 是否使用积分抵扣 */
    private Boolean usePoints = Boolean.FALSE;

    /** 希望使用的积分数；为 null 且 usePoints=true 时表示尽可能多用（受余额与上限约束） */
    private Long pointsToUse;

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

    public List<Long> getCartItemIds() {
        return cartItemIds;
    }

    public void setCartItemIds(List<Long> cartItemIds) {
        this.cartItemIds = cartItemIds;
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
}
