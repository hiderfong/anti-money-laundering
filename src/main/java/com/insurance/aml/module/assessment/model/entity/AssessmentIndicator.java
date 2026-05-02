package com.insurance.aml.module.assessment.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估指标实体
 * 定义风险自评估的各项评分指标
 */
@Data
@TableName("t_assessment_indicator")
public class AssessmentIndicator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 指标编码（唯一）
     */
    private String indicatorCode;

    /**
     * 指标名称
     */
    private String indicatorName;

    /**
     * 指标分类：INHERENT_RISK-固有风险、CONTROL_EFFECTIVENESS-控制有效性
     */
    private String category;

    /**
     * 评估维度
     */
    private String dimension;

    /**
     * 权重（百分比）
     */
    private BigDecimal weight;

    /**
     * 评分标准说明
     */
    private String scoringCriteria;

    /**
     * 最高分值
     */
    private Integer maxScore;

    /**
     * 状态：ENABLED-启用、DISABLED-禁用
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
