package com.example.supermarket.dto;

import com.example.supermarket.entity.HotSearch;

public class HotSearchResponse {

    private Long id;
    /** 实际用于搜索的关键词（点击后走 /shop?kw=） */
    private String keyword;
    /** 前台展示文案；DB 里为空时这里已回落成 keyword，所以前端直接用即可 */
    private String label;
    private Integer sortOrder;
    private Integer enabled;

    public static HotSearchResponse from(HotSearch h) {
        HotSearchResponse r = new HotSearchResponse();
        r.setId(h.getId());
        r.setKeyword(h.getKeyword());
        r.setLabel(h.getLabel() == null || h.getLabel().isBlank() ? h.getKeyword() : h.getLabel());
        r.setSortOrder(h.getSortOrder());
        r.setEnabled(Integer.valueOf(h.getEnabled()));
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
