package com.insurance.aml.common.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置
 * 用于存储证件影像、案件附件、报告文件等
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aml.minio")
public class MinioConfig {

    /** MinIO 服务地址 */
    private String endpoint = "http://localhost:9000";

    /** 访问密钥 */
    private String accessKey = "CHANGE_ME_DEV_MINIO_ACCESS";

    /** 秘密密钥 */
    private String secretKey = "CHANGE_ME_DEV_MINIO_SECRET";

    /** 默认存储桶名称 */
    private String bucketName = "aml-files";

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
