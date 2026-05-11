package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 制裁名单别名实体
 * 记录制裁名单条目的各种别名、曾用名等
 */
@Data
@TableName("t_watchlist_alias")
public class WatchlistAlias {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 关联的制裁名单条目ID（关联t_watchlist）
     */
    private Long watchlistId;

    /**
     * 别名
     */
    private String aliasName;

    /**
     * 别名类型：ORIGINAL-原始名、ALIAS-别名、AKA-又名、FKA-曾用名
     */
    private String aliasType;

    /**
     * 语言
     */
    private String language;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
