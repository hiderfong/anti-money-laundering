package com.insurance.aml.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 *
 * 功能说明：
 * 1. 分页插件配置 - 支持MySQL数据库物理分页
 * 2. 乐观锁插件 - 通过版本号机制防止并发更新冲突
 * 3. 多租户插件（预留） - 后续支持多租户数据隔离
 *
 * @author AML Team
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器配置
     * 注意：多租户插件需要添加在分页插件之前，否则会导致SQL解析异常
     *
     * @return MybatisPlusInterceptor
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        log.info("初始化 MyBatis-Plus 拦截器配置...");
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户插件（暂时注释，待后续接入租户数据源后启用）
        // TenantLineInnerInterceptor tenantInterceptor = new TenantLineInnerInterceptor(new TenantLineHandler() {
        //     @Override
        //     public Expression getTenantId() {
        //         // 从当前登录用户上下文中获取租户ID
        //         return new LongValue(SecurityUtils.getCurrentTenantId());
        //     }
        //
        //     @Override
        //     public String getTenantIdColumn() {
        //         return "tenant_id";
        //     }
        //
        //     @Override
        //     public boolean ignoreTable(String tableName) {
        //         // 忽略不需要租户过滤的表
        //         return "sys_config".equals(tableName) || "sys_tenant".equals(tableName);
        //     }
        // });
        // interceptor.addInnerInterceptor(tenantInterceptor);

        // 分页插件 - MySQL数据库物理分页
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置最大分页限制，防止恶意全表查询
        paginationInterceptor.setMaxLimit(500L);
        // 溢出总页数后是否进行处理
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);

        // 乐观锁插件 - 基于版本号的并发控制
        OptimisticLockerInnerInterceptor optimisticLockerInterceptor = new OptimisticLockerInnerInterceptor();
        interceptor.addInnerInterceptor(optimisticLockerInterceptor);

        log.info("MyBatis-Plus 拦截器配置完成：分页插件(MySQL) + 乐观锁插件");
        return interceptor;
    }
}
