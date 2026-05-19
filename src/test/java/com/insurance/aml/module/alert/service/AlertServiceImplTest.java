package com.insurance.aml.module.alert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.alert.controller.AlertController;
import com.insurance.aml.module.alert.mapper.AlertAssignmentLogMapper;
import com.insurance.aml.module.alert.mapper.AlertMapper;
import com.insurance.aml.module.alert.mapper.AlertRuleDetailMapper;
import com.insurance.aml.module.alert.model.dto.AlertAssignRequest;
import com.insurance.aml.module.alert.model.dto.AlertProcessRequest;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;
import com.insurance.aml.module.alert.service.impl.AlertServiceImpl;
import com.insurance.aml.module.casemgmt.model.dto.CaseCreateRequest;
import com.insurance.aml.module.casemgmt.service.CaseService;
import com.insurance.aml.module.system.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlertServiceImpl 单元测试
 * 使用 JUnit 5 + Mockito，中文注释
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("预警服务实现类测试")
class AlertServiceImplTest {

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private AlertRuleDetailMapper alertRuleDetailMapper;

    @Mock
    private AlertAssignmentLogMapper assignmentLogMapper;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private CaseService caseService;

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private AlertServiceImpl alertService;

    // ==================== createAlert 测试 ====================

    @Test
    @DisplayName("创建预警 - 成功场景")
    void createAlert_success() {
        // 准备预警数据
        Alert alert = buildTestAlert();
        alert.setId(null); // 新建时无ID
        alert.setStatus(null);

        // 准备规则明细
        List<AlertRuleDetail> ruleDetails = new ArrayList<>();
        AlertRuleDetail detail = new AlertRuleDetail();
        detail.setRuleCode("RULE_001");
        ruleDetails.add(detail);

        // mock：生成预警编号
        when(idGenerator.generateAlertNo()).thenReturn("ALT20260101120000123456");
        // mock：插入预警成功
        when(alertMapper.insert(any(Alert.class))).thenAnswer(invocation -> {
            Alert a = invocation.getArgument(0);
            a.setId(1L); // 模拟 MyBatis-Plus 回填主键
            return 1;
        });
        // mock：插入规则明细成功
        when(alertRuleDetailMapper.insert(any(AlertRuleDetail.class))).thenReturn(1);

        // 执行创建预警
        Alert result = alertService.createAlert(alert, ruleDetails);

        // 验证结果
        assertNotNull(result);
        assertEquals("ALT20260101120000123456", result.getAlertNo());
        assertEquals("NEW", result.getStatus());

        // 验证 alertMapper.insert 被调用一次
        verify(alertMapper, times(1)).insert(any(Alert.class));
        // 验证规则明细插入被调用一次
        verify(alertRuleDetailMapper, times(1)).insert(any(AlertRuleDetail.class));
    }

    // ==================== processAlert 测试 ====================

    @Test
    @DisplayName("处理预警 - 确认可疑，状态变更为CONFIRMED并创建案件")
    void processAlert_confirmSuspicous() {
        // 准备预警数据（状态为ASSIGNED）
        Alert alert = buildTestAlert();
        alert.setId(1L);
        alert.setStatus("ASSIGNED");

        // 准备处理请求
        AlertProcessRequest req = new AlertProcessRequest();
        req.setAlertId(1L);
        req.setProcessResult("CONFIRMED_SUSPICIOUS");
        req.setProcessRemark("确认存在可疑交易");

        // mock：查询预警
        when(alertMapper.selectById(1L)).thenReturn(alert);
        // mock：更新成功
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);

        // 执行处理
        alertService.processAlert(req);

