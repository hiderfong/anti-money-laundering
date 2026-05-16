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
 * 反洗钱模型管理服务。
 */
public interface AmlModelService {

    ModelOverviewVO overview();

    PageResult<AmlModel> pageModels(ModelQueryRequest request);

    AmlModel getModel(Long id);

    AmlModel createModel(ModelCreateRequest request);

    AmlModel updateModel(Long id, ModelCreateRequest request);

    AmlModel testModel(Long id, ModelLifecycleRequest request);

    AmlModel deployModel(Long id, ModelLifecycleRequest request);

    AmlModel monitorModel(Long id, ModelLifecycleRequest request);

    AmlModel iterateModel(Long id, ModelLifecycleRequest request);

    AmlModel archiveModel(Long id, ModelLifecycleRequest request);

    PageResult<AmlModelLifecycleLog> pageLifecycleLogs(Long modelId, PageQuery pageQuery);
}
