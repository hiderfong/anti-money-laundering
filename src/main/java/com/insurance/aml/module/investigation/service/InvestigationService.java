package com.insurance.aml.module.investigation.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.investigation.model.dto.InvestigationActionRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationOverviewVO;
import com.insurance.aml.module.investigation.model.dto.InvestigationRequestCreateRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationStatusUpdateRequest;
import com.insurance.aml.module.investigation.model.entity.InvestigationAction;
import com.insurance.aml.module.investigation.model.entity.InvestigationRequest;

/**
 * 调查协查服务接口
 * 管理监管/执法机关发起的调查请求及内部协查行动
 */
public interface InvestigationService {

    /**
     * 获取调查协查概览统计
     */
    InvestigationOverviewVO overview();

    /**
     * 创建调查请求
     */
    InvestigationRequest createRequest(InvestigationRequestCreateRequest request);

    /**
     * 分页查询调查请求
     */
    PageResult<InvestigationRequest> pageRequests(PageQuery pageQuery, String status, String requestType, String authorityName);

    /**
     * 更新调查请求状态
     */
    void updateStatus(Long id, InvestigationStatusUpdateRequest request);

    /**
     * 添加协查行动记录
     */
    InvestigationAction addAction(Long requestId, InvestigationActionRequest request);

    /**
     * 分页查询协查行动记录
     */
    PageResult<InvestigationAction> pageActions(Long requestId, PageQuery pageQuery);
}
