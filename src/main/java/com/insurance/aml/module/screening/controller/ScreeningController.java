package com.insurance.aml.module.screening.controller;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.screening.mapper.WhitelistMapper;
import com.insurance.aml.module.screening.model.dto.ReviewRequest;
import com.insurance.aml.module.screening.model.dto.ScreeningRequestDTO;
import com.insurance.aml.module.screening.model.dto.ScreeningResultVO;
import com.insurance.aml.module.screening.model.entity.Whitelist;
import com.insurance.aml.module.screening.service.ScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 制裁名单筛查控制器
 * 提供客户筛查、批量筛查、结果审核、白名单管理等接口
 */
@RestController
@RequestMapping("/screening")
@Tag(name = "名单筛查")
@Slf4j
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;
    private final WhitelistMapper whitelistMapper;

    /**
     * 触发单个客户的制裁名单筛查
     */
    @PostMapping("/screen")
    @Operation(summary = "触发客户筛查", description = "对指定客户进行制裁名单筛查匹配")
    public Result<Long> screenCustomer(@Valid @RequestBody ScreeningRequestDTO dto) {
        log.info("收到筛查请求，customerId={}, screeningType={}", dto.getCustomerId(), dto.getScreeningType());
        Long hitCount = screeningService.screenCustomer(dto.getCustomerId(), dto.getScreeningType());
        return Result.success(hitCount);
    }

    /**
     * 批量筛查多个客户
     */
    @PostMapping("/batch-screen")
    @Operation(summary = "批量筛查", description = "批量对多个客户进行制裁名单筛查")
    public Result<List<Long>> batchScreen(@RequestBody List<Long> customerIds) {
        log.info("收到批量筛查请求，客户数={}", customerIds.size());
        List<Long> results = screeningService.screenBatch(customerIds);
        return Result.success(results);
    }

    /**
     * 分页查询筛查结果
     */
    @GetMapping("/results")
    @Operation(summary = "查询筛查结果", description = "分页查询筛查结果列表，支持按客户ID和审核状态过滤")
    public Result<PageResult<ScreeningResultVO>> getResults(
            PageQuery pageQuery,
            @Parameter(description = "客户ID") @RequestParam(required = false) Long customerId,
            @Parameter(description = "审核状态（PENDING_REVIEW/CONFIRMED/EXCLUDED/ESCALATED）") @RequestParam(required = false) String reviewStatus) {
        PageResult<ScreeningResultVO> result = screeningService.pageResults(pageQuery, customerId, reviewStatus);
        return Result.success(result);
    }

    /**
     * 审核筛查命中结果
     */
    @PostMapping("/review")
    @Operation(summary = "审核筛查命中", description = "对筛查命中的结果进行人工审核，确认或排除")
    public Result<Void> reviewHit(@Valid @RequestBody ReviewRequest req) {
        log.info("收到审核请求，resultId={}, reviewStatus={}", req.getResultId(), req.getReviewStatus());
        screeningService.reviewHit(req);
        return Result.success();
    }

    /**
     * 查询白名单列表
     */
    @GetMapping("/whitelist")
    @Operation(summary = "查询白名单", description = "分页查询白名单列表")
    public Result<PageResult<Whitelist>> getWhitelist(PageQuery pageQuery) {
        List<Whitelist> list = whitelistMapper.selectList(null);
        // 简单分页处理
        PageResult<Whitelist> pageResult = PageResult.<Whitelist>builder()
                .total(list.size())
                .list(list.stream().skip((long) (pageQuery.getPage() - 1) * pageQuery.getSize())
                        .limit(pageQuery.getSize()).toList())
                .page(pageQuery.getPage())
                .size(pageQuery.getSize())
                .totalPages((int) Math.ceil((double) list.size() / pageQuery.getSize()))
                .build();
        return Result.success(pageResult);
    }

    /**
     * 新增白名单条目
     */
    @PostMapping("/whitelist")
    @Operation(summary = "新增白名单", description = "将筛查命中结果加入白名单，后续筛查将不再告警")
    public Result<Void> addWhitelist(@RequestBody Whitelist whitelist) {
        log.info("新增白名单，customerId={}, watchlistEntryId={}", whitelist.getCustomerId(), whitelist.getWatchlistEntryId());
        whitelistMapper.insert(whitelist);
        return Result.success();
    }
}
