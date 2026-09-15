package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;

/** 后台轮播位创建/编辑请求：纯图片，不叠加文字。 */
public class BannerRequest {

    @NotBlank(message = "请先上传轮播图片")
    private String imageUrl;

    private Long linkProductId;

    private Integer sortOrder = 0;

    private Boolean enabled = true;

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getLinkProductId() { return linkProductId; }
    public void setLinkProductId(Long linkProductId) { this.linkProductId = linkProductId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
