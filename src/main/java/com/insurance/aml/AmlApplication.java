package com.insurance.aml;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ============================================================
 *   Anti-Money Laundering (AML) System - Spring Boot Application
 *   反洗钱监测系统
 * ============================================================
 *   Features:
 *   - Transaction monitoring and analysis
 *   - Sanctions screening
 *   - Suspicious activity reporting (SAR)
 *   - Risk assessment and scoring
 *   - Real-time alerting
 * ============================================================
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.insurance.aml.module.**.mapper")
public class AmlApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlApplication.class, args);
        System.out.println("=============================================");
        System.out.println("   AML System started successfully!");
        System.out.println("   反洗钱监测系统启动成功！");
        System.out.println("=============================================");
    }
}
