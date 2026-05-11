package com.insurance.aml.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页查询参数
 * 用于接收前端分页请求参数，转换为 MyBatis-Plus 分页对象
 */
@Data
@Schema(description = "分页查询参数")
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码（从1开始）
     */
    @Schema(description = "当前页码", example = "1", minimum = "1")
    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    /**
     * 每页条数（最大500）
     */
    @Schema(description = "每页条数", example = "20", minimum = "1", maximum = "500")
    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 500, message = "每页条数最大为500")
    private int size = 20;

    /**
     * 排序字段名
     */
    @Schema(description = "排序字段名", example = "createdTime")
    private String sort;

    /**
     * 排序方向：ASC 或 DESC
     */
    @Schema(description = "排序方向（ASC/DESC）", example = "DESC", allowableValues = {"ASC", "DESC"})
    private String order;

    /**
     * 转换为 MyBatis-Plus 分页对象
     * @param <T> 实体类型
     * @return IPage 分页对象
     */
    public <T> IPage<T> toPage() {
        Page<T> pageObj = new Page<>(this.page, this.size);
        // 如果指定了排序字段和方向，设置排序
        if (this.sort != null && !this.sort.isEmpty() && this.order != null) {
            boolean isAsc = "ASC".equalsIgnoreCase(this.order);
            com.baomidou.mybatisplus.core.metadata.OrderItem orderItem =
                new com.baomidou.mybatisplus.core.metadata.OrderItem();
            orderItem.setColumn(this.sort);
            orderItem.setAsc(isAsc);
            pageObj.addOrder(orderItem);
        }
        return pageObj;
    }
}
