package com.example.supermarket.dto;

import com.example.supermarket.entity.Banner;

/** 轮播位响应。 */
public class BannerResponse {

    private Long id;
    private String imageUrl;
    private Long linkProductId;
    private Integer sortOrder;
    private Integer enabled;

    public static BannerResponse from(Banner b) {
        BannerResponse r = new BannerResponse();
        r.setId(b.getId());
        r.setImageUrl(b.getImageUrl());
        r.setLinkProductId(b.getLinkProductId());
        r.setSortOrder(b.getSortOrder());
        r.setEnabled(Integer.valueOf(b.getEnabled()));
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Long getLinkProductId() { return linkProductId; }
    public void setLinkProductId(Long linkProductId) { this.linkProductId = linkProductId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
