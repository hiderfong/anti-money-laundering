package com.insurance.aml.module.monitoring.service;

import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.module.monitoring.event.TransactionCommittedEvent;
import com.insurance.aml.module.monitoring.mapper.TransactionDailySummaryMapper;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.dto.TransactionIngestRequest;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.monitoring.model.entity.TransactionDailySummary;
import com.insurance.aml.module.monitoring.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 交易服务实现类单元测试
 * 覆盖 ingestTransaction / ingestTransactionAsync 核心方法
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("交易服务测试")
class TransactionServiceImplTest {

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionDailySummaryMapper dailySummaryMapper;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private Executor amlTaskExecutor;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private TransactionIngestRequest testRequest;

    @BeforeEach
    void setUp() {
        testRequest = new TransactionIngestRequest();
        testRequest.setTransactionNo("TXN_TEST_001");
        testRequest.setCustomerId(100L);
        testRequest.setPolicyId(200L);
        testRequest.setTransactionType("PREMIUM");
        testRequest.setAmount(new BigDecimal("50000.00"));
        testRequest.setPaymentMethod("TRANSFER");
        testRequest.setTransactionTime(LocalDateTime.now());

        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 测试正常入库并发布事件
     */
    @Test
    @DisplayName("正常入库 -> 交易保存成功并发布TransactionCommittedEvent")
    void ingestTransaction_success_savesAndPublishesEvent() {
        // 准备
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        Transaction result = transactionService.ingestTransaction(testRequest);

        // 验证
        assertNotNull(result, "交易结果不应为空");
        assertEquals("TXN_TEST_001", result.getTransactionNo(), "交易流水号应正确");
        assertEquals(100L, result.getCustomerId(), "客户ID应正确");
        assertEquals(new BigDecimal("50000.00"), result.getAmount(), "金额应正确");
        assertEquals("PREMIUM", result.getTransactionType(), "交易类型应正确");
        assertEquals("CNY", result.getCurrency(), "默认币种应为CNY");
        assertEquals("SUCCESS", result.getStatus(), "默认状态应为SUCCESS");

        // 验证insert调用
        verify(transactionMapper, times(1)).insert(any(Transaction.class));

        // 验证事件发布
        ArgumentCaptor<TransactionCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(TransactionCommittedEvent.class);
        verify(applicationEventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertNotNull(eventCaptor.getValue().getTransaction(), "事件中应包含交易对象");
    }

    /**
     * 测试自动生成交易流水号
     */
    @Test
    @DisplayName("无交易流水号 -> 自动生成业务流水号")
    void ingestTransaction_noTransactionNo_generatesOne() {
        // 准备：不设置transactionNo
        testRequest.setTransactionNo(null);
        when(idGenerator.generate("TXN")).thenReturn("TXN_AUTO_001");
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        Transaction result = transactionService.ingestTransaction(testRequest);

        // 验证
        assertEquals("TXN_AUTO_001", result.getTransactionNo(), "应自动生成交易流水号");
        verify(idGenerator).generate("TXN");
    }

    /**
     * 测试默认值设置
     */
    @Test
    @DisplayName("未设置默认值 -> 自动填充currency、isCrossBorder、status")
    void ingestTransaction_defaultValues_autoSet() {
        // 准备：不设置默认值字段
        testRequest.setCurrency(null);
        testRequest.setIsCrossBorder(null);
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        Transaction result = transactionService.ingestTransaction(testRequest);

        // 验证默认值
        assertEquals("CNY", result.getCurrency(), "默认币种应为CNY");
        assertFalse(result.getIsCrossBorder(), "默认非跨境交易");
        assertEquals("SUCCESS", result.getStatus(), "默认状态应为SUCCESS");
    }

    /**
     * 测试日汇总更新 - 新增记录
     */
    @Test
    @DisplayName("首次交易 -> 新增日汇总记录")
    void ingestTransaction_firstTransaction_createsDailySummary() {
        // 准备：日汇总查询返回null（首次交易）
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        transactionService.ingestTransaction(testRequest);

        // 验证：应插入新的日汇总记录
        ArgumentCaptor<TransactionDailySummary> summaryCaptor =
                ArgumentCaptor.forClass(TransactionDailySummary.class);
        verify(dailySummaryMapper).insert(summaryCaptor.capture());

        TransactionDailySummary summary = summaryCaptor.getValue();
        assertEquals(100L, summary.getCustomerId(), "日汇总客户ID应正确");
        assertEquals("PREMIUM", summary.getTransactionType(), "日汇总交易类型应正确");
        assertEquals(new BigDecimal("50000.00"), summary.getTotalAmount(), "日汇总金额应正确");
        assertEquals(1, summary.getTransactionCount(), "日汇总笔数应为1");
    }

    /**
     * 测试日汇总更新 - 累加已有记录
     */
    @Test
    @DisplayName("重复交易 -> 累加日汇总记录")
    void ingestTransaction_existingSummary_updatesSummary() {
        // 准备：已有的日汇总记录
        TransactionDailySummary existingSummary = new TransactionDailySummary();
        existingSummary.setId(1L);
        existingSummary.setCustomerId(100L);
        existingSummary.setSummaryDate(java.time.LocalDate.now());
        existingSummary.setTransactionType("PREMIUM");
        existingSummary.setTotalAmount(new BigDecimal("30000.00"));
        existingSummary.setTransactionCount(2);

        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(existingSummary);
        when(dailySummaryMapper.updateById(any())).thenReturn(1);

        // 执行
        transactionService.ingestTransaction(testRequest);

        // 验证：应更新已有记录
        ArgumentCaptor<TransactionDailySummary> summaryCaptor =
                ArgumentCaptor.forClass(TransactionDailySummary.class);
        verify(dailySummaryMapper).updateById(summaryCaptor.capture());

        TransactionDailySummary updated = summaryCaptor.getValue();
        assertEquals(new BigDecimal("80000.00"), updated.getTotalAmount(),
                "日汇总金额应累加：30000+50000=80000");
        assertEquals(3, updated.getTransactionCount(), "日汇总笔数应为3");
    }

    /**
     * 测试DB插入失败抛异常且不发布事件
     */
    @Test
    @DisplayName("DB插入失败 -> 抛出异常且不发布事件")
    void ingestTransaction_insertFails_throwsAndNoEvent() {
        // 准备
        when(transactionMapper.insert(any(Transaction.class)))
                .thenThrow(new RuntimeException("DB constraint violation"));

        // 执行 & 验证
        assertThrows(RuntimeException.class,
                () -> transactionService.ingestTransaction(testRequest));

        // 验证：事件未被发布
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    /**
     * 测试异步入库成功
     */
    @Test
    @DisplayName("异步入库 -> 返回CompletableFuture且交易入库成功")
    void ingestTransactionAsync_success_returnsFuture() throws Exception {
        // 准备
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);

        // Mock异步执行器：直接在当前线程执行
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(amlTaskExecutor).execute(any(Runnable.class));

        // 日汇总mock
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        CompletableFuture<Transaction> future = transactionService.ingestTransactionAsync(testRequest);

        // 验证
        assertNotNull(future, "Future不应为空");
        Transaction result = future.get();
        assertNotNull(result, "异步结果不应为空");
        assertEquals("TXN_TEST_001", result.getTransactionNo(), "交易流水号应正确");

        // 验证事件发布
        verify(applicationEventPublisher, times(1)).publishEvent(any(TransactionCommittedEvent.class));
    }

    /**
     * 测试异步入库 - 日汇总更新失败不影响主流程
     */
    @Test
    @DisplayName("异步入库-日汇总失败 -> 交易仍入库成功")
    void ingestTransactionAsync_dailySummaryFails_transactionStillSaved() throws Exception {
        // 准备
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);

        // Mock异步执行器，日汇总查询抛异常
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(amlTaskExecutor).execute(any(Runnable.class));

        when(dailySummaryMapper.selectOne(any()))
                .thenThrow(new RuntimeException("DB timeout"));

        // 执行
        CompletableFuture<Transaction> future = transactionService.ingestTransactionAsync(testRequest);

        // 验证：交易仍入库成功（日汇总失败不影响主流程）
        Transaction result = future.get();
        assertNotNull(result, "即使日汇总失败，交易仍应入库成功");
        assertEquals("TXN_TEST_001", result.getTransactionNo());
    }

    /**
     * 测试Redis异常不影响交易入库
     */
    @Test
    @DisplayName("Redis异常 -> 交易入库不受影响")
    void ingestTransaction_redisFails_transactionStillSaved() {
        // 准备
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);
        when(valueOperations.increment(anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis连接失败"));

        // 执行：Redis异常被内部catch，不影响主流程
        Transaction result = transactionService.ingestTransaction(testRequest);

        // 验证
        assertNotNull(result, "Redis异常不应影响交易入库");
        assertEquals("TXN_TEST_001", result.getTransactionNo());
    }

    /**
     * 测试指定交易流水号时不自动生成
     */
    @Test
    @DisplayName("指定交易流水号 -> 不自动生成")
    void ingestTransaction_withTransactionNo_doesNotGenerate() {
        // 准备：已设置transactionNo
        testRequest.setTransactionNo("TXN_CUSTOM_123");
        when(transactionMapper.insert(any(Transaction.class))).thenReturn(1);
        when(dailySummaryMapper.selectOne(any())).thenReturn(null);
        when(dailySummaryMapper.insert(any())).thenReturn(1);

        // 执行
        Transaction result = transactionService.ingestTransaction(testRequest);

        // 验证
        assertEquals("TXN_CUSTOM_123", result.getTransactionNo(), "应使用指定的交易流水号");
        verify(idGenerator, never()).generate(anyString());
    }
}
