package com.example.supermarket.dto;

/**
 * 即时配送范围查询结果。
 *
 * <p>结算页要在用户点「提交订单」之前就给出提示 —— 与项目里既有的
 * 「别等结算才提示」同一条原则，不能只靠后端下单时返 409。
 */
public class DeliveryRangeResponse {

    /** 该地址是否在即时配送范围内 */
    private boolean deliverable;
    /** 当前覆盖范围（人工可读），用于提示文案 */
    private String coverage;

    public DeliveryRangeResponse() {
    }

    public DeliveryRangeResponse(boolean deliverable, String coverage) {
        this.deliverable = deliverable;
        this.coverage = coverage;
    }

    public boolean isDeliverable() {
        return deliverable;
    }

    public void setDeliverable(boolean deliverable) {
        this.deliverable = deliverable;
    }

    public String getCoverage() {
        return coverage;
    }

    public void setCoverage(String coverage) {
        this.coverage = coverage;
    }
}
