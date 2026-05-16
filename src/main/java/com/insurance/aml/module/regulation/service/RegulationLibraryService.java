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
 * 法规及资料库服务。
 */
public interface RegulationLibraryService {

    RegulationOverviewVO overview();

    List<RegulationCategory> listCategories(String status);

    RegulationCategory createCategory(RegulationCategoryRequest request);

    RegulationCategory updateCategory(Long id, RegulationCategoryRequest request);

    PageResult<RegulationDocument> pageDocuments(RegulationDocumentQueryRequest request);

    PageResult<RegulationDocument> pageUpdates(RegulationDocumentQueryRequest request);

    RegulationDocument getDocument(Long id);

    RegulationDocument createDocument(RegulationDocumentRequest request);

    RegulationDocument updateDocument(Long id, RegulationDocumentRequest request);

    RegulationDocument publishDocument(Long id);

    RegulationDocument archiveDocument(Long id);
}
