package com.insurance.aml.module.system.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.system.model.dto.AuditLogQueryRequest;
import com.insurance.aml.module.system.model.dto.AuditLogVO;

/**
 * 审计日志查询服务接口
 */
public interface AuditLogQueryService {

    /**
     * 分页查询审计日志
     *
     * @param req 查询请求
     * @return 分页结果
     */
    PageResult<AuditLogVO> pageQueryLogs(AuditLogQueryRequest req);

    /**
     * 获取审计日志详情
     *
     * @param id 日志ID
     * @return 日志视图
     */
    AuditLogVO getLogDetail(Long id);

    /**
     * 导出审计日志为CSV
     *
     * @param req 查询条件
     * @return CSV文件字节数组
     */
    byte[] exportLogs(AuditLogQueryRequest req);
}
