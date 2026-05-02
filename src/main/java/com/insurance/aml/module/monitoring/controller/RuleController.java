package com.insurance.aml.module.monitoring.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.monitoring.mapper.RuleDefinitionMapper;
import com.insurance.aml.module.monitoring.model.entity.RuleDefinition;
import com.insurance.aml.module.monitoring.model.entity.RuleExecutionLog;
import com.insurance.aml.module.monitoring.mapper.RuleExecutionLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则管理控制器
 * 提供规则的增删改查、启用禁用、版本查询等接口
 */
@RestController
@RequestMapping("/monitoring/rules")
@RequiredArgsConstructor
@Tag(name = "规则管理")
public class RuleController {

    private final RuleDefinitionMapper ruleDefinitionMapper;
    private final RuleExecutionLogMapper ruleExecutionLogMapper;

    /**
     * 分页查询规则
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询规则")
    public Result<PageResult<RuleDefinition>> pageQueryRules(@Valid RuleQueryRequest request) {
        LambdaQueryWrapper<RuleDefinition> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(request.getRuleCategory())) {
            wrapper.eq(RuleDefinition::getRuleCategory, request.getRuleCategory());
        }
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(RuleDefinition::getStatus, request.getStatus());
        }
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(w -> w.like(RuleDefinition::getRuleCode, request.getKeyword())
                    .or()
                    .like(RuleDefinition::getRuleName, request.getKeyword()));
        }
        wrapper.orderByAsc(RuleDefinition::getPriority);

        IPage<RuleDefinition> page = request.toPage();
        IPage<RuleDefinition> resultPage = ruleDefinitionMapper.selectPage(page, wrapper);

        return Result.success(PageResult.from(resultPage));
    }

    /**
     * 创建规则
     */
    @PostMapping
    @Operation(summary = "创建规则")
    public Result<RuleDefinition> createRule(@Valid @RequestBody RuleDefinition rule) {
        rule.setCreatedTime(LocalDateTime.now());
        rule.setUpdatedTime(LocalDateTime.now());
        if (!StringUtils.hasText(rule.getStatus())) {
            rule.setStatus("DISABLED");
        }
        ruleDefinitionMapper.insert(rule);
        return Result.success(rule);
    }

    /**
     * 更新规则
     */
    @PutMapping
    @Operation(summary = "更新规则")
    public Result<RuleDefinition> updateRule(@Valid @RequestBody RuleDefinition rule) {
        rule.setUpdatedTime(LocalDateTime.now());
        ruleDefinitionMapper.updateById(rule);
        return Result.success(rule);
    }

    /**
     * 启用规则
     */
    @PostMapping("/{id}/enable")
    @Operation(summary = "启用规则")
    public Result<Void> enableRule(@Parameter(description = "规则ID") @PathVariable Long id) {
        RuleDefinition rule = ruleDefinitionMapper.selectById(id);
        if (rule == null) {
            return Result.fail(404, "规则不存在");
        }
        rule.setStatus("ENABLED");
        rule.setUpdatedTime(LocalDateTime.now());
        ruleDefinitionMapper.updateById(rule);
        return Result.success();
    }

    /**
     * 禁用规则
     */
    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用规则")
    public Result<Void> disableRule(@Parameter(description = "规则ID") @PathVariable Long id) {
        RuleDefinition rule = ruleDefinitionMapper.selectById(id);
        if (rule == null) {
            return Result.fail(404, "规则不存在");
        }
        rule.setStatus("DISABLED");
        rule.setUpdatedTime(LocalDateTime.now());
        ruleDefinitionMapper.updateById(rule);
        return Result.success();
    }

    /**
     * 获取规则执行日志（版本/执行历史）
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "获取规则执行日志")
    public Result<List<RuleExecutionLog>> getRuleVersions(@Parameter(description = "规则ID") @PathVariable Long id) {
        LambdaQueryWrapper<RuleExecutionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RuleExecutionLog::getRuleId, id)
                .orderByDesc(RuleExecutionLog::getExecutionTime);
        List<RuleExecutionLog> logs = ruleExecutionLogMapper.selectList(wrapper);
        return Result.success(logs);
    }

    /**
     * 规则分页查询请求（内部类）
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RuleQueryRequest extends PageQuery {
        private String ruleCategory;
        private String status;
        private String keyword;
    }
}
