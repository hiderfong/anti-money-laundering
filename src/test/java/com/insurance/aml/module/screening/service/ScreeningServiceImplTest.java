package com.insurance.aml.module.screening.service;

import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.screening.mapper.*;
import com.insurance.aml.module.screening.model.dto.ReviewRequest;
import com.insurance.aml.module.screening.model.entity.*;
import com.insurance.aml.module.screening.service.impl.ScreeningServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 制裁名单筛查服务单元测试
 * 覆盖 screenCustomer / screenBatch / reviewHit 核心方法
 * 使用WatchlistCacheService和WhitelistCacheService进行缓存数据加载
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

    @Mock
    WatchlistCacheService watchlistCacheService;

    @Mock
    WhitelistCacheService whitelistCacheService;

    @InjectMocks
    ScreeningServiceImpl screeningService;

    @BeforeEach
    void setUp() {
        // 默认：白名单缓存返回空
        lenient().when(whitelistCacheService.getAllActiveWhitelists()).thenReturn(Collections.emptyList());
        // 默认：别名和证件缓存返回空Map
        lenient().when(watchlistCacheService.getAllAliasesGrouped()).thenReturn(Collections.emptyMap());
        lenient().when(watchlistCacheService.getAllIdentitiesGrouped()).thenReturn(Collections.emptyMap());
    }

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
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101001");

        // mock客户信息
        when(customerMapper.selectById(100L)).thenReturn(createCustomer(100L, "张三", "E12345678"));

        // mock缓存服务
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.singletonList(watchlistEntry));
        Map<Long, List<WatchlistIdentity>> identitiesMap = new HashMap<>();
        identitiesMap.put(1L, Collections.singletonList(identity));
        when(watchlistCacheService.getAllIdentitiesGrouped()).thenReturn(identitiesMap);

        // mock证件精确匹配
        when(nameMatcher.isExactIdMatch(eq("E12345678"), eq("E12345678"))).thenReturn(true);

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(100L, "REGULAR");

        // 验证
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
     */
    @Test
    @DisplayName("无匹配 -> 不创建筛查结果，返回0")
    void screenCustomer_noMatch() {
        // mock ID生成器
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101002");

        // mock客户信息
        when(customerMapper.selectById(200L)).thenReturn(createCustomer(200L, "不匹配客户", "NO_MATCH_ID"));

        // mock制裁名单查询：返回一个条目但不匹配
        Watchlist watchlistEntry = createWatchlistEntry(2L, "李四");
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.singletonList(watchlistEntry));

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(200L, "REGULAR");

        // 验证
        assertNotNull(hitCount, "命中数不应为空");
        assertEquals(0L, hitCount, "无匹配时命中数应为0");
        verify(screeningRequestMapper).insert(argThat(request ->
                "SCR20260101002".equals(request.getRequestNo())
                        && Long.valueOf(200L).equals(request.getCustomerId())
                        && "REGULAR".equals(request.getScreeningType())
                        && "MANUAL".equals(request.getRequestSource())
        ));
        verify(screeningResultMapper, never()).insert(any(ScreeningResult.class));
    }

    @Test
    @DisplayName("开户筛查 -> 创建请求时写入KYC来源")
    void screenCustomer_customerOnboardSetsKycSource() {
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101006");
        when(customerMapper.selectById(201L)).thenReturn(createCustomer(201L, "开户客户", "ID_201"));
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.emptyList());

        Long hitCount = screeningService.screenCustomer(201L, "CUSTOMER_ONBOARD");

        assertEquals(0L, hitCount, "无名单时命中数应为0");
        verify(screeningRequestMapper).insert(argThat(request ->
                "SCR20260101006".equals(request.getRequestNo())
                        && Long.valueOf(201L).equals(request.getCustomerId())
                        && "CUSTOMER_ONBOARD".equals(request.getScreeningType())
                        && "KYC".equals(request.getRequestSource())
        ));
    }

    /**
     * 测试名称模糊匹配
     */
    @Test
    @DisplayName("名称模糊匹配 -> 相似度90%超过阈值，创建筛查结果")
    void screenCustomer_fuzzyNameMatch() {
        // 准备制裁名单条目
        Watchlist watchlistEntry = createWatchlistEntry(3L, "王五");

        // mock ID生成器
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101003");

        // mock客户信息
        when(customerMapper.selectById(300L)).thenReturn(createCustomer(300L, "王五", "ID_300"));

        // mock制裁名单查询
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.singletonList(watchlistEntry));

        // mock名称相似度计算
        when(nameMatcher.containsChinese(anyString())).thenReturn(true);
        when(nameMatcher.calculateSimilarity(anyString(), anyString(), eq(true))).thenReturn(90.0);

        // 执行筛查
        Long hitCount = screeningService.screenCustomer(300L, "REGULAR");

        // 验证
        assertNotNull(hitCount, "命中数不应为空");
        assertEquals(1L, hitCount, "模糊匹配应命中1条");

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
     */
    @Test
    @DisplayName("名称相似度低于阈值 -> 相似度70%低于85%，不创建筛查结果")
    void screenCustomer_belowThreshold() {
        // 准备
        Watchlist watchlistEntry = createWatchlistEntry(4L, "赵六");
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101004");
        when(customerMapper.selectById(400L)).thenReturn(createCustomer(400L, "赵六", "ID_400"));
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.singletonList(watchlistEntry));
        when(nameMatcher.containsChinese(anyString())).thenReturn(true);
        when(nameMatcher.calculateSimilarity(anyString(), anyString(), eq(true))).thenReturn(70.0);

        // 执行
        Long hitCount = screeningService.screenCustomer(400L, "REGULAR");

        // 验证
        assertEquals(0L, hitCount, "相似度70%低于85%阈值，命中数应为0");
        verify(screeningResultMapper, never()).insert(any(ScreeningResult.class));
    }

    /**
     * 测试审核命中结果 - 确认命中
     */
    @Test
    @DisplayName("审核命中结果-确认 -> 状态更新为CONFIRMED，客户标记为制裁")
    void reviewHit_confirm() {
        // 准备
        ScreeningResult existingResult = new ScreeningResult();
        existingResult.setId(1L);
        existingResult.setCustomerId(100L);
        existingResult.setCustomerName("张三");
        existingResult.setWatchlistEntryId(1L);
        existingResult.setMatchScore(BigDecimal.valueOf(100));
        existingResult.setMatchType("EXACT");
        existingResult.setReviewStatus("PENDING_REVIEW");

        when(screeningResultMapper.selectById(any())).thenReturn(existingResult);

        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setResultId(1L);
        reviewRequest.setReviewStatus("CONFIRMED");
        reviewRequest.setReviewReason("证件号码完全匹配，确认为制裁名单人员");

        // 执行
        screeningService.reviewHit(reviewRequest);

        // 验证
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
     */
    @Test
    @DisplayName("审核命中结果-排除误报 -> 状态更新为EXCLUDED，客户不变")
    void reviewHit_exclude() {
        // 准备
        ScreeningResult existingResult = new ScreeningResult();
        existingResult.setId(2L);
        existingResult.setCustomerId(200L);
        existingResult.setCustomerName("王五");
        existingResult.setWatchlistEntryId(2L);
        existingResult.setMatchScore(BigDecimal.valueOf(88));
        existingResult.setMatchType("FUZZY");
        existingResult.setReviewStatus("PENDING_REVIEW");

        when(screeningResultMapper.selectById(any())).thenReturn(existingResult);

        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setResultId(2L);
        reviewRequest.setReviewStatus("EXCLUDED");
        reviewRequest.setReviewReason("经核实为同名不同人，排除误报");

        // 执行
        screeningService.reviewHit(reviewRequest);

        // 验证
        verify(screeningResultMapper).updateById(argThat(result ->
                "EXCLUDED".equals(result.getReviewStatus())
                        && result.getReviewedTime() != null
        ));
        verify(customerMapper, never()).updateById(any(Customer.class));
    }

    /**
     * 测试审核结果不存在
     */
    @Test
    @DisplayName("审核-结果不存在 -> 抛出BusinessException")
    void reviewHit_resultNotFound_throwsException() {
        // 准备
        when(screeningResultMapper.selectById(999L)).thenReturn(null);

        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setResultId(999L);
        reviewRequest.setReviewStatus("CONFIRMED");
        reviewRequest.setReviewReason("原因");

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> screeningService.reviewHit(reviewRequest));
    }

    /**
     * 测试无效审核状态
     */
    @Test
    @DisplayName("审核-无效审核状态 -> 抛出BusinessException")
    void reviewHit_invalidStatus_throwsException() {
        ReviewRequest reviewRequest = new ReviewRequest();
        reviewRequest.setResultId(1L);
        reviewRequest.setReviewStatus("INVALID_STATUS");
        reviewRequest.setReviewReason("原因");

        // 执行 & 验证
        assertThrows(BusinessException.class, () -> screeningService.reviewHit(reviewRequest));
    }

    /**
     * 测试客户不存在时筛查
     */
    @Test
    @DisplayName("筛查-客户不存在 -> 抛出BusinessException")
    void screenCustomer_customerNotFound_throwsException() {
        // 准备
        when(idGenerator.generateScreeningNo()).thenReturn("SCR20260101005");
        when(customerMapper.selectById(999L)).thenReturn(null);

        // 执行 & 验证
        assertThrows(BusinessException.class,
                () -> screeningService.screenCustomer(999L, "REGULAR"));
    }

    /**
     * 测试批量筛查
     */
    @Test
    @DisplayName("批量筛查 -> 多客户逐个筛查返回结果列表")
    void screenBatch_success() {
        // 准备：mock 3个客户的筛查
        Watchlist entry = createWatchlistEntry(1L, "制裁人");
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.singletonList(entry));

        when(idGenerator.generateScreeningNo())
                .thenReturn("SCR_B1", "SCR_B2", "SCR_B3");

        when(customerMapper.selectById(1L)).thenReturn(createCustomer(1L, "客户A", "ID_A"));
        when(customerMapper.selectById(2L)).thenReturn(createCustomer(2L, "客户B", "ID_B"));
        when(customerMapper.selectById(3L)).thenReturn(createCustomer(3L, "客户C", "ID_C"));

        // 所有客户都不匹配
        when(nameMatcher.containsChinese(anyString())).thenReturn(true);
        when(nameMatcher.calculateSimilarity(anyString(), anyString(), eq(true))).thenReturn(30.0);

        // 执行
        List<Long> results = screeningService.screenBatch(List.of(1L, 2L, 3L));

        // 验证
        assertNotNull(results, "批量结果不应为空");
        assertEquals(3, results.size(), "应返回3个结果");
        for (Long hitCount : results) {
            assertEquals(0L, hitCount, "每个客户的命中数应为0");
        }
    }

    /**
     * 测试批量筛查中部分客户失败
     */
    @Test
    @DisplayName("批量筛查-部分失败 -> 失败的返回-1")
    void screenBatch_partialFailure() {
        // 准备
        when(watchlistCacheService.getAllActiveWatchlists()).thenReturn(Collections.emptyList());

        when(idGenerator.generateScreeningNo())
                .thenReturn("SCR_F1", "SCR_F2");

        // 客户1正常
        when(customerMapper.selectById(1L)).thenReturn(createCustomer(1L, "正常客户", "ID_OK"));
        // 客户2不存在
        when(customerMapper.selectById(2L)).thenReturn(null);

        // 执行
        List<Long> results = screeningService.screenBatch(List.of(1L, 2L));

        // 验证
        assertEquals(2, results.size(), "应返回2个结果");
        assertEquals(0L, results.get(0), "正常客户命中数应为0");
        assertEquals(-1L, results.get(1), "失败客户应返回-1");
    }
}
