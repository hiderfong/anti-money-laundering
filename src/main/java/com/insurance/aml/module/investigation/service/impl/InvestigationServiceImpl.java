package com.insurance.aml.module.investigation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.case_.mapper.CaseMapper;
import com.insurance.aml.module.case_.model.entity.Case;
import com.insurance.aml.module.investigation.mapper.InvestigationActionMapper;
import com.insurance.aml.module.investigation.mapper.InvestigationRequestMapper;
import com.insurance.aml.module.investigation.model.dto.InvestigationActionRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationOverviewVO;
import com.insurance.aml.module.investigation.model.dto.InvestigationRequestCreateRequest;
import com.insurance.aml.module.investigation.model.dto.InvestigationStatusUpdateRequest;
import com.insurance.aml.module.investigation.model.entity.InvestigationAction;
import com.insurance.aml.module.investigation.model.entity.InvestigationRequest;
import com.insurance.aml.module.investigation.service.InvestigationService;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调查协查中心服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InvestigationServiceImpl implements InvestigationService {

    private static final List<String> ACTIVE_STATUSES = List.of("RECEIVED", "PROCESSING", "WAITING_APPROVAL", "RETURNED");
    private static final List<String> LEGACY_CUSTOMER_NAMES = List.of(
            "张晨曦",
            "李若宁",
            "周建国",
            "王嘉宁",
            "上海华颐供应链管理有限公司",
            "深圳远航进出口有限公司"
    );

    private final InvestigationRequestMapper requestMapper;
    private final InvestigationActionMapper actionMapper;
    private final CustomerMapper customerMapper;
    private final CaseMapper caseMapper;
    private final IdGenerator idGenerator;

    @Override
    public InvestigationOverviewVO overview() {
        LocalDate today = LocalDate.now();
        LocalDate dueSoonDate = today.plusDays(3);

        long pendingRequests = requestMapper.selectCount(
                new LambdaQueryWrapper<InvestigationRequest>().eq(InvestigationRequest::getStatus, "RECEIVED"));
        long processingRequests = requestMapper.selectCount(
                new LambdaQueryWrapper<InvestigationRequest>().in(InvestigationRequest::getStatus, List.of("PROCESSING", "WAITING_APPROVAL", "RETURNED")));
        long dueSoonRequests = requestMapper.selectCount(new LambdaQueryWrapper<InvestigationRequest>()
                .in(InvestigationRequest::getStatus, ACTIVE_STATUSES)
                .ge(InvestigationRequest::getDueDate, today)
                .le(InvestigationRequest::getDueDate, dueSoonDate));
        long overdueRequests = requestMapper.selectCount(new LambdaQueryWrapper<InvestigationRequest>()
                .in(InvestigationRequest::getStatus, ACTIVE_STATUSES)
                .lt(InvestigationRequest::getDueDate, today));
        long closedRequests = requestMapper.selectCount(
                new LambdaQueryWrapper<InvestigationRequest>().in(InvestigationRequest::getStatus, List.of("RESPONDED", "CLOSED")));

        return InvestigationOverviewVO.builder()
                .pendingRequests(pendingRequests)
                .processingRequests(processingRequests)
                .dueSoonRequests(dueSoonRequests)
                .overdueRequests(overdueRequests)
                .closedRequests(closedRequests)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvestigationRequest createRequest(InvestigationRequestCreateRequest request) {
        Customer customer = null;
        if (request.getCustomerId() != null) {
            customer = customerMapper.selectById(request.getCustomerId());
            if (customer == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "客户不存在，id=" + request.getCustomerId());
            }
        }
        if (request.getRelatedCaseId() != null) {
            Case relatedCase = caseMapper.selectById(request.getRelatedCaseId());
            if (relatedCase == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "案件不存在，id=" + request.getRelatedCaseId());
            }
        }

        InvestigationRequest entity = new InvestigationRequest();
        entity.setRequestNo(idGenerator.generate("IRQ"));
        entity.setAuthorityName(request.getAuthorityName());
        entity.setRequestType(request.getRequestType());
        entity.setDocumentNo(request.getDocumentNo());
        entity.setCustomerId(request.getCustomerId());
        entity.setCustomerName(customer == null ? null : normalizeLegacyCustomerName(customer.getName(), customer.getId()));
        entity.setRelatedCaseId(request.getRelatedCaseId());
        entity.setPriority(StringUtils.hasText(request.getPriority()) ? request.getPriority() : "MEDIUM");
        entity.setReceivedDate(request.getReceivedDate());
        entity.setDueDate(request.getDueDate());
        entity.setStatus("RECEIVED");
        entity.setHandler(request.getHandler());
        entity.setSummary(request.getSummary());
        requestMapper.insert(entity);

        addSystemAction(entity.getId(), "OTHER", "登记有权机关调查协查请求", "已登记并进入待处理队列", request.getHandler());
        return entity;
    }

    @Override
    public PageResult<InvestigationRequest> pageRequests(PageQuery pageQuery, String status, String requestType, String authorityName) {
        LambdaQueryWrapper<InvestigationRequest> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(InvestigationRequest::getStatus, status);
        }
        if (StringUtils.hasText(requestType)) {
            wrapper.eq(InvestigationRequest::getRequestType, requestType);
        }
        if (StringUtils.hasText(authorityName)) {
            wrapper.like(InvestigationRequest::getAuthorityName, authorityName);
        }
        wrapper.orderByDesc(InvestigationRequest::getCreatedTime);
        IPage<InvestigationRequest> page = requestMapper.selectPage(pageQuery.toPage(), wrapper);
        page.getRecords().forEach(item ->
                item.setCustomerName(normalizeLegacyCustomerName(item.getCustomerName(), item.getCustomerId() == null ? item.getId() : item.getCustomerId())));
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, InvestigationStatusUpdateRequest request) {
        InvestigationRequest entity = loadRequest(id);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(request.getStatus());
        if (StringUtils.hasText(request.getResponseSummary())) {
            entity.setResponseSummary(request.getResponseSummary());
        }
        if ("RESPONDED".equals(request.getStatus())) {
            entity.setCompletedTime(now);
        }
        if ("CLOSED".equals(request.getStatus())) {
            entity.setClosedTime(now);
            if (entity.getCompletedTime() == null) {
                entity.setCompletedTime(now);
            }
        }
        requestMapper.updateById(entity);
        addSystemAction(id, "OTHER", "更新调查协查状态为 " + request.getStatus(), request.getResponseSummary(), entity.getHandler());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InvestigationAction addAction(Long requestId, InvestigationActionRequest request) {
        InvestigationRequest entity = loadRequest(requestId);
        InvestigationAction action = new InvestigationAction();
        action.setRequestId(entity.getId());
        action.setActionNo(idGenerator.generate("IAC"));
        action.setActionType(request.getActionType());
        action.setActionContent(request.getActionContent());
        action.setActionResult(request.getActionResult());
        action.setOperator(StringUtils.hasText(request.getOperator()) ? request.getOperator() : SecurityUtils.getCurrentUsername());
        action.setActionTime(request.getActionTime() == null ? LocalDateTime.now() : request.getActionTime());
        action.setAttachmentRef(request.getAttachmentRef());
        actionMapper.insert(action);

        if ("RECEIVED".equals(entity.getStatus())) {
            entity.setStatus("PROCESSING");
            requestMapper.updateById(entity);
        }
        return action;
    }

    @Override
    public PageResult<InvestigationAction> pageActions(Long requestId, PageQuery pageQuery) {
        loadRequest(requestId);
        LambdaQueryWrapper<InvestigationAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InvestigationAction::getRequestId, requestId)
                .orderByDesc(InvestigationAction::getActionTime)
                .orderByDesc(InvestigationAction::getCreatedTime);
        IPage<InvestigationAction> page = actionMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.from(page);
    }

    private InvestigationRequest loadRequest(Long id) {
        InvestigationRequest entity = requestMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "调查协查请求不存在，id=" + id);
        }
        return entity;
    }

    private void addSystemAction(Long requestId, String actionType, String content, String result, String operator) {
        InvestigationAction action = new InvestigationAction();
        action.setRequestId(requestId);
        action.setActionNo(idGenerator.generate("IAC"));
        action.setActionType(actionType);
        action.setActionContent(content);
        action.setActionResult(result);
        action.setOperator(StringUtils.hasText(operator) ? operator : SecurityUtils.getCurrentUsername());
        action.setActionTime(LocalDateTime.now());
        actionMapper.insert(action);
    }

    private String normalizeLegacyCustomerName(String customerName, Long seed) {
        if (!StringUtils.hasText(customerName) || !customerName.startsWith("E2E客户")) {
            return customerName;
        }
        int index = seed == null ? 0 : (int) Math.floorMod(seed, (long) LEGACY_CUSTOMER_NAMES.size());
        return LEGACY_CUSTOMER_NAMES.get(index);
    }
}
