package com.insurance.aml.module.screening.service;

import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.screening.mapper.*;
import com.insurance.aml.module.screening.model.dto.ReviewRequest;
import com.insurance.aml.module.screening.model.entity.*;
import com.insurance.aml.module.screening.service.impl.ScreeningServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 制裁名单筛查服务单元测试
 * 覆盖 screenCustomer 和 reviewHit 核心方法
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("制裁名单筛查服务测试")
class ScreeningServiceImplTest {

    @Mock
    WatchlistMapper watchlistMapper;

    @Mock
    WatchlistAliasMapper watchlistAliasMapper;

    @Mock
    WatchlistIdentityMapper watchlistIdentityMapper;

    @Mock
    ScreeningResultMapper screeningResultMapper;

    @Mock
    ScreeningRequestMapper screeningRequestMapper;

    @Mock
    WhitelistMapper whitelistMapper;

    @Mock
    CustomerMapper customerMapper;

    @Mock
    NameMatcher nameMatcher;

    @Mock
    IdGenerator idGenerator;

    @InjectMocks
    ScreeningServiceImpl screeningService;

    /**
     * 创建测试用的制裁名单条目
     */
    private Watchlist createWatchlistEntry(Long id, String name) {
        Watchlist entry = new Watchlist();
        entry.setId(id);
        entry.setName(name);
        entry.setNameEn(null);
        entry.setStatus("ACTIVE");
        entry.setEntityType("INDIVIDUAL");
        return entry;
    }

