package com.example.supermarket.common;

import java.util.List;

public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long total;
    private int pages;

    public PageResponse() {
    }

    private PageResponse(List<T> items, int page, int size, long total, int pages) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
        this.pages = pages;
    }

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(items, page, size, total, pages);
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
