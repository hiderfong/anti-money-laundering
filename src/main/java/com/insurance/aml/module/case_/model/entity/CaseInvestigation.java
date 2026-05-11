package com.insurance.aml.module.case_.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案件调查记录实体
 * 记录案件调查过程中的调查内容和结论
 */
@Data
@TableName("t_case_investigation")
public class CaseInvestigation {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联案件ID
     */
    private Long caseId;

    /**
     * 调查内容
     */
    private String content;

    /**
     * 调查结论
     */
    private String conclusion;

    /**
     * 调查员ID
     */
    private Long investigatorId;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