        // 验证：状态变更为 CONFIRMED
        verify(alertMapper, times(1)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "CONFIRMED".equals(updated.getStatus())
                    && "CONFIRMED_SUSPICIOUS".equals(updated.getProcessResult());
        }));
        // 验证：确认可疑后创建案件
        verify(caseService, times(1)).createCase(argThat((CaseCreateRequest caseReq) ->
                Long.valueOf(1L).equals(caseReq.getAlertId())
                        && "SUSPICIOUS".equals(caseReq.getCaseType())
                        && Integer.valueOf(4).equals(caseReq.getPriority())
                        && "可疑交易预警".equals(caseReq.getSummary())
        ));
    }

    @Test
    @DisplayName("处理预警 - 排除，状态变更为EXCLUDED")
    void processAlert_exclude() {
        // 准备预警数据（状态为ASSIGNED）
        Alert alert = buildTestAlert();
        alert.setId(2L);
        alert.setStatus("ASSIGNED");

        // 准备处理请求
        AlertProcessRequest req = new AlertProcessRequest();
        req.setAlertId(2L);
        req.setProcessResult("EXCLUDED");
        req.setProcessRemark("误报排除");

        // mock：查询预警
        when(alertMapper.selectById(2L)).thenReturn(alert);
        // mock：更新成功
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);

        // 执行处理
        alertService.processAlert(req);

        // 验证：状态变更为 EXCLUDED
        verify(alertMapper, times(1)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "EXCLUDED".equals(updated.getStatus())
                    && "EXCLUDED".equals(updated.getProcessResult());
        }));
        verify(caseService, never()).createCase(any(CaseCreateRequest.class));
    }

    @Test
    @DisplayName("处理预警 - 状态为NEW不允许处理，抛出业务异常")
    void processAlert_invalidStatus() {
        // 准备预警数据（状态为NEW，不允许处理）
        Alert alert = buildTestAlert();
        alert.setId(3L);
        alert.setStatus("NEW");

        // 准备处理请求
        AlertProcessRequest req = new AlertProcessRequest();
        req.setAlertId(3L);
        req.setProcessResult("CONFIRMED_SUSPICIOUS");

        // mock：查询预警
        when(alertMapper.selectById(3L)).thenReturn(alert);

        // 验证抛出 BusinessException 且错误码为 ALERT_STATUS_ERROR
        BusinessException exception = assertThrows(BusinessException.class,
                () -> alertService.processAlert(req));
        assertEquals(ResultCode.ALERT_STATUS_ERROR.getCode(), exception.getCode());

        // 验证 updateById 未被调用
        verify(alertMapper, never()).updateById(any(Alert.class));
        verify(caseService, never()).createCase(any(CaseCreateRequest.class));
    }

    // ==================== assignAlert 测试 ====================

    @Test
    @DisplayName("分配预警 - 成功分配，状态变更为ASSIGNED")
    void assignAlert_success() {
        // 准备预警数据（状态为NEW）
        Alert alert = buildTestAlert();
        alert.setId(1L);
        alert.setStatus("NEW");

        // 准备分配请求
        AlertAssignRequest req = new AlertAssignRequest();
        req.setAlertId(1L);
        req.setAssignTo(100L);
        req.setAssignReason("手动分配");

        // mock：查询预警
        when(alertMapper.selectById(1L)).thenReturn(alert);
        // mock：更新成功
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);
        // mock：插入分配日志
        when(assignmentLogMapper.insert(any())).thenReturn(1);

        // 执行分配
        alertService.assignAlert(req);

        // 验证：状态变更为 ASSIGNED，分配人更新
        verify(alertMapper, times(1)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "ASSIGNED".equals(updated.getStatus())
                    && Long.valueOf(100L).equals(updated.getAssignedTo());
        }));
    }

    @Test
    @DisplayName("分配预警 - 预警已确认（CONFIRMED状态），不允许分配")
    void assignAlert_alreadyAssigned() {
        // 准备预警数据（状态为CONFIRMED，不允许再分配）
        Alert alert = buildTestAlert();
        alert.setId(4L);
        alert.setStatus("CONFIRMED");

        // 准备分配请求
        AlertAssignRequest req = new AlertAssignRequest();
        req.setAlertId(4L);
        req.setAssignTo(200L);

        // mock：查询预警
        when(alertMapper.selectById(4L)).thenReturn(alert);

        // 验证抛出 BusinessException 且错误码为 ALERT_STATUS_ERROR
        BusinessException exception = assertThrows(BusinessException.class,
                () -> alertService.assignAlert(req));
        assertEquals(ResultCode.ALERT_STATUS_ERROR.getCode(), exception.getCode());

        // 验证 updateById 未被调用
        verify(alertMapper, never()).updateById(any(Alert.class));
    }

    // ==================== escalateOverdueAlerts 测试 ====================

    @Test
    @DisplayName("升级超期预警 - 存在超期预警，状态变更为ESCALATED")
    void escalateOverdueAlerts() {
        // 准备超期预警列表
        Alert overdueAlert1 = buildTestAlert();
        overdueAlert1.setId(1L);
        overdueAlert1.setStatus("ASSIGNED");
        overdueAlert1.setAssignedTime(LocalDateTime.now().minusHours(72));

        Alert overdueAlert2 = buildTestAlert();
        overdueAlert2.setId(2L);
        overdueAlert2.setStatus("ASSIGNED");
        overdueAlert2.setAssignedTime(LocalDateTime.now().minusHours(50));

        List<Alert> overdueAlerts = Arrays.asList(overdueAlert1, overdueAlert2);

        // mock：查询超期预警
        when(alertMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(overdueAlerts);
        // mock：更新成功
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);
        // mock：插入分配日志
        when(assignmentLogMapper.insert(any())).thenReturn(1);
        // mock：查询升级管理员
        when(sysUserMapper.findEnabledUserIdsByRoleCodes(anyList())).thenReturn(List.of(9001L));

        // 执行超期预警升级
        alertService.escalateOverdueAlerts();

        // 验证：每条预警状态变更为 ESCALATED
        verify(alertMapper, times(2)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "ESCALATED".equals(updated.getStatus());
        }));
        // 验证：分配日志插入两次
        verify(assignmentLogMapper, times(2)).insert(any());
    }

    // ==================== getAlertStatistics 测试 ====================

    @Test
    @DisplayName("获取预警统计信息 - 返回正确的统计数据")
    void getAlertStatistics() {
        // mock：各状态统计查询
        when(alertMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L)   // NEW
                .thenReturn(3L)   // ASSIGNED
                .thenReturn(2L)   // PROCESSING
                .thenReturn(10L)  // CONFIRMED
                .thenReturn(8L)   // EXCLUDED
                .thenReturn(1L)   // ESCALATED
                .thenReturn(0L)   // LOW
                .thenReturn(3L)   // MEDIUM
                .thenReturn(5L)   // HIGH
                .thenReturn(2L);  // CRITICAL
        // mock：总数查询（selectCount(null)）
        when(alertMapper.selectCount(isNull())).thenReturn(29L);

        // 执行统计查询
        AlertController.AlertStatisticsVO statistics = alertService.getAlertStatistics();

        // 验证结果
        assertNotNull(statistics);
        // 验证按状态统计
        Map<String, Long> statusCount = statistics.getCountByStatus();
        assertNotNull(statusCount);
        assertEquals(5L, statusCount.get("NEW"));
        assertEquals(3L, statusCount.get("ASSIGNED"));
        assertEquals(2L, statusCount.get("PROCESSING"));
        assertEquals(10L, statusCount.get("CONFIRMED"));
        assertEquals(8L, statusCount.get("EXCLUDED"));
        assertEquals(1L, statusCount.get("ESCALATED"));
        // 验证按风险等级统计
        Map<String, Long> riskLevelCount = statistics.getCountByRiskLevel();
        assertNotNull(riskLevelCount);
        assertEquals(0L, riskLevelCount.get("LOW"));
        assertEquals(3L, riskLevelCount.get("MEDIUM"));
        assertEquals(5L, riskLevelCount.get("HIGH"));
        assertEquals(2L, riskLevelCount.get("CRITICAL"));
        // 验证总数
        assertEquals(29L, statistics.getTotalCount());
    }

    // ==================== 自动分配测试 ====================

    @Test
    @DisplayName("自动分配预警 - 无待分配预警时正常返回不更新")
    void testAutoAssignAlert() {
        // mock：查询NEW状态预警返回空列表
        when(alertMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // 执行自动分配
        alertService.autoAssignAlerts();

        // 验证：不应调用updateById（没有预警需要分配）
        verify(alertMapper, never()).updateById(any(Alert.class));
        // 验证：不应插入分配日志
        verify(assignmentLogMapper, never()).insert(any());
    }

    // ==================== 升级预警测试 ====================

    @Test
    @DisplayName("升级预警 - 处理结果为ESCALATED时状态变更为ESCALATED且不创建案件")
    void testEscalateAlert() {
        // 准备预警数据（状态为ASSIGNED）
        Alert alert = buildTestAlert();
        alert.setId(5L);
        alert.setStatus("ASSIGNED");

        // 准备处理请求 - ESCALATED
        AlertProcessRequest req = new AlertProcessRequest();
        req.setAlertId(5L);
        req.setProcessResult("ESCALATED");
        req.setProcessRemark("升级至高级审核");

        // mock：查询预警
        when(alertMapper.selectById(5L)).thenReturn(alert);
        // mock：更新成功
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);

        // 执行处理
        alertService.processAlert(req);

        // 验证：状态变更为 ESCALATED
        verify(alertMapper, times(1)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "ESCALATED".equals(updated.getStatus())
                    && "ESCALATED".equals(updated.getProcessResult());
        }));
        // 验证：ESCALATED不应创建案件
        verify(caseService, never()).createCase(any(CaseCreateRequest.class));
    }

    // ==================== 忽略预警测试 ====================

    @Test
    @DisplayName("忽略预警 - 批量忽略多条预警，状态变更为EXCLUDED")
    void testDismissAlert() {
        // 准备两条ASSIGNED预警
        Alert alert1 = buildTestAlert();
        alert1.setId(10L);
        alert1.setStatus("ASSIGNED");

        Alert alert2 = buildTestAlert();
        alert2.setId(11L);
        alert2.setStatus("ASSIGNED");

        // mock：依次返回两条预警
        when(alertMapper.selectById(10L)).thenReturn(alert1);
        when(alertMapper.selectById(11L)).thenReturn(alert2);
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);

        // 执行批量忽略
        alertService.batchProcess(Arrays.asList(10L, 11L), "EXCLUDED");

        // 验证：两条预警都被更新为EXCLUDED
        verify(alertMapper, times(2)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "EXCLUDED".equals(updated.getStatus())
                    && "EXCLUDED".equals(updated.getProcessResult());
        }));
        // 验证：排除不应创建案件
        verify(caseService, never()).createCase(any(CaseCreateRequest.class));
    }

    // ==================== 预警统计测试 ====================

    @Test
    @DisplayName("预警统计 - 各状态均为零时返回空统计")
    void testGetAlertStatistics_emptyStats() {
        // mock：所有统计查询返回0
        when(alertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(alertMapper.selectCount(isNull())).thenReturn(0L);

        // 执行统计查询
        AlertController.AlertStatisticsVO statistics = alertService.getAlertStatistics();

        // 验证结果
        assertNotNull(statistics);
        assertEquals(0L, statistics.getTotalCount());
        // 验证各状态计数为0
        Map<String, Long> statusCount = statistics.getCountByStatus();
        assertNotNull(statusCount);
        assertEquals(0L, statusCount.get("NEW"));
        assertEquals(0L, statusCount.get("ASSIGNED"));
        assertEquals(0L, statusCount.get("CONFIRMED"));
        // 验证各风险等级计数为0
        Map<String, Long> riskLevelCount = statistics.getCountByRiskLevel();
        assertNotNull(riskLevelCount);
        assertEquals(0L, riskLevelCount.get("HIGH"));
        assertEquals(0L, riskLevelCount.get("CRITICAL"));
    }

    // ==================== 批量处理测试 ====================

    @Test
    @DisplayName("批量处理预警 - 确认可疑多条预警并为每条创建案件")
    void testBatchProcessAlerts() {
        // 准备两条ASSIGNED预警
        Alert alert1 = buildTestAlert();
        alert1.setId(20L);
        alert1.setStatus("ASSIGNED");
        alert1.setRiskLevel("HIGH");

        Alert alert2 = buildTestAlert();
        alert2.setId(21L);
        alert2.setStatus("ASSIGNED");
        alert2.setRiskLevel("CRITICAL");

        // mock：查询预警
        when(alertMapper.selectById(20L)).thenReturn(alert1);
        when(alertMapper.selectById(21L)).thenReturn(alert2);
        when(alertMapper.updateById(any(Alert.class))).thenReturn(1);

        // 执行批量确认可疑
        alertService.batchProcess(Arrays.asList(20L, 21L), "CONFIRMED_SUSPICIOUS");

        // 验证：两条预警都被更新为CONFIRMED
        verify(alertMapper, times(2)).updateById(argThat(a -> {
            Alert updated = (Alert) a;
            return "CONFIRMED".equals(updated.getStatus())
                    && "CONFIRMED_SUSPICIOUS".equals(updated.getProcessResult());
        }));
        // 验证：为每条预警创建了案件
        verify(caseService, times(2)).createCase(any(CaseCreateRequest.class));
    }

    // ==================== 超期检查测试 ====================

    @Test
    @DisplayName("超期检查 - 无超期预警时不更新也不记录日志")
    void testCheckOverdueAlerts() {
        // mock：查询超期预警返回空列表
        when(alertMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // 执行超期检查
        alertService.escalateOverdueAlerts();

        // 验证：不应更新任何预警
        verify(alertMapper, never()).updateById(any(Alert.class));
        // 验证：不应插入分配日志
        verify(assignmentLogMapper, never()).insert(any());
    }

    // ==================== 异常场景测试 ====================

    @Test
    @DisplayName("分配预警 - 预警不存在时抛出ALERT_NOT_FOUND异常")
    void testAssignAlertToInvalidUser() {
        // mock：查询预警返回null（预警不存在）
        when(alertMapper.selectById(999L)).thenReturn(null);

        // 准备分配请求
        AlertAssignRequest req = new AlertAssignRequest();
        req.setAlertId(999L);
        req.setAssignTo(100L);
        req.setAssignReason("手动分配");

        // 验证抛出 BusinessException 且错误码为 ALERT_NOT_FOUND
        BusinessException exception = assertThrows(BusinessException.class,
                () -> alertService.assignAlert(req));
        assertEquals(ResultCode.ALERT_NOT_FOUND.getCode(), exception.getCode());

        // 验证 updateById 未被调用
        verify(alertMapper, never()).updateById(any(Alert.class));
        // 验证 分配日志未被记录
        verify(assignmentLogMapper, never()).insert(any());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建测试用预警对象
     */
    private Alert buildTestAlert() {
        Alert alert = new Alert();
        alert.setId(1L);
        alert.setAlertNo("ALT20260101120000123456");
        alert.setCustomerId(100L);
        alert.setCustomerName("张三");
        alert.setAlertType("SUSPICIOUS");
        alert.setRiskScore(75);
        alert.setRiskLevel("HIGH");
        alert.setSourceRuleCodes("RULE_001,RULE_002");
        alert.setAlertSummary("可疑交易预警");
        alert.setStatus("NEW");
        alert.setCreatedTime(LocalDateTime.now());
        alert.setUpdatedTime(LocalDateTime.now());
        return alert;
    }
}
