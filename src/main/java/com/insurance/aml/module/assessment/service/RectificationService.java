package com.insurance.aml.module.assessment.service;

import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.entity.RectificationTask;

import java.util.List;

/**
 * 整改任务服务接口
 */
public interface RectificationService {

    /**
     * 创建整改任务
     * @param req 创建请求
     * @return 整改任务
     */
    RectificationTask createTask(RectificationTaskRequest req);

    /**
     * 更新整改任务状态
     * @param taskId 任务ID
     * @param status 新状态
     */
    void updateTaskStatus(Long taskId, String status);

    /**
     * 查询整改任务列表（支持逾期检测）
     * @param assessmentId 评估ID（可为null查全部）
     * @return 任务列表
     */
    List<RectificationTask> listTasks(Long assessmentId);

    /**
     * 验证整改任务完成情况
     * @param taskId 任务ID
     * @param verifiedBy 验证人
     */
    void verifyTask(Long taskId, String verifiedBy);
}
