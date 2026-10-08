package com.example.supermarket.dto;

/** 手机号换绑：申请验证码的返回 */
public class PhoneVerifyTicketResponse {

    /** 联调模式下后端回传的验证码（未接短信通道时才有值，生产应为空/null） */
    private String devCode;

    /** 有效期秒数 */
    private Integer expiresIn;

    public PhoneVerifyTicketResponse() {
    }

    public PhoneVerifyTicketResponse(String devCode, int expiresIn) {
        this.devCode = devCode;
        this.expiresIn = expiresIn;
    }

    public String getDevCode() {
        return devCode;
    }

    public void setDevCode(String devCode) {
        this.devCode = devCode;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