    private Customer createCustomer(Long id, String name, String idNumber) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName(name);
        customer.setIdNumber(idNumber);
        return customer;
    }

    /**
     * 测试证件号码精确匹配
     * 场景：客户证件号与制裁名单中的证件号完全一致，应返回score=100的精确匹配结果
     */
    @Test
    @DisplayName("证件号码精确匹配 -> 命中制裁名单，匹配分数100")
    void screenCustomer_exactIdMatch() {
        // 准备制裁名单条目
        Watchlist watchlistEntry = createWatchlistEntry(1L, "张三");

        // 准备制裁名单证件信息
        WatchlistIdentity identity = new WatchlistIdentity();
        identity.setId("ID_001");
        identity.setWatchlistId(1L);
        identity.setIdType("PASSPORT");
        identity.setIdNumber("E12345678");

        // mock ID生成器
        when(idGenerator.generate("SCR_REQ")).thenReturn("SCR_REQ_001");
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101001");

        // mock客户信息
        when(customerMapper.selectById(100L)).thenReturn(createCustomer(100L, "张三", "E12345678"));

        // mock白名单为空
        when(whitelistMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock制裁名单查询
        when(watchlistMapper.selectList(any())).thenReturn(Collections.singletonList(watchlistEntry));

        // mock证件信息查询
        when(watchlistIdentityMapper.selectList(any())).thenReturn(Collections.singletonList(identity));
        when(watchlistAliasMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock证件精确匹配
        when(nameMatcher.isExactIdMatch(eq("E12345678"), eq("E12345678"))).thenReturn(true);

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(100L, "REGULAR");

        // 验证：命中1条精确匹配结果
        assertNotNull(hitCount, "命中数不应为空");
        assertEquals(1L, hitCount, "证件匹配应命中1条");

        // 验证请求记录已创建
        verify(screeningRequestMapper, atLeastOnce()).insert(any(ScreeningRequest.class));
        verify(screeningResultMapper).insert(argThat(result ->
                100L == result.getCustomerId()
                        && "张三".equals(result.getCustomerName())
                        && "E12345678".equals(result.getCustomerIdNumber())
                        && BigDecimal.valueOf(100).compareTo(result.getMatchScore()) == 0
                        && "EXACT".equals(result.getMatchType())
                        && "id_number".equals(result.getMatchField())
        ));
    }

    /**
     * 测试无匹配场景
     * 场景：客户信息与制裁名单无任何匹配，应不创建筛查结果
     */
    @Test
    @DisplayName("无匹配 -> 不创建筛查结果，返回0")
    void screenCustomer_noMatch() {
        // mock ID生成器
        when(idGenerator.generate("SCR_REQ")).thenReturn("SCR_REQ_002");
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101002");

        // mock客户信息
        when(customerMapper.selectById(200L)).thenReturn(createCustomer(200L, "不匹配客户", "NO_MATCH_ID"));

        // mock白名单为空
        when(whitelistMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock制裁名单查询：返回一个条目但不匹配
        Watchlist watchlistEntry = createWatchlistEntry(2L, "李四");
        when(watchlistMapper.selectList(any())).thenReturn(Collections.singletonList(watchlistEntry));
        when(watchlistIdentityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(watchlistAliasMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(200L, "REGULAR");

        // 验证：无匹配时应返回0
        assertNotNull(hitCount, "命中数不应为空");
        assertEquals(0L, hitCount, "无匹配时命中数应为0");

        // 验证未创建筛查结果
        verify(screeningResultMapper, never()).insert(any(ScreeningResult.class));
    }

    /**
     * 测试名称模糊匹配
     * 场景：客户名称与制裁名单名称相似度为0.9（90%），超过85%阈值，应创建模糊匹配结果
     */
    @Test
    @DisplayName("名称模糊匹配 -> 相似度90%超过阈值，创建筛查结果")
    void screenCustomer_fuzzyNameMatch() {
        // 准备制裁名单条目
        Watchlist watchlistEntry = createWatchlistEntry(3L, "王五");

        // mock ID生成器
        when(idGenerator.generate("SCR_REQ")).thenReturn("SCR_REQ_003");
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101003");

        // mock客户信息
        when(customerMapper.selectById(300L)).thenReturn(createCustomer(300L, "王五", "ID_300"));

        // mock白名单为空
        when(whitelistMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock制裁名单查询
        when(watchlistMapper.selectList(any())).thenReturn(Collections.singletonList(watchlistEntry));
        when(watchlistIdentityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(watchlistAliasMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock名称相似度计算：返回0.9（90%相似度）
        when(nameMatcher.containsChinese(anyString())).thenReturn(true);
        when(nameMatcher.calculateSimilarity(anyString(), anyString(), eq(true))).thenReturn(90.0);

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(300L, "REGULAR");

        // 验证：命中1条模糊匹配结果
        assertNotNull(hitCount, "命中数不应为空");
        assertEquals(1L, hitCount, "模糊匹配应命中1条");

        // 验证请求记录已创建
        verify(screeningRequestMapper, atLeastOnce()).insert(any(ScreeningRequest.class));
        verify(screeningResultMapper).insert(argThat(result ->
                300L == result.getCustomerId()
                        && "王五".equals(result.getCustomerName())
                        && BigDecimal.valueOf(90.00).compareTo(result.getMatchScore()) == 0
                        && "FUZZY".equals(result.getMatchType())
                        && "name".equals(result.getMatchField())
        ));
    }

    /**
     * 测试名称相似度低于阈值
     * 场景：客户名称与制裁名单名称相似度为0.7（70%），低于85%阈值，不应创建结果
     */
    @Test
    @DisplayName("名称相似度低于阈值 -> 相似度70%低于85%，不创建筛查结果")
    void screenCustomer_belowThreshold() {
        // 准备制裁名单条目
        Watchlist watchlistEntry = createWatchlistEntry(4L, "赵六");

        // mock ID生成器
        when(idGenerator.generate("SCR_REQ")).thenReturn("SCR_REQ_004");
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101004");

        // mock客户信息
        when(customerMapper.selectById(400L)).thenReturn(createCustomer(400L, "赵六", "ID_400"));

        // mock白名单为空
        when(whitelistMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock制裁名单查询
        when(watchlistMapper.selectList(any())).thenReturn(Collections.singletonList(watchlistEntry));
        when(watchlistIdentityMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(watchlistAliasMapper.selectList(any())).thenReturn(Collections.emptyList());

        // mock名称相似度计算：返回0.7（70%相似度，低于85%阈值）
        when(nameMatcher.containsChinese(anyString())).thenReturn(true);
        when(nameMatcher.calculateSimilarity(anyString(), anyString(), eq(true))).thenReturn(70.0);

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(400L, "REGULAR");

        // 验证：低于阈值时不应创建筛查结果
        assertNotNull(hitCount, "命中数不应为空");
        assertEquals(0L, hitCount, "相似度70%低于85%阈值，命中数应为0");

        // 验证未创建筛查结果
        verify(screeningResultMapper, never()).insert(any(ScreeningResult.class));
    }

    /**
     * 测试审核命中结果 - 确认命中
     * 场景：审核人员确认筛查命中结果，状态应更新为CONFIRMED
     */
    @Test
    @DisplayName("审核命中结果-确认 -> 状态更新为CONFIRMED")
    void reviewHit_confirm() {
        // 准备已有的筛查结果
        ScreeningResult existingResult = new ScreeningResult();
        existingResult.setId(1L);
        existingResult.setCustomerId(100L);
        existingResult.setCustomerName("张三");
        existingResult.setWatchlistEntryId(1L);
        existingResult.setMatchScore(BigDecimal.valueOf(100));
        existingResult.setMatchType("EXACT");
        existingResult.setReviewStatus("PENDING_REVIEW");

        // mock查询返回已有结果
        when(screeningResultMapper.selectById(any())).thenReturn(existingResult);

        // 准备审核请求：确认命中
        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setResultId(1L);
        reviewRequest.setReviewStatus("CONFIRMED");
        reviewRequest.setReviewReason("证件号码完全匹配，确认为制裁名单人员");

        // 执行审核
        screeningService.reviewHit(reviewRequest);

        // 验证：审核状态应更新为CONFIRMED
        verify(screeningResultMapper).updateById(argThat(result ->
                "CONFIRMED".equals(result.getReviewStatus())
                        && result.getReviewedTime() != null
        ));
        verify(customerMapper).updateById(argThat(customer ->
                100L == customer.getId()
                        && Boolean.TRUE.equals(customer.getIsSanctioned())
                        && "HIGH".equals(customer.getRiskLevel())
                        && Integer.valueOf(100).equals(customer.getRiskScore())
                        && customer.getRiskUpdateTime() != null
        ));
    }

    /**
     * 测试审核命中结果 - 排除误报
     * 场景：审核人员判定筛查命中为误报，状态应更新为EXCLUDED
     */
    @Test
    @DisplayName("审核命中结果-排除误报 -> 状态更新为EXCLUDED")
    void reviewHit_exclude() {
        // 准备已有的筛查结果
        ScreeningResult existingResult = new ScreeningResult();
        existingResult.setId(2L);
        existingResult.setCustomerId(200L);
        existingResult.setCustomerName("王五");
        existingResult.setWatchlistEntryId(2L);
        existingResult.setMatchScore(BigDecimal.valueOf(88));
        existingResult.setMatchType("FUZZY");
        existingResult.setReviewStatus("PENDING_REVIEW");

        // mock查询返回已有结果
        when(screeningResultMapper.selectById(any())).thenReturn(existingResult);

        // 准备审核请求：排除误报
        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setResultId(2L);
        reviewRequest.setReviewStatus("EXCLUDED");
        reviewRequest.setReviewReason("经核实为同名不同人，排除误报");

        // 执行审核
        screeningService.reviewHit(reviewRequest);

        // 验证：审核状态应更新为EXCLUDED
        verify(screeningResultMapper).updateById(argThat(result ->
                "EXCLUDED".equals(result.getReviewStatus())
                        && result.getReviewedTime() != null
        ));
        verify(customerMapper, never()).updateById(any(Customer.class));
    }
}
