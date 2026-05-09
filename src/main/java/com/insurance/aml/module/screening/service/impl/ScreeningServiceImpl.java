package com.insurance.aml.module.screening.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insurance.aml.common.enums.AlertStatus;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.enums.ScreeningStatus;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.screening.mapper.*;
import com.insurance.aml.module.screening.model.dto.ReviewRequest;
import com.insurance.aml.module.screening.model.dto.ScreeningResultVO;
import com.insurance.aml.module.screening.model.entity.*;
import com.insurance.aml.module.screening.service.NameMatcher;
import com.insurance.aml.module.screening.service.ScreeningService;
import com.insurance.aml.module.screening.service.WatchlistCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.insurance.aml.module.screening.service.WhitelistCacheService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 制裁名单筛查服务实现
 * 实现客户筛查、批量筛查、结果审核等核心逻辑
 *
 * 优化说明：
 * - 使用WatchlistCacheService批量加载名单数据到Redis缓存（TTL 5分钟）
 * - 消除N+1查询：一次加载所有名单、别名、证件信息，内存分组匹配
 * - 名单数据变更时自动清除缓存
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScreeningServiceImpl implements ScreeningService {

    private final ScreeningRequestMapper screeningRequestMapper;
    private final ScreeningResultMapper screeningResultMapper;
    private final WatchlistMapper watchlistMapper;
    private final WatchlistAliasMapper watchlistAliasMapper;
    private final WatchlistIdentityMapper watchlistIdentityMapper;
    private final WhitelistMapper whitelistMapper;
    private final CustomerMapper customerMapper;
    private final NameMatcher nameMatcher;
    private final IdGenerator idGenerator;
    private final WatchlistCacheService watchlistCacheService;
    private final WhitelistCacheService whitelistCacheService;

    /**
     * 命中阈值（分数>=此值即视为命中），可通过配置调整
     */
    private static final double HIT_THRESHOLD = 85.0;

    /**
     * 精确匹配分数
     */
    private static final BigDecimal EXACT_SCORE = BigDecimal.valueOf(100);

    /**
     * 对单个客户进行制裁名单筛查
     * 流程：
     * 1. 创建筛查请求记录
     * 2. 加载客户信息
     * 3. 从Redis缓存批量加载所有制裁名单数据（消除N+1）
     * 4. 内存中遍历匹配
     * 5. 更新筛查请求统计
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long screenCustomer(Long customerId, String screeningType) {
        log.info("开始对客户进行制裁名单筛查，customerId={}, screeningType={}", customerId, screeningType);

        // 1. 创建筛查请求记录
        ScreeningRequest request = new ScreeningRequest();
        request.setRequestNo(idGenerator.generateScreeningNo());
        request.setCustomerId(customerId);
        request.setScreeningType(screeningType);
        request.setStatus(ScreeningStatus.PROCESSING.getCode());
        request.setCreatedTime(LocalDateTime.now());
        screeningRequestMapper.insert(request);

        try {
            // 2. 查询客户基本信息（通过客户表查询姓名和证件号）
            Customer customer = customerMapper.selectById(customerId);
            if (customer == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "客户不存在: " + customerId);
            }
            String customerName = customer.getName();
            String customerIdNumber = customer.getIdNumber();

            // 查询该客户是否有白名单记录（通过WhitelistCacheService从缓存加载）
            List<Whitelist> allActiveWhitelists = whitelistCacheService.getAllActiveWhitelists();
            List<Whitelist> whitelists = allActiveWhitelists.stream()
                    .filter(w -> customerId.equals(w.getCustomerId()))
                    .toList();
            boolean hasWhitelist = !whitelists.isEmpty();

            // 3. 从缓存批量加载所有制裁名单数据（消除N+1查询）
            //    名单+别名+证件信息全部一次性加载，Redis缓存5分钟TTL
            List<Watchlist> watchlistEntries = watchlistCacheService.getAllActiveWatchlists();
            Map<Long, List<WatchlistAlias>> aliasesMap = watchlistCacheService.getAllAliasesGrouped();
            Map<Long, List<WatchlistIdentity>> identitiesMap = watchlistCacheService.getAllIdentitiesGrouped();

            log.info("加载到 {} 条有效制裁名单条目", watchlistEntries.size());

            int totalHit = 0;

            // 4. 遍历每个制裁名单条目进行匹配
            for (Watchlist entry : watchlistEntries) {
                boolean matchedEntry = false;

                // 从预加载的Map中获取该条目的别名和证件信息（内存查找，无DB查询）
                List<WatchlistAlias> aliases = aliasesMap.getOrDefault(entry.getId(), List.of());
                List<WatchlistIdentity> identities = identitiesMap.getOrDefault(entry.getId(), List.of());

                // 4a. 检查证件精确匹配
                if (customerIdNumber != null && !customerIdNumber.isBlank()) {
                    for (WatchlistIdentity identity : identities) {
                        if (nameMatcher.isExactIdMatch(customerIdNumber, identity.getIdNumber())) {
                            // 证件号码精确匹配，分数100
                            BigDecimal score = EXACT_SCORE;
                            ScreeningResult result = buildScreeningResult(
                                    request.getId(), customerId, customerName, customerIdNumber,
                                    entry, score, "EXACT", "id_number",
                                    "证件号码精确匹配: 客户[" + customerIdNumber + "] <-> 制裁名单[" + identity.getIdNumber() + "]"
                            );

                            // 检查白名单
                            if (hasWhitelist && isInWhitelist(whitelists, entry.getId())) {
                                result.setWhitelisted(true);
                            }

                            screeningResultMapper.insert(result);
                            totalHit++;
                            matchedEntry = true;
                            break; // 该条目已通过证件匹配命中，无需继续检查名称
                        }
                    }
                }

                if (matchedEntry) {
                    continue;
                }

                // 4b. 检查名称相似度
                if (customerName != null && !customerName.isBlank()) {
                    boolean isChinese = nameMatcher.containsChinese(customerName);

                    // 与制裁名单主名称对比
                    double score = nameMatcher.calculateSimilarity(customerName, entry.getName(), isChinese);
                    if (entry.getNameEn() != null && !entry.getNameEn().isBlank()) {
                        // 如果有英文名也一并对比，取较高分数
                        double enScore = nameMatcher.calculateSimilarity(customerName, entry.getNameEn(), false);
                        score = Math.max(score, enScore);
                    }

                    if (score >= HIT_THRESHOLD) {
                        BigDecimal matchScore = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
                        ScreeningResult result = buildScreeningResult(
                                request.getId(), customerId, customerName, customerIdNumber,
                                entry, matchScore, "FUZZY", "name",
                                "名称相似度匹配: 分数=" + matchScore
                        );

                        // 检查白名单
                        if (hasWhitelist && isInWhitelist(whitelists, entry.getId())) {
                            result.setWhitelisted(true);
                        }

                        screeningResultMapper.insert(result);
                        totalHit++;
                        continue; // 已命中，无需继续对比别名
                    }

                    // 与别名对比
                    for (WatchlistAlias alias : aliases) {
                        double aliasScore = nameMatcher.calculateSimilarity(customerName, alias.getAliasName(), isChinese);
                        if (aliasScore >= HIT_THRESHOLD) {
                            BigDecimal matchScore = BigDecimal.valueOf(aliasScore).setScale(2, RoundingMode.HALF_UP);
                            ScreeningResult result = buildScreeningResult(
                                    request.getId(), customerId, customerName, customerIdNumber,
                                    entry, matchScore, "FUZZY", "alias",
                                    "别名相似度匹配: 客户[" + customerName + "] <-> 别名[" + alias.getAliasName()
                                            + "] 类型=" + alias.getAliasType() + " 分数=" + matchScore
                            );

                            if (hasWhitelist && isInWhitelist(whitelists, entry.getId())) {
                                result.setWhitelisted(true);
                            }

                            screeningResultMapper.insert(result);
                            totalHit++;
                            break; // 该条目已通过别名匹配命中
                        }
                    }
                }
            }

            // 5. 更新筛查请求统计
            request.setTotalScanned(watchlistEntries.size());
            request.setTotalHit(totalHit);
            request.setStatus(ScreeningStatus.COMPLETED.getCode());
            request.setCompletedTime(LocalDateTime.now());
            screeningRequestMapper.updateById(request);

            log.info("客户制裁名单筛查完成，customerId={}, 扫描={}, 命中={}", customerId, watchlistEntries.size(), totalHit);
            return (long) totalHit;

        } catch (BusinessException e) {
            log.error("制裁名单筛查业务异常，customerId={}", customerId, e);
            request.setStatus(ScreeningStatus.FAILED.getCode());
            request.setErrorMessage(e.getMessage());
            screeningRequestMapper.updateById(request);
            throw e;
        } catch (Exception e) {
            log.error("制裁名单筛查异常，customerId={}", customerId, e);
            request.setStatus(ScreeningStatus.FAILED.getCode());
            request.setErrorMessage(e.getMessage());
            screeningRequestMapper.updateById(request);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "筛查处理失败: " + e.getMessage());
        }
    }

    /**
     * 批量筛查多个客户
     */
    @Override
    public List<Long> screenBatch(List<Long> customerIds) {
        log.info("开始批量制裁名单筛查，客户数={}", customerIds.size());
        List<Long> results = new ArrayList<>();
        for (Long customerId : customerIds) {
            try {
                Long hitCount = screenCustomer(customerId, "BATCH");
                results.add(hitCount);
            } catch (Exception e) {
                log.error("批量筛查中客户筛查失败，customerId={}", customerId, e);
                results.add(-1L); // -1 表示筛查失败
            }
        }
        log.info("批量制裁名单筛查完成，总数={}", customerIds.size());
        return results;
    }

    /**
     * 审核筛查命中结果
     * 如果审核为确认命中（CONFIRMED），则标记客户为制裁状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewHit(ReviewRequest req) {
        log.info("审核筛查命中结果，resultId={}, reviewStatus={}", req.getResultId(), req.getReviewStatus());

        // 校验审核状态合法性
        if (!AlertStatus.CONFIRMED.getCode().equals(req.getReviewStatus()) && !AlertStatus.EXCLUDED.getCode().equals(req.getReviewStatus())) {
            throw new BusinessException("审核状态必须为 CONFIRMED 或 EXCLUDED");
        }

        // 查询筛查结果
        ScreeningResult result = screeningResultMapper.selectById(req.getResultId());
        if (result == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "筛查结果不存在: " + req.getResultId());
        }

        // 更新审核信息
        result.setReviewStatus(req.getReviewStatus());
        result.setReviewReason(req.getReviewReason());
        result.setReviewedTime(LocalDateTime.now());
        screeningResultMapper.updateById(result);

        // 如果确认命中，标记客户为制裁状态并提升为高风险
        if (AlertStatus.CONFIRMED.getCode().equals(req.getReviewStatus())) {
            log.warn("客户被确认命中制裁名单，customerId={}, watchlistEntryId={}",
                    result.getCustomerId(), result.getWatchlistEntryId());
            if (result.getCustomerId() != null) {
                Customer customerUpdate = new Customer();
                customerUpdate.setId(result.getCustomerId());
                customerUpdate.setIsSanctioned(true);
                customerUpdate.setRiskLevel(RiskLevel.HIGH.getCode());
                customerUpdate.setRiskScore(100);
                customerUpdate.setRiskUpdateTime(LocalDateTime.now());
                customerMapper.updateById(customerUpdate);
            }
        }

        log.info("筛查结果审核完成，resultId={}", req.getResultId());
    }

    /**
     * 分页查询筛查结果
     */
    @Override
    public PageResult<ScreeningResultVO> pageResults(PageQuery pageQuery, Long customerId, String reviewStatus) {
        IPage<ScreeningResult> page = pageQuery.toPage();

        LambdaQueryWrapper<ScreeningResult> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(ScreeningResult::getCustomerId, customerId);
        }
        if (reviewStatus != null && !reviewStatus.isBlank()) {
            wrapper.eq(ScreeningResult::getReviewStatus, reviewStatus);
        }
        wrapper.orderByDesc(ScreeningResult::getCreatedTime);

        screeningResultMapper.selectPage(page, wrapper);

        // 转换为VO
        Page<ScreeningResultVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ScreeningResultVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .toList();
        voPage.setRecords(voList);

        return PageResult.from(voPage);
    }

    /**
     * 构建筛查结果对象
     */
    private ScreeningResult buildScreeningResult(Long requestId, Long customerId,
                                                  String customerName, String customerIdNumber,
                                                  Watchlist entry, BigDecimal matchScore,
                                                  String matchType, String matchField,
                                                  String matchDetail) {
        ScreeningResult result = new ScreeningResult();
        result.setRequestId(String.valueOf(requestId));
        result.setCustomerId(customerId);
        result.setCustomerName(customerName);
        result.setCustomerIdNumber(customerIdNumber);
        result.setWatchlistEntryId(entry.getId());
        result.setWatchlistName(entry.getName());
        result.setMatchScore(matchScore);
        result.setMatchType(matchType);
        result.setMatchField(matchField);
        result.setMatchDetail(matchDetail);
        result.setReviewStatus(ReportStatus.PENDING_REVIEW.getCode());
        result.setWhitelisted(false);
        result.setCreatedTime(LocalDateTime.now());
        return result;
    }

    /**
     * 判断客户是否在白名单中（排除特定制裁名单条目）
     */
    private boolean isInWhitelist(List<Whitelist> whitelists, Long watchlistEntryId) {
        return whitelists.stream()
                .anyMatch(w -> w.getWatchlistEntryId() != null && w.getWatchlistEntryId().equals(watchlistEntryId));
    }


    /**
     * 将实体转换为展示VO
     */
    private ScreeningResultVO toVO(ScreeningResult entity) {
        ScreeningResultVO vo = new ScreeningResultVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
