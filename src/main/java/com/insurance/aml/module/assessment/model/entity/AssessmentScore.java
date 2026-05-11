package com.insurance.aml.module.assessment.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评估评分明细实体
 * 记录自评估中每个指标的评分详情
 */
@Data
@TableName("t_assessment_score")
public class AssessmentScore implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 评估ID
     */
    private Long assessmentId;

    /**
     * 指标ID
     */
    private Long indicatorId;

    /**
     * 原始值
     */
    private BigDecimal rawValue;

    /**
     * 评分
     */
    private Integer score;

    /**
     * 评分依据/证据
     */
    private String evidence;

    /**
     * 数据来源
     */
    private String dataSource;

    /**
     * 备注
     */
    private String remark;

    /**
     * 评分人
     */
    private String scoredBy;

    /**
     * 评分时间
     */
    private LocalDateTime scoredTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
