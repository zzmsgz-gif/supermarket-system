package com.example.supermarket.dto;

/** 「再来一单」里没能加进购物车的商品，以及原因（已下架 / 已售罄 / 库存不足 / 超出限购）。 */
public class ReorderSkippedResponse {

    private String productName;
    private String reason;

    public ReorderSkippedResponse() {
    }

    public ReorderSkippedResponse(String productName, String reason) {
        this.productName = productName;
        this.reason = reason;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
