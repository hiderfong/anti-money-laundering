package com.insurance.aml.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应包装类
 *
 * @param <T> 列表数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /**
     * 总记录数
     */
    private long total;

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 从MyBatis-Plus的IPage转换为PageResult
     *
     * @param page MyBatis-Plus分页对象
     * @param <T>  数据类型
     * @return PageResult分页结果
     */
    public static <T> PageResult<T> from(IPage<T> page) {
        return PageResult.<T>builder()
                .total(page.getTotal())
                .list(page.getRecords())
                .page((int) page.getCurrent())
                .size((int) page.getSize())
                .totalPages((int) page.getPages())
                .build();
    }
}
