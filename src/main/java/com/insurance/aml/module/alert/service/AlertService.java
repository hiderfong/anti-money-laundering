package com.insurance.aml.module.alert.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.alert.controller.AlertController;
import com.insurance.aml.module.alert.model.dto.*;
import com.insurance.aml.module.alert.model.entity.Alert;
import com.insurance.aml.module.alert.model.entity.AlertRuleDetail;

import java.util.List;

/**
 * 预警管理服务接口
 */
public interface AlertService {

    /**
     * 创建预警
     * @param alert 预警信息
     * @param ruleDetails 命中规则明细
     * @return 创建的预警
     */
    Alert createAlert(Alert alert, List<AlertRuleDetail> ruleDetails);

    /**
     * 分页查询预警
     * @param req 查询条件
     * @return 分页结果
     */
    PageResult<AlertVO> pageQueryAlerts(AlertQueryRequest req);

    /**
     * 获取预警详情
     * @param id 预警ID
     * @return 预警详情
     */
    AlertVO getAlertDetail(Long id);

    /**
     * 分配预警
     * @param req 分配请求
     */
    void assignAlert(AlertAssignRequest req);

    /**
     * 处理预警
     * @param req 处理请求
     */
    void processAlert(AlertProcessRequest req);

    /**
     * 批量处理预警
     * @param alertIds 预警ID列表
     * @param action 处理动作（CONFIRMED_SUSPICIOUS/EXCLUDED/ESCALATED）
     */
    void batchProcess(List<Long> alertIds, String action);

    /**
     * 自动分配预警（分配给空闲的AML专员）
     */
    void autoAssignAlerts();

    /**
     * 升级超期预警（超过48小时未处理的预警自动升级）
     */
    void escalateOverdueAlerts();

    /**
     * 获取预警统计信息
     * @return 按状态、风险等级的统计数据
     */
    AlertController.AlertStatisticsVO getAlertStatistics();
}
