package com.insurance.aml.module.assessment.service;

import com.insurance.aml.module.assessment.model.dto.AssessmentCreateRequest;
import com.insurance.aml.module.assessment.model.dto.AssessmentScoreRequest;
import com.insurance.aml.module.assessment.model.dto.SelfAssessmentDetailVO;
import com.insurance.aml.module.assessment.model.entity.SelfAssessment;

import java.util.List;

/**
 * 风险自评估服务接口
 */
public interface SelfAssessmentService {

    /**
     * 创建自评估
     * @param req 创建请求
     * @return 评估实体
     */
    SelfAssessment createAssessment(AssessmentCreateRequest req);

    /**
     * 提交指标评分
     * @param req 评分请求
     */
    void submitScore(AssessmentScoreRequest req);

    /**
     * 完成评估（计算综合评分和风险等级）
     * @param assessmentId 评估ID
     * @return 评估实体
     */
    SelfAssessment completeAssessment(Long assessmentId);

    /**
     * 审批评估
     * @param assessmentId 评估ID
     * @param approvedBy 审批人
     */
    void approveAssessment(Long assessmentId, String approvedBy);

    /**
     * 获取评估详情（含所有指标评分和指标信息）
     * @param assessmentId 评估ID
     * @return 评估详情VO
     */
    SelfAssessmentDetailVO getAssessmentDetail(Long assessmentId);

    /**
     * 按年度查询评估列表
     * @param year 评估年度
     * @return 评估列表
     */
    List<SelfAssessment> listAssessments(Integer year);
}
