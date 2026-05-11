package com.insurance.aml.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.system.mapper.SysAuditLogMapper;
import com.insurance.aml.module.system.model.dto.AuditLogQueryRequest;
import com.insurance.aml.module.system.model.dto.AuditLogVO;
import com.insurance.aml.module.system.model.entity.SysAuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.system.service.AuditLogQueryService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志查询服务实现
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class AuditLogQueryServiceImpl implements AuditLogQueryService {
    private final SysAuditLogMapper sysAuditLogMapper;

    /**
     * 分页查询审计日志：支持按用户ID、用户名、操作类型、模块、时间范围筛选
     */
    @Override
    public PageResult<AuditLogVO> pageQueryLogs(AuditLogQueryRequest req) {
        LambdaQueryWrapper<SysAuditLog> wrapper = buildQueryWrapper(req);
        wrapper.orderByDesc(SysAuditLog::getCreatedTime);

        IPage<SysAuditLog> page = sysAuditLogMapper.selectPage(req.toPage(), wrapper);

        List<AuditLogVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult result = new PageResult();
        result.setTotal(page.getTotal());
        result.setPage((int) page.getCurrent());
        result.setSize((int) page.getSize());
        result.setList(voList);
        return result;
    }

    /**
     * 获取审计日志详情
     */
    @Override
    public AuditLogVO getLogDetail(Long id) {
        SysAuditLog log = sysAuditLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return convertToVO(log);
    }

    /**
     * 导出审计日志为CSV文件
     */
    @Override
    public byte[] exportLogs(AuditLogQueryRequest req) {
        LambdaQueryWrapper<SysAuditLog> wrapper = buildQueryWrapper(req);
        wrapper.orderByDesc(SysAuditLog::getCreatedTime);

        // 导出时不限制条数上限为10000条
        wrapper.last("LIMIT 10000");
        List<SysAuditLog> logs = sysAuditLogMapper.selectList(wrapper);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 写入BOM头（兼容Excel中文显示）
        baos.write(0xEF);
        baos.write(0xBB);
        baos.write(0xBF);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.println("ID,追踪ID,用户ID,用户名,操作类型,模块,目标类型,目标ID,详情,IP地址," +
                    "请求URI,请求方法,响应码,耗时(ms),错误信息,创建时间");

            for (SysAuditLog auditLog : logs) {
                writer.println(String.format("%d,%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d,%s,%s,%s",
                        auditLog.getId(),
                        escapeCsv(auditLog.getTraceId()),
                        auditLog.getUserId(),
                        escapeCsv(auditLog.getUsername()),
                        escapeCsv(auditLog.getOperationType()),
                        escapeCsv(auditLog.getModule()),
                        escapeCsv(auditLog.getTargetType()),
                        escapeCsv(auditLog.getTargetId()),
                        escapeCsv(auditLog.getDetail()),
                        escapeCsv(auditLog.getIpAddress()),
                        escapeCsv(auditLog.getRequestUri()),
                        escapeCsv(auditLog.getRequestMethod()),
                        auditLog.getResponseCode(),
                        auditLog.getDurationMs() != null ? auditLog.getDurationMs().toString() : "",
                        escapeCsv(auditLog.getErrorMessage()),
                        auditLog.getCreatedTime() != null ? auditLog.getCreatedTime().toString() : ""));
            }
            writer.flush();
        } catch (Exception e) {
            log.error("导出审计日志CSV失败", e);
            throw new BusinessException("导出审计日志失败");
        }

        log.info("导出审计日志CSV成功，共{}条记录", logs.size());
        return baos.toByteArray();
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<SysAuditLog> buildQueryWrapper(AuditLogQueryRequest req) {
        LambdaQueryWrapper<SysAuditLog> wrapper = new LambdaQueryWrapper<>();

        if (req.getUserId() != null) {
            wrapper.eq(SysAuditLog::getUserId, req.getUserId());
        }
        if (StringUtils.hasText(req.getUsername())) {
            wrapper.like(SysAuditLog::getUsername, req.getUsername());
        }
        if (StringUtils.hasText(req.getOperationType())) {
            wrapper.eq(SysAuditLog::getOperationType, req.getOperationType());
        }
        if (StringUtils.hasText(req.getModule())) {
            wrapper.eq(SysAuditLog::getModule, req.getModule());
        }
        if (req.getStartTime() != null) {
            wrapper.ge(SysAuditLog::getCreatedTime, req.getStartTime());
        }
        if (req.getEndTime() != null) {
            wrapper.le(SysAuditLog::getCreatedTime, req.getEndTime());
        }

        return wrapper;
    }

    /**
     * CSV特殊字符转义
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // 如果包含逗号、双引号或换行符，则用双引号包裹并转义内部双引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 实体转VO
     */
    private AuditLogVO convertToVO(SysAuditLog entity) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
