package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 用户填写寄回信息 */
public class ReturnShipRequest {

    @NotBlank(message = "请填写快递公司")
    @Size(max = 40, message = "快递公司过长")
    private String expressCompany;

    @NotBlank(message = "请填写运单号")
    @Size(max = 64, message = "运单号过长")
    private String trackingNo;

    public String getExpressCompany() {
        return expressCompany;
    }

    public void setExpressCompany(String expressCompany) {
        this.expressCompany = expressCompany;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }
}
