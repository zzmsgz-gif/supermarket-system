package com.example.supermarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台热搜词创建/编辑请求。
 *
 * <p>为什么有 {@code keyword} 和 {@code label} 两个字段：展示文案和实际搜索词**经常不是一个词** ——
 * 原来硬编码那几条就是「显示『纯牛奶』、点下去搜『牛奶』」。
 * {@code label} 留空时前台回落成 {@code keyword}，所以只填一个也能用。
 */
public class HotSearchRequest {

    @NotBlank(message = "搜索词不能为空")
    @Size(max = 30, message = "搜索词最长 30 字")
    private String keyword;

    /** 展示文案，可空；空则前台直接用 keyword 显示 */
    @Size(max = 30, message = "展示文案最长 30 字")
    private String label;

    private Integer sortOrder = 0;

    private Boolean enabled = true;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
