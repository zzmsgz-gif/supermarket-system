package com.example.supermarket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台创建/编辑秒杀场次。时间字段用前端 datetime-local 的 "yyyy-MM-ddTHH:mm" 即可。 */
public class FlashSaleRequest {

    @NotNull(message = "请选择秒杀商品")
    private Long productId;

    @Size(max = 80, message = "场次名最多 80 个字符")
    private String name;

    @NotNull(message = "请填写秒杀价")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
    private BigDecimal flashPrice;

    @NotNull(message = "请填写秒杀名额")
    @Min(value = 1, message = "秒杀名额至少为 1")
    private Integer totalQuota;

    /** 每人限购件数，0 或不传表示不限购 */
    @Min(value = 0, message = "每人限购不能为负数")
    private Integer perUserLimit;

    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;

    private Integer status;

    private Integer sortNo;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getFlashPrice() {
        return flashPrice;
    }

    public void setFlashPrice(BigDecimal flashPrice) {
        this.flashPrice = flashPrice;
    }

    public Integer getTotalQuota() {
        return totalQuota;
    }

    public void setTotalQuota(Integer totalQuota) {
        this.totalQuota = totalQuota;
    }

    public Integer getPerUserLimit() {
        return perUserLimit;
    }

    public void setPerUserLimit(Integer perUserLimit) {
        this.perUserLimit = perUserLimit;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
