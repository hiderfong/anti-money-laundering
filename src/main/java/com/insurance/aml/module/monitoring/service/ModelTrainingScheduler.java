package com.insurance.aml.module.monitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ML模型定时训练任务
 *
 * 每天凌晨2点重新训练Isolation Forest模型，使用近90天的历史交易数据。
 * 训练完成后模型自动持久化到磁盘，下次交易评估将使用新模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelTrainingScheduler {

    private final TransactionAnomalyDetector anomalyDetector;

    /**
     * 每天凌晨2点执行模型重训练
     *
     * cron表达式: 秒 分 时 日 月 周
     * 0 0 2 * * ? = 每天凌晨2:00
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void retrainModel() {
        log.info("[ML-Scheduler] 开始定时重训练Isolation Forest模型...");

        long startTime = System.currentTimeMillis();

        try {
            int sampleCount = anomalyDetector.train();

            long duration = System.currentTimeMillis() - startTime;

            if (sampleCount > 0) {
                log.info("[ML-Scheduler] 模型重训练成功完成: 训练样本={}, 耗时={}ms",
                        sampleCount, duration);
            } else {
                log.warn("[ML-Scheduler] 模型重训练未执行(样本不足或无数据), 耗时={}ms", duration);
            }

            // 输出当前模型状态
            log.info("[ML-Scheduler] 模型状态: {}", anomalyDetector.getModelStatus());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ML-Scheduler] 模型重训练异常: error={}, 耗时={}ms", e.getMessage(), duration, e);
        }
    }
}
