package com.insurance.aml.module.case_.service;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.case_.mapper.*;
import com.insurance.aml.module.case_.model.dto.CaseCreateRequest;
import com.insurance.aml.module.case_.model.dto.CaseDetailVO;
import com.insurance.aml.module.case_.model.entity.Case;
import com.insurance.aml.module.case_.model.entity.CaseInvestigation;
import com.insurance.aml.module.case_.model.entity.CaseStatusLog;
import com.insurance.aml.module.case_.model.entity.StrReport;
import com.insurance.aml.module.case_.service.impl.CaseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 案件管理服务单元测试
 * 覆盖 createCase / changeCaseStatus / getCaseDetail / closeCase 核心方法
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("案件管理服务测试")
class CaseServiceImplTest {

    @Mock
    private CaseMapper caseMapper;

    @Mock
    private CaseStatusLogMapper caseStatusLogMapper;

    @Mock
    private CaseInvestigationMapper caseInvestigationMapper;

    @Mock
    private CaseAttachmentMapper caseAttachmentMapper;

    @Mock
    private StrReportMapper strReportMapper;

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private CaseServiceImpl caseService;

    private Alert confirmedAlert;
    private CaseCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        // 准备已确认的告警
        confirmedAlert = new Alert();
        confirmedAlert.setId(1L);
        confirmedAlert.setCustomerId(100L);
        confirmedAlert.setCustomerName("张三");
        confirmedAlert.setStatus("CONFIRMED");

