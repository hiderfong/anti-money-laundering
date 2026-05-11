package com.insurance.aml.common.config;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Drools规则引擎配置
 * 从classpath:/rules/目录加载所有.drl规则文件，构建KieContainer
 * 通过KieContainer获取KieSession执行规则，保证线程安全
 */
@Slf4j
@Configuration
public class DroolsConfig {

    private static final String RULES_PATH = "rules/";
    private static final String DRL_PATTERN = "classpath:" + RULES_PATH + "*.drl";

    /**
     * 构建KieContainer，加载所有DRL规则文件
     * KieContainer是线程安全的，可作为单例使用
     *
     * @return KieContainer 规则容器
     */
    @Bean
    public KieContainer kieContainer() throws IOException {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        // 加载所有.drl规则文件
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources(DRL_PATTERN);

        if (resources.length == 0) {
            log.warn("未找到任何Drools规则文件, 路径: {}", DRL_PATTERN);
        }

        for (Resource resource : resources) {
            String filePath = RULES_PATH + resource.getFilename();
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            kieFileSystem.write(filePath, content);
            log.info("加载Drools规则文件: {}", filePath);
        }

        // 编译规则
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem).buildAll();
        Results results = kieBuilder.getResults();

        if (results.hasMessages(Message.Level.ERROR)) {
            String errors = results.getMessages().stream()
                    .filter(m -> m.getLevel() == Message.Level.ERROR)
                    .map(Message::getText)
                    .reduce("", (a, b) -> a + "\n" + b);
            log.error("Drools规则编译失败: {}", errors);
            throw new IllegalStateException("Drools规则编译失败:" + errors);
        }

        if (results.hasMessages(Message.Level.WARNING)) {
            results.getMessages().stream()
                    .filter(m -> m.getLevel() == Message.Level.WARNING)
                    .forEach(m -> log.warn("Drools规则编译警告: {}", m.getText()));
        }

        KieModule kieModule = kieBuilder.getKieModule();
        KieContainer kieContainer = kieServices.newKieContainer(kieModule.getReleaseId());

        log.info("Drools规则引擎初始化完成, 加载规则文件数: {}", resources.length);
        return kieContainer;
    }

    /**
     * 从KieContainer创建新的KieSession
     * 每次评估应使用独立的Session实例，保证线程安全
     *
     * @param kieContainer 规则容器
     * @return 新的KieSession实例，调用方需负责dispose
     */
    public static KieSession newKieSession(KieContainer kieContainer) {
        return kieContainer.newKieSession();
    }
}
