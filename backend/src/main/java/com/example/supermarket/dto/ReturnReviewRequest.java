package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 商家审核售后单 */
public class ReturnReviewRequest {

    /** 同意还是驳回 */
    private Boolean approved;

    /** 审核意见（驳回时必填 —— 驳回不给理由用户会反复申诉） */
    @Size(max = 500)
    private String remark;

    /**
     * 是否需要用户寄回。
     * 质量问题 / 错发 / 损坏一律 false —— 让用户为了几十块钱来回寄件是体验灾难。
     */
    private Boolean needReturn;

    /**
     * 运费责任方：SELLER / BUYER。
     * <p>needReturn=true 时必填（SELLER=商家出回程运费，BUYER=用户自己承担）。
     */
    @Size(max = 10)
    private String freightBorneBy;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Boolean getNeedReturn() {
        return needReturn;
    }

    public void setNeedReturn(Boolean needReturn) {
        this.needReturn = needReturn;
    }

    public String getFreightBorneBy() {
        return freightBorneBy;
    }

    public void setFreightBorneBy(String freightBorneBy) {
        this.freightBorneBy = freightBorneBy;
    }
}
