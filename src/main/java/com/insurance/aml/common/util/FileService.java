package com.insurance.aml.common.util;

import com.insurance.aml.common.config.MinioConfig;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务
 * 封装 MinIO 对象存储操作，提供文件上传、下载、删除、预签名URL等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 上传文件到 MinIO
     *
     * @param file      上传的文件
     * @param directory 目录前缀，如 "kyc/identities", "case/attachments"
     * @return 文件对象的 key（存储路径）
     */
    public String uploadFile(MultipartFile file, String directory) {
        try {
            ensureBucketExists();

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String objectKey = directory + "/" + UUID.randomUUID().toString().replace("-", "") + extension;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("文件上传成功: bucket={}, key={}, size={}", minioConfig.getBucketName(), objectKey, file.getSize());
            return objectKey;
        } catch (Exception e) {
            log.error("文件上传失败: originalName={}", file.getOriginalFilename(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传文件流
     *
     * @param inputStream 文件输入流
     * @param objectKey   对象key
     * @param contentType 内容类型
     * @param size        文件大小
     * @return 文件对象的 key
     */
    public String uploadStream(InputStream inputStream, String objectKey, String contentType, long size) {
        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());

            log.info("文件流上传成功: key={}", objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("文件流上传失败: key={}", objectKey, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 下载文件
     *
     * @param objectKey 对象key
     * @return 文件输入流
     */
    public InputStream downloadFile(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("文件下载失败: key={}", objectKey, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     *
     * @param objectKey 对象key
     */
    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
            log.info("文件删除成功: key={}", objectKey);
        } catch (Exception e) {
            log.error("文件删除失败: key={}", objectKey, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件的预签名访问URL
     *
     * @param objectKey   对象key
     * @param expiryHours URL有效期（小时）
     * @return 预签名URL
     */
    public String getPresignedUrl(String objectKey, int expiryHours) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .expiry(expiryHours, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("获取预签名URL失败: key={}", objectKey, e);
            throw new RuntimeException("获取预签名URL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param objectKey 对象key
     * @return 是否存在
     */
    public boolean fileExists(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 确保存储桶存在，不存在则创建
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build());
                log.info("存储桶创建成功: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.error("检查/创建存储桶失败: {}", minioConfig.getBucketName(), e);
            throw new RuntimeException("存储桶操作失败: " + e.getMessage(), e);
        }
    }
}
