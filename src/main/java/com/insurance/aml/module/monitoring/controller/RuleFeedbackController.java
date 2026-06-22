package com.insurance.aml.module.monitoring.controller;

import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.monitoring.model.vo.RuleFeedbackVO;
import com.insurance.aml.module.monitoring.service.RuleFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 规则反馈闭环控制器
 *
 * 提供规则效果评估、阈值调整建议等接口
 * 支撑"规则执行 → 人工反馈 → 阈值优化"的自学习闭环
 */
@RestController
@RequestMapping("/monitoring/rules/feedback")
@RequiredArgsConstructor
@Tag(name = "规则反馈闭环", description = "规则效果评估与阈值优化建议")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
public class RuleFeedbackController {

    private final RuleFeedbackService ruleFeedbackService;

    /**
     * 获取所有规则的反馈统计汇总
     * 包含每条规则的命中量、确认率、误报率、阈值调整建议
     */
    @GetMapping("/summary")
    @Operation(summary = "规则反馈统计汇总", description = "统计所有启用规则的命中/确认/误报数据，生成阈值调整建议")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
    public Result<RuleFeedbackVO.FeedbackSummary> getFeedbackSummary() {
        RuleFeedbackVO.FeedbackSummary summary = ruleFeedbackService.calculateRuleStats();
        return Result.success(summary);
    }

    /**
     * 获取单条规则的反馈详情（按规则ID）
     */
    @GetMapping("/rule/{ruleId}")
    @Operation(summary = "单条规则反馈详情", description = "按规则ID查询该规则的命中/确认/误报统计及阈值建议")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
    public Result<RuleFeedbackVO> getRuleFeedback(
            @Parameter(description = "规则ID") @PathVariable Long ruleId) {
        RuleFeedbackVO vo = ruleFeedbackService.getRuleFeedback(ruleId);
        return Result.success(vo);
    }

    /**
     * 获取单条规则的反馈详情（按规则编码）
     */
    @GetMapping("/rule/code/{ruleCode}")
    @Operation(summary = "按规则编码查询反馈", description = "按规则编码查询该规则的命中/确认/误报统计及阈值建议")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
    public Result<RuleFeedbackVO> getRuleFeedbackByCode(
            @Parameter(description = "规则编码") @PathVariable String ruleCode) {
        RuleFeedbackVO vo = ruleFeedbackService.getRuleFeedbackByCode(ruleCode);
        return Result.success(vo);
    }

    /**
     * 获取需要关注的规则列表
     * 只返回系统建议调整阈值的规则（RELAX或TIGHTEN）
     */
    @GetMapping("/attention")
    @Operation(summary = "需关注的规则", description = "返回系统建议调整阈值的规则列表（确认率异常或命中量异常）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('monitoring:view')")
    public Result<List<RuleFeedbackVO>> getRulesNeedingAttention() {
        List<RuleFeedbackVO> list = ruleFeedbackService.getRulesNeedingAttention();
        return Result.success(list);
    }
}
