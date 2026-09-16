package com.example.supermarket.dto;

/**
 * 申请提交结果。
 *
 * <p>⚠️ 防账号枚举：无论账号是否存在，文案都完全一致（由 PasswordResetService 里的常量给出）。
 * 只要存在「账号不存在」与「申请已提交」两种不同回复，接口就等于一个账号探测器。
 */
public class PasswordResetSubmitResponse {

    private boolean submitted;
    private String message;

    public PasswordResetSubmitResponse() {
    }

    public PasswordResetSubmitResponse(boolean submitted, String message) {
        this.submitted = submitted;
        this.message = message;
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public void setSubmitted(boolean submitted) {
        this.submitted = submitted;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
