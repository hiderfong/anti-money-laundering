package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 制裁名单证件信息实体
 * 记录制裁名单条目关联的身份证件信息
 */
@Data
@TableName("t_watchlist_identity")
public class WatchlistIdentity {

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
     * 证件类型
     */
    private String idType;

    /**
     * 证件号码
     */
    private String idNumber;

    /**
     * 签发国家
     */
    private String issuingCountry;

    /**
     * 证件到期日期
     */
    private String expiryDate;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
