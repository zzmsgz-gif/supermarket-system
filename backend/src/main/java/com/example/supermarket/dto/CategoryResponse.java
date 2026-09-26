package com.example.supermarket.dto;

import com.example.supermarket.entity.ProductCategory;

public class CategoryResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String iconUrl;
    private Integer sortNo;

    public CategoryResponse() {
    }

    private CategoryResponse(Long id, Long parentId, String name, String iconUrl, Integer sortNo) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.iconUrl = iconUrl;
        this.sortNo = sortNo;
    }

    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getIconUrl(),
                category.getSortNo()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
