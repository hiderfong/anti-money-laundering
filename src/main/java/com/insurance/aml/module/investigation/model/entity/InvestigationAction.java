package com.insurance.aml.module.investigation.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.insurance.aml.common.entity.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调查协查动作记录。
 */
@Data
@TableName("t_investigation_action")
public class InvestigationAction extends BaseEntity {

    private Long requestId;
    private String actionNo;
    private String actionType;
    private String actionContent;
    private String actionResult;
    private String operator;
    private LocalDateTime actionTime;
    private String attachmentRef;
}
