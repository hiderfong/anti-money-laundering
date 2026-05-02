package com.insurance.aml.module.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.insurance.aml.module.assessment.mapper.AssessmentIndicatorMapper;
import com.insurance.aml.module.assessment.mapper.AssessmentScoreMapper;
import com.insurance.aml.module.assessment.mapper.SelfAssessmentMapper;
import com.insurance.aml.module.assessment.model.dto.AssessmentCreateRequest;
import com.insurance.aml.module.assessment.model.dto.AssessmentScoreRequest;
import com.insurance.aml.module.assessment.model.dto.AssessmentScoreVO;
import com.insurance.aml.module.assessment.model.dto.SelfAssessmentDetailVO;
import com.insurance.aml.module.assessment.model.entity.AssessmentIndicator;
import com.insurance.aml.module.assessment.model.entity.AssessmentScore;
import com.insurance.aml.module.assessment.model.entity.SelfAssessment;
import com.insurance.aml.module.assessment.service.SelfAssessmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 风险自评估服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SelfAssessmentServiceImpl implements SelfAssessmentService {

    private final SelfAssessmentMapper assessmentMapper;
    private final AssessmentIndicatorMapper indicatorMapper;
    private final AssessmentScoreMapper scoreMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SelfAssessment createAssessment(AssessmentCreateRequest req) {
        log.info("创建风险自评估，year={}, period={}", req.getAssessmentYear(), req.getAssessmentPeriod());

        SelfAssessment assessment = new SelfAssessment();
        assessment.setAssessmentYear(req.getAssessmentYear());
        assessment.setAssessmentPeriod(req.getAssessmentPeriod());
        assessment.setAssessorId(req.getAssessorId());
        assessment.setAssessmentStatus("DRAFT");
        assessment.setCreatedTime(LocalDateTime.now());
        assessment.setUpdatedTime(LocalDateTime.now());
        assessmentMapper.insert(assessment);

        log.info("风险自评估创建成功，id={}", assessment.getId());
        return assessment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(AssessmentScoreRequest req) {
        log.info("提交评估评分，assessmentId={}, indicatorId={}", req.getAssessmentId(), req.getIndicatorId());

        // 校验评估状态必须为DRAFT或IN_PROGRESS
        SelfAssessment assessment = assessmentMapper.selectById(req.getAssessmentId());
        if (assessment == null) {
            throw new RuntimeException("评估不存在，id=" + req.getAssessmentId());
        }
        if (!"IN_PROGRESS".equals(assessment.getAssessmentStatus())
                && !"DRAFT".equals(assessment.getAssessmentStatus())) {
            throw new RuntimeException("当前评估状态不允许评分，status=" + assessment.getAssessmentStatus());
        }

        // 如果是草稿状态，自动切换为进行中
        if ("DRAFT".equals(assessment.getAssessmentStatus())) {
            assessment.setAssessmentStatus("IN_PROGRESS");
            assessment.setUpdatedTime(LocalDateTime.now());
            assessmentMapper.updateById(assessment);
        }

        // 校验指标是否存在
        AssessmentIndicator indicator = indicatorMapper.selectById(req.getIndicatorId());
        if (indicator == null) {
            throw new RuntimeException("评估指标不存在，id=" + req.getIndicatorId());
        }

        // 保存评分明细（如果已有该指标评分则更新）
        LambdaQueryWrapper<AssessmentScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssessmentScore::getAssessmentId, req.getAssessmentId())
                .eq(AssessmentScore::getIndicatorId, req.getIndicatorId());
        AssessmentScore existing = scoreMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setRawValue(req.getRawValue());
            existing.setScore(req.getScore());
            existing.setEvidence(req.getEvidence());
            existing.setDataSource(req.getDataSource());
            existing.setRemark(req.getRemark());
            scoreMapper.updateById(existing);
        } else {
            AssessmentScore score = new AssessmentScore();
            score.setAssessmentId(req.getAssessmentId());
            score.setIndicatorId(req.getIndicatorId());
            score.setRawValue(req.getRawValue());
            score.setScore(req.getScore());
            score.setEvidence(req.getEvidence());
            score.setDataSource(req.getDataSource());
            score.setRemark(req.getRemark());
            score.setScoredTime(LocalDateTime.now());
            score.setCreatedTime(LocalDateTime.now());
            scoreMapper.insert(score);
        }

        log.info("评估评分提交成功，assessmentId={}, indicatorId={}", req.getAssessmentId(), req.getIndicatorId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SelfAssessment completeAssessment(Long assessmentId) {
        log.info("完成评估，assessmentId={}", assessmentId);

        SelfAssessment assessment = assessmentMapper.selectById(assessmentId);
        if (assessment == null) {
            throw new RuntimeException("评估不存在，id=" + assessmentId);
        }
        if (!"IN_PROGRESS".equals(assessment.getAssessmentStatus())) {
            throw new RuntimeException("只有进行中的评估才能完成，当前状态=" + assessment.getAssessmentStatus());
        }

        // 查询所有评分明细
        LambdaQueryWrapper<AssessmentScore> scoreWrapper = new LambdaQueryWrapper<>();
        scoreWrapper.eq(AssessmentScore::getAssessmentId, assessmentId);
        List<AssessmentScore> scores = scoreMapper.selectList(scoreWrapper);

        if (scores.isEmpty()) {
            throw new RuntimeException("评估无任何评分记录，无法完成");
        }

        // 查询所有启用的指标
        LambdaQueryWrapper<AssessmentIndicator> indicatorWrapper = new LambdaQueryWrapper<>();
        indicatorWrapper.eq(AssessmentIndicator::getStatus, "ENABLED");
        List<AssessmentIndicator> indicators = indicatorMapper.selectList(indicatorWrapper);

        // 计算固有风险和控制有效性的加权评分
        BigDecimal inherentRiskWeighted = BigDecimal.ZERO;
        BigDecimal controlEffectivenessWeighted = BigDecimal.ZERO;
        BigDecimal totalWeightInherent = BigDecimal.ZERO;
        BigDecimal totalWeightControl = BigDecimal.ZERO;

        for (AssessmentScore s : scores) {
            AssessmentIndicator indicator = indicators.stream()
                    .filter(i -> i.getId().equals(s.getIndicatorId()))
                    .findFirst()
                    .orElse(null);
            if (indicator == null || s.getScore() == null) continue;

            // 按指标分类计算加权分
            BigDecimal weightedScore = new BigDecimal(s.getScore()).multiply(indicator.getWeight());
            if ("INHERENT_RISK".equals(indicator.getCategory())) {
                inherentRiskWeighted = inherentRiskWeighted.add(weightedScore);
                totalWeightInherent = totalWeightInherent.add(indicator.getWeight());
            } else if ("CONTROL_EFFECTIVENESS".equals(indicator.getCategory())) {
                controlEffectivenessWeighted = controlEffectivenessWeighted.add(weightedScore);
                totalWeightControl = totalWeightControl.add(indicator.getWeight());
            }
        }

        // 计算固有风险评分（加权平均）
        int inherentScore = 0;
        if (totalWeightInherent.compareTo(BigDecimal.ZERO) > 0) {
            inherentScore = inherentRiskWeighted.divide(totalWeightInherent, 0, RoundingMode.HALF_UP).intValue();
        }

        // 计算控制有效性评分（加权平均）
        int controlScore = 0;
        if (totalWeightControl.compareTo(BigDecimal.ZERO) > 0) {
            controlScore = controlEffectivenessWeighted.divide(totalWeightControl, 0, RoundingMode.HALF_UP).intValue();
        }

        // 综合评分 = 固有风险评分 * 0.6 + 控制有效性评分 * 0.4
        int overallScore = (int) (inherentScore * 0.6 + controlScore * 0.4);

        // 确定综合风险等级：>70=HIGH, 40-70=MEDIUM, <40=LOW
        String riskLevel;
        if (overallScore > 70) {
            riskLevel = "HIGH";
        } else if (overallScore >= 40) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        // 更新评估记录
        assessment.setInherentRiskScore(inherentScore);
        assessment.setControlEffectivenessScore(controlScore);
        assessment.setOverallScore(overallScore);
        assessment.setOverallRiskLevel(riskLevel);
        assessment.setAssessmentStatus("COMPLETED");
        assessment.setUpdatedTime(LocalDateTime.now());
        assessmentMapper.updateById(assessment);

        log.info("评估完成，assessmentId={}, overallScore={}, riskLevel={}", assessmentId, overallScore, riskLevel);
        return assessment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAssessment(Long assessmentId, String approvedBy) {
        log.info("审批评估，assessmentId={}, approvedBy={}", assessmentId, approvedBy);

        SelfAssessment assessment = assessmentMapper.selectById(assessmentId);
        if (assessment == null) {
            throw new RuntimeException("评估不存在，id=" + assessmentId);
        }
        if (!"COMPLETED".equals(assessment.getAssessmentStatus())) {
            throw new RuntimeException("只有已完成的评估才能审批，当前状态=" + assessment.getAssessmentStatus());
        }

        assessment.setAssessmentStatus("APPROVED");
        assessment.setApprovedBy(approvedBy);
        assessment.setApprovedTime(LocalDateTime.now());
        assessment.setUpdatedTime(LocalDateTime.now());
        assessmentMapper.updateById(assessment);

        log.info("评估审批通过，assessmentId={}", assessmentId);
    }

    @Override
    public SelfAssessmentDetailVO getAssessmentDetail(Long assessmentId) {
        log.info("获取评估详情，assessmentId={}", assessmentId);

        SelfAssessment assessment = assessmentMapper.selectById(assessmentId);
        if (assessment == null) {
            throw new RuntimeException("评估不存在，id=" + assessmentId);
        }

        // 构建详情VO
        SelfAssessmentDetailVO detailVO = new SelfAssessmentDetailVO();
        BeanUtils.copyProperties(assessment, detailVO);

        // 查询所有评分明细
        LambdaQueryWrapper<AssessmentScore> scoreWrapper = new LambdaQueryWrapper<>();
        scoreWrapper.eq(AssessmentScore::getAssessmentId, assessmentId);
        List<AssessmentScore> scores = scoreMapper.selectList(scoreWrapper);

        if (!scores.isEmpty()) {
            // 查询所有启用的指标
            LambdaQueryWrapper<AssessmentIndicator> indicatorWrapper = new LambdaQueryWrapper<>();
            indicatorWrapper.eq(AssessmentIndicator::getStatus, "ENABLED");
            List<AssessmentIndicator> indicators = indicatorMapper.selectList(indicatorWrapper);
            Map<Long, AssessmentIndicator> indicatorMap = indicators.stream()
                    .collect(Collectors.toMap(AssessmentIndicator::getId, i -> i));

            // 组装评分明细VO（含指标详情）
            List<AssessmentScoreVO> scoreVOs = scores.stream().map(s -> {
                AssessmentScoreVO vo = new AssessmentScoreVO();
                vo.setId(s.getId());
                vo.setAssessmentId(s.getAssessmentId());
                vo.setIndicatorId(s.getIndicatorId());
                vo.setRawValue(s.getRawValue());
                vo.setScore(s.getScore());
                vo.setEvidence(s.getEvidence());
                vo.setDataSource(s.getDataSource());
                vo.setRemark(s.getRemark());
                vo.setScoredBy(s.getScoredBy());
                vo.setScoredTime(s.getScoredTime());

                // 填充指标详情
                AssessmentIndicator indicator = indicatorMap.get(s.getIndicatorId());
                if (indicator != null) {
                    vo.setIndicatorCode(indicator.getIndicatorCode());
                    vo.setIndicatorName(indicator.getIndicatorName());
                    vo.setCategory(indicator.getCategory());
                    vo.setDimension(indicator.getDimension());
                    vo.setWeight(indicator.getWeight());
                }
                return vo;
            }).toList();

            detailVO.setScores(scoreVOs);
        }

        return detailVO;
    }

    @Override
    public List<SelfAssessment> listAssessments(Integer year) {
        log.info("按年度查询评估列表，year={}", year);
        LambdaQueryWrapper<SelfAssessment> wrapper = new LambdaQueryWrapper<>();
        if (year != null) {
            wrapper.eq(SelfAssessment::getAssessmentYear, year);
        }
        wrapper.orderByDesc(SelfAssessment::getCreatedTime);
        return assessmentMapper.selectList(wrapper);
    }
}
