package com.campus.market.common;

import lombok.Getter;

import java.util.List;

/**
 * 分页响应（契约：02§2.5.1 PageResult{list,total,page,size}）。
 */
@Getter
public class PageResult<T> {

    private final List<T> list;
    private final long total;
    private final long page;
    private final long size;

    private PageResult(List<T> list, long total, long page, long size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }
}