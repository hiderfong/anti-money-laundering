package com.insurance.aml.module.screening.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 制裁名单来源实体
 * 记录各类制裁名单的数据来源信息（如联合国OFAC、欧盟、人行等）
 */
@Data
@TableName("t_watchlist_source")
public class WatchlistSource extends BaseEntity {

    /**
     * 来源编码（唯一标识）
     */
    private String sourceCode;

    /**
     * 来源名称
     */
    private String sourceName;

    /**
     * 来源类型：UN-联合国、OFAC-美国财政部、EU-欧盟、MPS-公安部、PBOC-人民银行、FATF-金融行动特别工作组、OTHER-其他
     */
    private String sourceType;

    /**
     * 更新频率：REALTIME-实时、DAILY-每日、WEEKLY-每周、MONTHLY-每月
     */
    private String updateFrequency;

    /**
     * 文件格式：XML、CSV、JSON
     */
    private String fileFormat;

    /**
     * 文件下载地址
     */
    private String fileUrl;

    /**
     * 最近更新时间
     */
    private LocalDateTime lastUpdateTime;

    /**
     * 下次更新时间
     */
    private LocalDateTime nextUpdateTime;

    /**
     * 总记录数
     */
    private Integer totalEntries;

    /**
     * 状态
     */
    private String status;
}