        // 准备创建案件请求
        createRequest = new CaseCreateRequest();
        createRequest.setAlertId(1L);
        createRequest.setCaseType("SUSPICIOUS_TXN");
        createRequest.setPriority(3);
        createRequest.setSummary("可疑交易调查");
    }

    /**
     * 辅助方法：创建案件实体
     */
    private Case createCaseEntity(Long id, String status) {
        Case caseEntity = new Case();
        caseEntity.setId(id);
        caseEntity.setCaseNo("CAS20260101001");
        caseEntity.setAlertId(1L);
        caseEntity.setCustomerId(100L);
        caseEntity.setCustomerName("张三");
        caseEntity.setCaseStatus(status);
        caseEntity.setCaseType("SUSPICIOUS_TXN");
        caseEntity.setPriority(3);
        caseEntity.setSummary("测试案件");
        caseEntity.setCreatedBy("admin");
        caseEntity.setCreatedTime(LocalDateTime.now());
        return caseEntity;
    }

    /**
     * 测试创建案件成功
     */
    @Test
    @DisplayName("创建案件 -> 告警已确认，案件创建成功状态为DRAFT")
    void createCase_success() {
        // 准备
        when(alertMapper.selectById(1L)).thenReturn(confirmedAlert);
        when(idGenerator.generateCaseNo()).thenReturn("CAS20260101001");
        when(caseMapper.insert(any(Case.class))).thenReturn(1);
        when(caseStatusLogMapper.insert(any(CaseStatusLog.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

            // 执行
            Case result = caseService.createCase(createRequest);

            // 验证
            assertNotNull(result, "案件不应为空");
            assertEquals("DRAFT", result.getCaseStatus(), "初始状态应为DRAFT");
            assertEquals(1L, result.getAlertId(), "关联告警ID应正确");
            assertEquals(100L, result.getCustomerId(), "客户ID应正确");
            assertEquals("张三", result.getCustomerName(), "客户姓名应正确");
            assertEquals("SUSPICIOUS_TXN", result.getCaseType(), "案件类型应正确");
            assertEquals(3, result.getPriority(), "优先级应正确");
            assertEquals("admin", result.getCreatedBy(), "创建人应正确");

            // 验证状态日志已记录
            verify(caseStatusLogMapper).insert(argThat(log ->
                    "DRAFT".equals(log.getToStatus()) && log.getFromStatus() == null));
        }
    }

    /**
     * 测试关联告警不存在
     */
    @Test
    @DisplayName("创建案件-告警不存在 -> 抛出BusinessException")
    void createCase_alertNotFound_throwsException() {
        // 准备
        when(alertMapper.selectById(999L)).thenReturn(null);
        createRequest.setAlertId(999L);

        // 执行 & 验证
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.createCase(createRequest));
        assertTrue(ex.getMessage().contains("关联告警不存在"), "异常消息应提示告警不存在");
    }

    /**
     * 测试告警状态不是CONFIRMED
     */
    @Test
    @DisplayName("创建案件-告警未确认 -> 抛出BusinessException")
    void createCase_alertNotConfirmed_throwsException() {
        // 准备：告警状态为NEW
        Alert newAlert = new Alert();
        newAlert.setId(1L);
        newAlert.setStatus("NEW");
        when(alertMapper.selectById(1L)).thenReturn(newAlert);

        // 执行 & 验证
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.createCase(createRequest));
        assertTrue(ex.getMessage().contains("只有已确认的告警"), "异常消息应提示告警未确认");
    }

    /**
     * 测试状态流转 DRAFT -> INVESTIGATING
     */
    @Test
    @DisplayName("状态流转 DRAFT->INVESTIGATING -> 流转成功")
    void changeCaseStatus_draftToInvestigating_success() {
        // 准备
        Case draftCase = createCaseEntity(1L, "DRAFT");
        when(caseMapper.selectById(1L)).thenReturn(draftCase);
        when(caseMapper.updateById(any(Case.class))).thenReturn(1);
        when(caseStatusLogMapper.insert(any(CaseStatusLog.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

            // 执行
            caseService.changeCaseStatus(1L, "INVESTIGATING", "开始调查");

            // 验证
            verify(caseMapper).updateById(argThat(c ->
                    "INVESTIGATING".equals(c.getCaseStatus())));
            verify(caseStatusLogMapper).insert(argThat(log ->
                    "DRAFT".equals(log.getFromStatus())
                            && "INVESTIGATING".equals(log.getToStatus())
                            && "开始调查".equals(log.getRemark())));
        }
    }

    /**
     * 测试无效状态流转
     */
    @Test
    @DisplayName("无效状态流转 DRAFT->CLOSED -> 抛出BusinessException")
    void changeCaseStatus_invalidTransition_throwsException() {
        // 准备：DRAFT状态案件
        Case draftCase = createCaseEntity(1L, "DRAFT");
        when(caseMapper.selectById(1L)).thenReturn(draftCase);

        // 执行 & 验证：DRAFT不能直接到CLOSED
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.changeCaseStatus(1L, "CLOSED", "直接关闭"));
        assertTrue(ex.getMessage().contains("非法状态流转"), "异常消息应提示非法状态流转");
    }

    /**
     * 测试案件不存在时变更状态
     */
    @Test
    @DisplayName("变更状态-案件不存在 -> 抛出BusinessException")
    void changeCaseStatus_caseNotFound_throwsException() {
        // 准备
        when(caseMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.changeCaseStatus(999L, "INVESTIGATING", "备注"));
        assertTrue(ex.getMessage().contains("案件不存在"), "异常消息应提示案件不存在");
    }

    /**
     * 测试关闭案件成功
     */
    @Test
    @DisplayName("关闭案件-已提交状态 -> 关闭成功")
    void closeCase_submittedCase_success() {
        // 准备：已提交的案件
        Case submittedCase = createCaseEntity(1L, "SUBMITTED");
        when(caseMapper.selectById(1L)).thenReturn(submittedCase);
        when(caseMapper.updateById(any(Case.class))).thenReturn(1);
        when(caseStatusLogMapper.insert(any(CaseStatusLog.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

            // 执行
            caseService.closeCase(1L, "调查完毕，排除嫌疑");

            // 验证
            verify(caseMapper).updateById(argThat(c ->
                    "CLOSED".equals(c.getCaseStatus())
                            && c.getCloseTime() != null
                            && "调查完毕，排除嫌疑".equals(c.getCloseReason())));
            verify(caseStatusLogMapper).insert(argThat(log ->
                    "SUBMITTED".equals(log.getFromStatus())
                            && "CLOSED".equals(log.getToStatus())));
        }
    }

    /**
     * 测试关闭非SUBMITTED状态案件
     */
    @Test
    @DisplayName("关闭案件-非已提交状态 -> 抛出BusinessException")
    void closeCase_notSubmitted_throwsException() {
        // 准备：DRAFT状态案件
        Case draftCase = createCaseEntity(1L, "DRAFT");
        when(caseMapper.selectById(1L)).thenReturn(draftCase);

        // 执行 & 验证
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.closeCase(1L, "关闭原因"));
        assertTrue(ex.getMessage().contains("只有已提交的案件才能关闭"), "异常消息应提示状态不允许");
    }

    /**
     * 测试查询案件详情
     */
    @Test
    @DisplayName("查询案件详情 -> 返回完整详情包含调查记录和附件")
    void getCaseDetail_success() {
        // 准备
        Case caseEntity = createCaseEntity(1L, "INVESTIGATING");
        when(caseMapper.selectById(1L)).thenReturn(caseEntity);
        when(caseInvestigationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(caseAttachmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(strReportMapper.selectOne(any())).thenReturn(null);
        when(caseStatusLogMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 执行
        CaseDetailVO detail = caseService.getCaseDetail(1L);

        // 验证
        assertNotNull(detail, "案件详情不应为空");
        assertEquals(1L, detail.getId(), "案件ID应正确");
        assertEquals("INVESTIGATING", detail.getCaseStatus(), "案件状态应正确");
        assertFalse(detail.isHasStrReport(), "无STR报告时应为false");
    }

    /**
     * 测试添加调查记录
     */
    @Test
    @DisplayName("添加调查记录 -> 记录保存成功")
    void addInvestigation_success() {
        // 准备
        Case caseEntity = createCaseEntity(1L, "INVESTIGATING");
        when(caseMapper.selectById(1L)).thenReturn(caseEntity);
        when(caseInvestigationMapper.insert(any(CaseInvestigation.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            // 执行
            caseService.addInvestigation(1L, "调查内容", "初步结论");

            // 验证
            verify(caseInvestigationMapper).insert(argThat(inv ->
                    1L == inv.getCaseId()
                            && "调查内容".equals(inv.getContent())
                            && "初步结论".equals(inv.getConclusion())
                            && inv.getInvestigatorId() != null));
        }
    }

    /**
     * 测试完整状态流转链路 DRAFT -> INVESTIGATING -> PENDING_APPROVAL -> SUBMITTED -> CLOSED
     */
    @Test
    @DisplayName("完整状态流转 -> DRAFT->INVESTIGATING->PENDING_APPROVAL->SUBMITTED->CLOSED")
    void changeCaseStatus_fullTransitionChain() {
        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

            // DRAFT -> INVESTIGATING
            Case draftCase = createCaseEntity(1L, "DRAFT");
            when(caseMapper.selectById(1L)).thenReturn(draftCase);
            when(caseMapper.updateById(any(Case.class))).thenReturn(1);
            when(caseStatusLogMapper.insert(any(CaseStatusLog.class))).thenReturn(1);
            caseService.changeCaseStatus(1L, "INVESTIGATING", "开始调查");

            // INVESTIGATING -> PENDING_APPROVAL
            Case investigatingCase = createCaseEntity(1L, "INVESTIGATING");
            when(caseMapper.selectById(1L)).thenReturn(investigatingCase);
            caseService.changeCaseStatus(1L, "PENDING_APPROVAL", "提交审批");

            // PENDING_APPROVAL -> SUBMITTED
            Case pendingCase = createCaseEntity(1L, "PENDING_APPROVAL");
            when(caseMapper.selectById(1L)).thenReturn(pendingCase);
            caseService.changeCaseStatus(1L, "SUBMITTED", "审批通过");

            // SUBMITTED -> CLOSED
            Case submittedCase = createCaseEntity(1L, "SUBMITTED");
            when(caseMapper.selectById(1L)).thenReturn(submittedCase);
            caseService.closeCase(1L, "案件结束");

            // 验证：共3次状态变更 + 1次关闭 = 4次updateById
            verify(caseMapper, times(4)).updateById(any(Case.class));
        }
    }

    /**
     * 测试CLOSED为终态不可再流转
     */
    @Test
    @DisplayName("终态验证 -> CLOSED状态不可再流转")
    void changeCaseStatus_closedIsTerminal_throwsException() {
        // 准备：已关闭的案件
        Case closedCase = createCaseEntity(1L, "CLOSED");
        when(caseMapper.selectById(1L)).thenReturn(closedCase);

        // 执行 & 验证：CLOSED状态不能再流转到任何状态
        BusinessException ex = assertThrows(BusinessException.class,
                () -> caseService.changeCaseStatus(1L, "INVESTIGATING", "重新调查"));
        assertTrue(ex.getMessage().contains("非法状态流转"), "CLOSED为终态，不应允许流转");
    }
}
