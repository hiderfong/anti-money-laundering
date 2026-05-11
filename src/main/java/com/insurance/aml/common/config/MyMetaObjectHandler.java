package com.insurance.aml.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * 自动填充创建人、创建时间、更新人、更新时间字段
 */
@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     * 设置 createdBy、createdTime、updatedBy、updatedTime
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("自动填充 - 插入操作");
        String username = getCurrentUsername();
        this.strictInsertFill(metaObject, "createdBy", String.class, username);
        this.strictInsertFill(metaObject, "createdTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updatedBy", String.class, username);
        this.strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 更新时自动填充
     * 设置 updatedBy、updatedTime
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("自动填充 - 更新操作");
        String username = getCurrentUsername();
        this.strictUpdateFill(metaObject, "updatedBy", String.class, username);
        this.strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, LocalDateTime.now());
    }

    /**
     * 获取当前登录用户名
     * 从 SecurityUtils 获取，获取失败则返回 "system"
     */
    private String getCurrentUsername() {
        try {
            // 从 Spring Security 上下文中获取当前用户名
            // 如果未登录或获取失败，返回默认值
            String username = com.insurance.aml.common.util.SecurityUtils.getCurrentUsername();
            return username != null ? username : "system";
        } catch (Exception e) {
            log.warn("获取当前用户名失败，使用默认值 'system': {}", e.getMessage());
            return "system";
        }
    }
}
