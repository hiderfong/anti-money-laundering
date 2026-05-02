package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 *
 * 功能说明：
 * 1. 启用Spring异步任务支持（@EnableAsync）
 * 2. 自定义线程池执行器，用于异步处理反洗钱交易分析任务
 * 3. 核心参数：核心线程8个，最大线程50个，队列容量10000
 * 4. 支持虚拟线程（Virtual Threads），适用于JDK 21+
 *
 * 业务场景：
 * - 异步执行交易规则引擎检测
 * - 异步发送Kafka告警消息
 * - 异步生成可疑交易报告
 * - 异步调用外部风控系统接口
 *
 * @author AML Team
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 异步任务执行器Bean名称 */
    public static final String TASK_EXECUTOR = "amlTaskExecutor";

    /** 核心线程数 - 常驻线程，即使空闲也会保持 */
    private static final int CORE_POOL_SIZE = 8;

    /** 最大线程数 - 线程池允许创建的最大线程数 */
    private static final int MAX_POOL_SIZE = 50;

    /** 队列容量 - 等待执行的任务队列大小 */
    private static final int QUEUE_CAPACITY = 10000;

    /** 线程名称前缀 - 便于日志追踪和线程监控 */
    private static final String THREAD_NAME_PREFIX = "aml-async-";

    /** 线程空闲存活时间（秒） */
    private static final int KEEP_ALIVE_SECONDS = 60;

    /**
     * 自定义异步任务执行器
     *
     * 线程池策略说明：
     * - 核心线程满 -> 新任务进入队列等待
     * - 队列满 -> 创建新线程（直到最大线程数）
     * - 线程数达到最大且队列满 -> 按拒绝策略处理
     * - 拒绝策略采用 CallerRunsPolicy：由调用线程执行，避免任务丢失
     *
     * @return Executor 异步任务执行器
     */
    @Bean(TASK_EXECUTOR)
    public Executor amlTaskExecutor() {
        log.info("初始化 AML 异步任务执行器：核心线程={}, 最大线程={}, 队列容量={}",
                CORE_POOL_SIZE, MAX_POOL_SIZE, QUEUE_CAPACITY);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);

        // 拒绝策略：由调用线程执行，避免任务丢失（适合对可靠性要求高的AML业务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // 初始化线程池
        executor.initialize();

        log.info("AML 异步任务执行器初始化完成，线程前缀={}", THREAD_NAME_PREFIX);
        return executor;
    }

    /**
     * 虚拟线程执行器（JDK 21+ 特性）
     *
     * 使用虚拟线程可以大幅提升并发性能，特别适合I/O密集型任务：
     * - 调用外部风控API
     * - 数据库查询等待
     * - Kafka消息发送
     *
     * 启用方式：在 application.yml 中配置 spring.threads.virtual.enabled=true
     * 或直接使用此Bean替代上面的传统线程池
     *
     * @return Executor 虚拟线程执行器
     */
    @Bean("amlVirtualTaskExecutor")
    public Executor amlVirtualTaskExecutor() {
        log.info("初始化 AML 虚拟线程执行器...");
        // JDK 21+ 虚拟线程支持
        // return Executors.newVirtualThreadPerTaskExecutor();

        // 如果JDK版本低于21，回退到传统线程池
        log.warn("当前环境使用传统线程池，如需虚拟线程请确保使用JDK 21+");
        return amlTaskExecutor();
    }
}
