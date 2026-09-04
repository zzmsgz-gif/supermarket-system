package com.example.supermarket.dto;

import com.example.supermarket.entity.ProductCategory;

public class CategoryResponse {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sortNo;

    public CategoryResponse() {
    }

    private CategoryResponse(Long id, Long parentId, String name, Integer sortNo) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.sortNo = sortNo;
    }

    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getName(),
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

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
