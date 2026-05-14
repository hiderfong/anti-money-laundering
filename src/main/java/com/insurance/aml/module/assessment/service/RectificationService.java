package com.insurance.aml.module.assessment.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.assessment.model.dto.RectificationProgressRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationTaskRequest;
import com.insurance.aml.module.assessment.model.dto.RectificationVerifyRequest;
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
     * 分页查询整改中心任务。
     */
    PageResult<RectificationTask> pageTasks(PageQuery pageQuery, String sourceType, String status, String responsiblePerson);

    /**
     * 更新整改进度。
     */
    void updateProgress(Long taskId, RectificationProgressRequest req);

    /**
     * 验证整改任务完成情况
     * @param taskId 任务ID
     * @param verifiedBy 验证人
     */
    void verifyTask(Long taskId, String verifiedBy);

    /**
     * 验证整改任务并记录验证意见。
     */
    void verifyTask(Long taskId, RectificationVerifyRequest req);
}
