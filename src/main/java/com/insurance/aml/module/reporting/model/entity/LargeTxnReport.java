package com.insurance.aml.module.reporting.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 大额交易报告实体
 * 记录需要向人民银行报送的大额交易信息
 */
@Data
@TableName("t_large_txn_report")
public class LargeTxnReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（雪花算法生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 报告编号
     */
    private String reportNo;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 交易ID
     */
    private Long transactionId;

    /**
     * 报告日期
     */
    private LocalDate reportDate;

    /**
     * 交易时间
     */
    private LocalDateTime transactionTime;

    /**
     * 交易类型
     */
    private String transactionType;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 币种
     */
    private String currency;

    /**
     * 支付方式
     */
    private String paymentMethod;

    /**
     * 交易对手信息（JSON格式）
     */
    private String counterpartyInfo;

    /**
     * 报告状态
     * DRAFT-草稿, REVIEWED-已审核, SUBMITTED-已提交,
     * FAILED-提交失败, RESUBMITTED-重新提交
     */
    private String reportStatus;

    /**
     * 审核人
     */
    private String reviewedBy;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedTime;

    /**
     * 提交人
     */
    private String submittedBy;

    /**
     * 提交时间
     */
    private LocalDateTime submittedTime;

    /**
     * XML报文内容
     */
    private String xmlContent;

    /**
     * 提交响应结果
     */
    private String submitResponse;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;
}
