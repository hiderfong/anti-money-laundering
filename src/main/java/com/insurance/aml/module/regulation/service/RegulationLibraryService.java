package com.insurance.aml.module.regulation.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.regulation.model.dto.RegulationCategoryRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationDocumentQueryRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationDocumentRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationOverviewVO;
import com.insurance.aml.module.regulation.model.entity.RegulationCategory;
import com.insurance.aml.module.regulation.model.entity.RegulationDocument;

import java.util.List;

/**
 * 法规及资料库服务接口
 * 管理反洗钱法规分类、法规文档的创建、发布、归档及分页查询
 */
public interface RegulationLibraryService {

    /**
     * 获取法规库概览统计
     */
    RegulationOverviewVO overview();

    /**
     * 查询法规分类列表
     */
    List<RegulationCategory> listCategories(String status);

    /**
     * 创建法规分类
     */
    RegulationCategory createCategory(RegulationCategoryRequest request);

    /**
     * 更新法规分类
     */
    RegulationCategory updateCategory(Long id, RegulationCategoryRequest request);

    /**
     * 分页查询法规文档
     */
    PageResult<RegulationDocument> pageDocuments(RegulationDocumentQueryRequest request);

    /**
     * 分页查询法规更新记录
     */
    PageResult<RegulationDocument> pageUpdates(RegulationDocumentQueryRequest request);

    /**
     * 根据ID获取法规文档详情
     */
    RegulationDocument getDocument(Long id);

    /**
     * 创建法规文档
     */
    RegulationDocument createDocument(RegulationDocumentRequest request);

    /**
     * 更新法规文档
     */
    RegulationDocument updateDocument(Long id, RegulationDocumentRequest request);

    /**
     * 发布法规文档
     */
    RegulationDocument publishDocument(Long id);

    /**
     * 归档法规文档
     */
    RegulationDocument archiveDocument(Long id);
}
