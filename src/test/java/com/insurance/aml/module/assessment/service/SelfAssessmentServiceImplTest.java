package com.insurance.aml.module.assessment.service;

import com.insurance.aml.common.enums.AssessmentStatusEnum;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.module.assessment.mapper.AssessmentIndicatorMapper;
import com.insurance.aml.module.assessment.mapper.AssessmentScoreMapper;
import com.insurance.aml.module.assessment.mapper.SelfAssessmentMapper;
import com.insurance.aml.module.assessment.model.dto.AssessmentScoreRequest;
import com.insurance.aml.module.assessment.model.dto.SelfAssessmentDetailVO;
import com.insurance.aml.module.assessment.model.entity.AssessmentIndicator;
import com.insurance.aml.module.assessment.model.entity.AssessmentScore;
import com.insurance.aml.module.assessment.model.entity.SelfAssessment;
import com.insurance.aml.module.assessment.service.impl.SelfAssessmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("风险自评估服务测试")
class SelfAssessmentServiceImplTest {

    @Mock private SelfAssessmentMapper assessmentMapper;
    @Mock private AssessmentIndicatorMapper indicatorMapper;
    @Mock private AssessmentScoreMapper scoreMapper;

    @InjectMocks private SelfAssessmentServiceImpl service;

    private SelfAssessment assessment(String status) {
        SelfAssessment a = new SelfAssessment();
        a.setId(1L);
        a.setAssessmentStatus(status);
        return a;
    }

    private AssessmentScoreRequest scoreReq() {
        AssessmentScoreRequest r = new AssessmentScoreRequest();
        r.setAssessmentId(1L);
        r.setIndicatorId(10L);
        r.setScore(80);
        return r;
    }

    // ---------- submitScore ----------

