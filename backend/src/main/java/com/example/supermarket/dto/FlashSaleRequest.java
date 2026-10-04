package com.example.supermarket.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 后台创建/编辑秒杀场次。时间字段用前端 datetime-local 的 "yyyy-MM-ddTHH:mm" 即可。 */
public class FlashSaleRequest {

    /**
     * 秒杀商品来源，二选一：
     * <ul>
     *   <li><b>有值</b>＝基于现有商品（原商品），建场时克隆出一件独立的 FLASH 商品，原商品全程不动；</li>
     *   <li><b>为空</b>＝不依赖任何原商品，直接新建一件完全独立的秒杀商品，
     *       此时下面那组「独立秒杀商品」字段生效（productName/categoryId/price 必填）。</li>
     * </ul>
     */
    private Long productId;

    // ==================== 独立秒杀商品（productId 为空时生效） ====================

    /** 商品名称，不传则回落到场次名 */
    @Size(max = 120, message = "商品名称最多 120 个字符")
    private String productName;

    /** 所属分类（必填） */
    private Long categoryId;

    /** 售价（必填，秒杀价必须低于它；同时作为前台展示的划线基准价） */
    @DecimalMin(value = "0.01", message = "商品售价必须大于 0")
    private BigDecimal price;

    /** 划线原价（可选） */
    @DecimalMin(value = "0.01", message = "划线价必须大于 0")
    private BigDecimal originalPrice;

    /** 主图地址（可选，留空则前台走占位图） */
    @Size(max = 500, message = "主图地址最多 500 个字符")
    private String coverUrl;

    /** 单位，默认「件」 */
    @Size(max = 20, message = "单位最多 20 个字符")
    private String unit;

    /** 副标题（可选） */
    @Size(max = 255, message = "副标题最多 255 个字符")
    private String subtitle;

    /** 品牌（可选） */
    @Size(max = 100, message = "品牌最多 100 个字符")
    private String brand;

    // ==================== 场次本身 ====================

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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
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
