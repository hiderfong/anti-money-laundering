package com.insurance.aml.module.kyc.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户验证记录实体
 */
@Data
@TableName("t_verification_record")
public class VerificationRecord {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 验证类型：TWO_FACTOR-两要素，FOUR_FACTOR-四要素，ENTERPRISE-企业验证，FACE-人脸验证
     */
    private String verificationType;

    /**
     * 验证结果：SUCCESS-成功，FAILURE-失败，PENDING-待处理
     */
    private String verificationResult;

    /**
     * 请求数据（加密存储）
     */
    private String requestData;

    /**
     * 响应数据
     */
    private String responseData;

    /**
     * 第三方服务提供商
     */
    private String thirdPartyProvider;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 验证人
     */
    private Long verifiedBy;

    /**
     * 验证时间
     */
    private LocalDateTime verifiedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
