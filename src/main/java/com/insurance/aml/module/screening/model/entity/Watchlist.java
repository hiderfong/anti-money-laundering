package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDate;

/**
 * 制裁名单实体
 * 记录制裁名单中的个人或组织信息
 */
@Data
@TableName("t_watchlist")
public class Watchlist extends BaseEntity {

    /**
     * 来源ID（关联t_watchlist_source）
     */
    private Long sourceId;

    /**
     * 外部系统编号
     */
    private String externalId;

    /**
     * 实体类型：INDIVIDUAL-个人、ORGANIZATION-组织
     */
    private String entityType;

    /**
     * 名称（中文）
     */
    private String name;

    /**
     * 英文名称
     */
    private String nameEn;

    /**
     * 性别
     */
    private String gender;

    /**
     * 国籍
     */
    private String nationality;

    /**
     * 出生日期（字符串格式，原始数据可能存在不完整日期）
     */
    private String dateOfBirth;

    /**
     * 出生地
     */
    private String placeOfBirth;

    /**
     * 备注
     */
    private String remarks;

    /**
     * 列入日期
     */
    private LocalDate listDate;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    private LocalDate expiryDate;

    /**
     * 状态
     */
    private String status;
}