    @Test
    @DisplayName("submitScore：评估不存在抛异常")
    void submitScore_assessmentNotFound_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("评估不存在"));
    }

    @Test
    @DisplayName("submitScore：状态为COMPLETED时拒绝评分")
    void submitScore_completedStatus_rejected() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.COMPLETED.getCode()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("不允许评分"));
    }

    @Test
    @DisplayName("submitScore：状态为APPROVED时拒绝评分（守卫对称性）")
    void submitScore_approvedStatus_rejected() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.APPROVED.getCode()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("不允许评分"));
    }

    @Test
    @DisplayName("submitScore：CREATED自动转IN_PROGRESS")
    void submitScore_createdStatus_autoTransitions() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.CREATED.getCode()));
        when(indicatorMapper.selectById(10L)).thenReturn(new AssessmentIndicator());
        when(scoreMapper.selectOne(any())).thenReturn(null);

        service.submitScore(scoreReq());

        ArgumentCaptor<SelfAssessment> captor = ArgumentCaptor.forClass(SelfAssessment.class);
        verify(assessmentMapper).updateById(captor.capture());
        assertEquals("IN_PROGRESS", captor.getValue().getAssessmentStatus());
    }

    @Test
    @DisplayName("submitScore：指标不存在抛异常")
    void submitScore_indicatorNotFound_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(indicatorMapper.selectById(10L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.submitScore(scoreReq()));
        assertTrue(ex.getMessage().contains("评估指标不存在"));
    }

    @Test
    @DisplayName("submitScore：已有评分走更新分支")
    void submitScore_existingScore_updates() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(indicatorMapper.selectById(10L)).thenReturn(new AssessmentIndicator());
        when(scoreMapper.selectOne(any())).thenReturn(new AssessmentScore());

        service.submitScore(scoreReq());

        verify(scoreMapper).updateById(any(AssessmentScore.class));
        verify(scoreMapper, never()).insert(any(AssessmentScore.class));
    }

    @Test
    @DisplayName("submitScore：无既有评分走新增分支")
    void submitScore_noExistingScore_inserts() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(indicatorMapper.selectById(10L)).thenReturn(new AssessmentIndicator());
        when(scoreMapper.selectOne(any())).thenReturn(null);

        service.submitScore(scoreReq());

        verify(scoreMapper).insert(any(AssessmentScore.class));
        verify(scoreMapper, never()).updateById(any(AssessmentScore.class));
    }

    // ---------- completeAssessment ----------

    private void stubCompleteWith(int inherentVal, int controlVal) {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        AssessmentScore s1 = new AssessmentScore();
        s1.setIndicatorId(10L); s1.setScore(inherentVal);
        AssessmentScore s2 = new AssessmentScore();
        s2.setIndicatorId(20L); s2.setScore(controlVal);
        when(scoreMapper.selectList(any())).thenReturn(List.of(s1, s2));
        AssessmentIndicator i1 = new AssessmentIndicator();
        i1.setId(10L); i1.setCategory("INHERENT_RISK"); i1.setWeight(BigDecimal.ONE);
        AssessmentIndicator i2 = new AssessmentIndicator();
        i2.setId(20L); i2.setCategory("CONTROL_EFFECTIVENESS"); i2.setWeight(BigDecimal.ONE);
        when(indicatorMapper.selectList(any())).thenReturn(List.of(i1, i2));
    }

    @Test
    @DisplayName("completeAssessment：非IN_PROGRESS拒绝")
    void completeAssessment_notInProgress_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.CREATED.getCode()));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.completeAssessment(1L));
        assertTrue(ex.getMessage().contains("只有进行中"));
    }

    @Test
    @DisplayName("completeAssessment：无评分拒绝")
    void completeAssessment_noScores_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        when(scoreMapper.selectList(any())).thenReturn(List.of());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.completeAssessment(1L));
        assertTrue(ex.getMessage().contains("无任何评分"));
    }

    @Test
    @DisplayName("completeAssessment：综合分>70判定HIGH")
    void completeAssessment_highRisk() {
        stubCompleteWith(80, 80);
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(80, result.getInherentRiskScore());
        assertEquals(80, result.getControlEffectivenessScore());
        assertEquals(80, result.getOverallScore());
        assertEquals(RiskLevel.HIGH.getCode(), result.getOverallRiskLevel());
        assertEquals(AssessmentStatusEnum.COMPLETED.getCode(), result.getAssessmentStatus());
        // 结果须落库，而非仅修改内存对象
        verify(assessmentMapper).updateById(any(SelfAssessment.class));
    }

    @Test
    @DisplayName("completeAssessment：综合分40-70判定MEDIUM")
    void completeAssessment_mediumRisk() {
        stubCompleteWith(50, 50);
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(50, result.getOverallScore());
        assertEquals(RiskLevel.MEDIUM.getCode(), result.getOverallRiskLevel());
    }

    @Test
    @DisplayName("completeAssessment：综合分<40判定LOW")
    void completeAssessment_lowRisk() {
        stubCompleteWith(30, 30);
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(30, result.getOverallScore());
        assertEquals(RiskLevel.LOW.getCode(), result.getOverallRiskLevel());
    }

    @Test
    @DisplayName("completeAssessment：综合分恰好=70判定MEDIUM（HIGH下边界off-by-one）")
    void completeAssessment_exactly70_isMedium() {
        stubCompleteWith(70, 70); // overallScore = 70 → 需 >70 才 HIGH
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(70, result.getOverallScore());
        assertEquals(RiskLevel.MEDIUM.getCode(), result.getOverallRiskLevel());
    }

    @Test
    @DisplayName("completeAssessment：综合分恰好=40判定MEDIUM（LOW上边界off-by-one）")
    void completeAssessment_exactly40_isMedium() {
        stubCompleteWith(40, 40); // overallScore = 40 → ≥40 即 MEDIUM
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(40, result.getOverallScore());
        assertEquals(RiskLevel.MEDIUM.getCode(), result.getOverallRiskLevel());
    }

    @Test
    @DisplayName("completeAssessment：综合分=39判定LOW（LOW边界内侧）")
    void completeAssessment_score39_isLow() {
        stubCompleteWith(39, 39); // overallScore = 39 → <40 为 LOW
        SelfAssessment result = service.completeAssessment(1L);
        assertEquals(39, result.getOverallScore());
        assertEquals(RiskLevel.LOW.getCode(), result.getOverallRiskLevel());
    }

    // ---------- approveAssessment ----------

    @Test
    @DisplayName("approveAssessment：非COMPLETED拒绝")
    void approveAssessment_notCompleted_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment("IN_PROGRESS"));
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.approveAssessment(1L, "mgr"));
        assertTrue(ex.getMessage().contains("只有已完成"));
    }

    @Test
    @DisplayName("approveAssessment：成功置APPROVED并记录审批人")
    void approveAssessment_success() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.COMPLETED.getCode()));
        service.approveAssessment(1L, "mgr");
        ArgumentCaptor<SelfAssessment> captor = ArgumentCaptor.forClass(SelfAssessment.class);
        verify(assessmentMapper).updateById(captor.capture());
        assertEquals(AssessmentStatusEnum.APPROVED.getCode(), captor.getValue().getAssessmentStatus());
        assertEquals("mgr", captor.getValue().getApprovedBy());
        assertNotNull(captor.getValue().getApprovedTime());
    }

    // ---------- getAssessmentDetail ----------

    @Test
    @DisplayName("getAssessmentDetail：不存在抛异常")
    void getAssessmentDetail_notFound_throws() {
        when(assessmentMapper.selectById(1L)).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getAssessmentDetail(1L));
        assertTrue(ex.getMessage().contains("评估不存在"));
    }

    @Test
    @DisplayName("getAssessmentDetail：装配指标明细")
    void getAssessmentDetail_assemblesIndicatorDetail() {
        when(assessmentMapper.selectById(1L)).thenReturn(assessment(AssessmentStatusEnum.COMPLETED.getCode()));
        AssessmentScore s = new AssessmentScore();
        s.setId(100L); s.setAssessmentId(1L); s.setIndicatorId(10L); s.setScore(88);
        when(scoreMapper.selectList(any())).thenReturn(List.of(s));
        AssessmentIndicator ind = new AssessmentIndicator();
        ind.setId(10L); ind.setIndicatorCode("I1"); ind.setIndicatorName("指标1");
        ind.setCategory("INHERENT_RISK"); ind.setDimension("D1"); ind.setWeight(new BigDecimal("2"));
        when(indicatorMapper.selectList(any())).thenReturn(List.of(ind));

        SelfAssessmentDetailVO vo = service.getAssessmentDetail(1L);

        assertNotNull(vo.getScores());
        assertEquals(1, vo.getScores().size());
        assertEquals(100L, vo.getScores().get(0).getId());
        assertEquals(88, vo.getScores().get(0).getScore());
        assertEquals("I1", vo.getScores().get(0).getIndicatorCode());
        assertEquals("指标1", vo.getScores().get(0).getIndicatorName());
        assertEquals("INHERENT_RISK", vo.getScores().get(0).getCategory());
        assertEquals(new BigDecimal("2"), vo.getScores().get(0).getWeight());
    }
}
