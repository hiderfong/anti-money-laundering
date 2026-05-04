package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 异步任务配置类
 *
 * 功能说明：
 * 1. 启用Spring异步任务支持（@EnableAsync）
 * 2. 当 spring.threads.virtual.enabled=true 时自动使用虚拟线程
 * 3. 提供显式的虚拟线程执行器 amlVirtualTaskExecutor
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

    /**
     * 主异步任务执行器 - 使用虚拟线程
     *
     * 当 spring.threads.virtual.enabled=true 时，Spring Boot 3.4+ 会自动
     * 将 Tomcat 线程池替换为虚拟线程。此处显式创建虚拟线程执行器，
     * 同时注册为 applicationTaskExecutor 以覆盖Spring默认执行器。
     */
    @Bean(name = {TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME, TASK_EXECUTOR})
    public Executor amlTaskExecutor() {
        log.info("初始化 AML 异步任务执行器 (Virtual Threads=true)");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 显式的虚拟线程执行器，供需要明确指定的任务使用
     */
    @Bean("amlVirtualTaskExecutor")
    public Executor amlVirtualTaskExecutor() {
        log.info("初始化 AML 虚拟线程执行器...");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
