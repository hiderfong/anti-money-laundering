package com.insurance.aml.module.integration.task;

import com.insurance.aml.module.integration.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 扫描到期的动态集成任务，具体Cron由任务记录计算为 nextRunTime。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationJobScheduler {
    private final IntegrationService integrationService;

    @Value("${aml.integration.scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${aml.integration.scheduler.fixed-delay-ms:60000}")
    public void executeDueJobs() {
        if (!enabled) {
            return;
        }
        try {
            integrationService.runDueJobs();
        } catch (Exception exception) {
            log.error("集成任务调度扫描失败：{}", exception.getMessage(), exception);
        }
    }
}
