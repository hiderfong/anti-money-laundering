package com.insurance.aml.module.modelmgmt.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.modelmgmt.model.dto.ModelCreateRequest;
import com.insurance.aml.module.modelmgmt.model.dto.ModelLifecycleRequest;
import com.insurance.aml.module.modelmgmt.model.dto.ModelOverviewVO;
import com.insurance.aml.module.modelmgmt.model.dto.ModelQueryRequest;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModel;
import com.insurance.aml.module.modelmgmt.model.entity.AmlModelLifecycleLog;

/**
 * 反洗钱模型管理服务接口
 * 覆盖模型全生命周期：创建、测试、部署、监控、迭代、归档
 */
public interface AmlModelService {

    /**
     * 获取模型管理概览统计
     */
    ModelOverviewVO overview();

    /**
     * 分页查询模型列表
     */
    PageResult<AmlModel> pageModels(ModelQueryRequest request);

    /**
     * 根据ID获取模型详情
     */
    AmlModel getModel(Long id);

    /**
     * 创建新模型
     */
    AmlModel createModel(ModelCreateRequest request);

    /**
     * 更新模型信息
     */
    AmlModel updateModel(Long id, ModelCreateRequest request);

    /**
     * 测试模型
     */
    AmlModel testModel(Long id, ModelLifecycleRequest request);

    /**
     * 部署模型到生产环境
     */
    AmlModel deployModel(Long id, ModelLifecycleRequest request);

    /**
     * 监控模型运行状态
     */
    AmlModel monitorModel(Long id, ModelLifecycleRequest request);

    /**
     * 迭代模型版本
     */
    AmlModel iterateModel(Long id, ModelLifecycleRequest request);

    /**
     * 归档模型
     */
    AmlModel archiveModel(Long id, ModelLifecycleRequest request);

    /**
     * 分页查询模型生命周期日志
     */
    PageResult<AmlModelLifecycleLog> pageLifecycleLogs(Long modelId, PageQuery pageQuery);
}
