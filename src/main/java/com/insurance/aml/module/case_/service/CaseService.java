package com.insurance.aml.module.case_.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.case_.model.dto.CaseCreateRequest;
import com.insurance.aml.module.case_.model.dto.CaseDetailVO;
import com.insurance.aml.module.case_.model.dto.CaseQueryRequest;
import com.insurance.aml.module.case_.model.dto.CaseVO;
import com.insurance.aml.module.case_.model.entity.Case;

/**
 * 案件管理服务接口
 * 提供案件的创建、状态流转、查询、调查记录等核心功能
 */
public interface CaseService {

    /**
     * 创建案件
     * 从已确认的告警创建调查案件，自动生成案件编号
     *
     * @param req 创建请求
     * @return 创建的案件实体
     */
    Case createCase(CaseCreateRequest req);

    /**
     * 变更案件状态
     * 支持的状态流转：DRAFT→INVESTIGATING→PENDING_APPROVAL→SUBMITTED→CLOSED
     * 允许驳回：PENDING_APPROVAL→INVESTIGATING
     *
     * @param caseId   案件ID
     * @param toStatus 目标状态
     * @param remark   变更备注
     */
    void changeCaseStatus(Long caseId, String toStatus, String remark);

    /**
     * 获取案件详情
     * 包含案件基本信息、调查记录、附件、可疑交易报告、状态变更日志
     *
     * @param caseId 案件ID
     * @return 案件详情
     */
    CaseDetailVO getCaseDetail(Long caseId);

    /**
     * 分页查询案件列表
     * 支持按状态、类型、优先级、客户、调查员、时间范围过滤
     *
     * @param req 查询请求（含分页和过滤条件）
     * @return 分页结果
     */
    PageResult<CaseVO> pageQueryCases(CaseQueryRequest req);

    /**
     * 添加调查记录
     *
     * @param caseId      案件ID
     * @param content     调查内容
     * @param conclusion  调查结论
     */
    void addInvestigation(Long caseId, String content, String conclusion);

    /**
     * 关闭案件
     * 设置案件状态为CLOSED，记录关闭时间和原因
     *
     * @param caseId 案件ID
     * @param reason 关闭原因
     */
    void closeCase(Long caseId, String reason);
}
