package com.insurance.aml.module.regulation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.module.regulation.mapper.RegulationCategoryMapper;
import com.insurance.aml.module.regulation.mapper.RegulationDocumentMapper;
import com.insurance.aml.module.regulation.model.dto.RegulationCategoryRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationDocumentQueryRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationDocumentRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationOverviewVO;
import com.insurance.aml.module.regulation.model.entity.RegulationCategory;
import com.insurance.aml.module.regulation.model.entity.RegulationDocument;
import com.insurance.aml.module.regulation.service.RegulationLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 法规及资料库服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegulationLibraryServiceImpl implements RegulationLibraryService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String DOC_TYPE_REGULATORY_UPDATE = "REGULATORY_UPDATE";
    private static final String DOC_TYPE_INDUSTRY_UPDATE = "INDUSTRY_UPDATE";

    private final RegulationCategoryMapper categoryMapper;
    private final RegulationDocumentMapper documentMapper;

    @Override
    public RegulationOverviewVO overview() {
        List<RegulationDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<>());
        long categories = categoryMapper.selectCount(new LambdaQueryWrapper<RegulationCategory>()
                .eq(RegulationCategory::getStatus, STATUS_ENABLED));

        return RegulationOverviewVO.builder()
                .totalDocuments(documents.size())
                .regulationDocuments(countType(documents, "REGULATION"))
                .policyDocuments(countType(documents, "POLICY"))
                .trainingDocuments(countType(documents, "TRAINING"))
                .regulatoryUpdates(countType(documents, DOC_TYPE_REGULATORY_UPDATE))
                .industryUpdates(countType(documents, DOC_TYPE_INDUSTRY_UPDATE))
                .publishedDocuments(documents.stream().filter(doc -> STATUS_PUBLISHED.equals(doc.getStatus())).count())
                .importantDocuments(documents.stream().filter(doc -> Boolean.TRUE.equals(doc.getImportantFlag())).count())
                .categories(categories)
                .build();
    }

    @Override
    public List<RegulationCategory> listCategories(String status) {
        LambdaQueryWrapper<RegulationCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), RegulationCategory::getStatus, status)
                .orderByAsc(RegulationCategory::getSortOrder)
                .orderByAsc(RegulationCategory::getCreatedTime);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationCategory createCategory(RegulationCategoryRequest request) {
        ensureCategoryCodeUnique(request.getCategoryCode(), null);
        RegulationCategory category = new RegulationCategory();
        applyCategory(category, request);
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationCategory updateCategory(Long id, RegulationCategoryRequest request) {
        RegulationCategory category = loadCategory(id);
        ensureCategoryCodeUnique(request.getCategoryCode(), id);
        applyCategory(category, request);
        categoryMapper.updateById(category);
        syncCategoryName(category);
        return category;
    }

    @Override
    public PageResult<RegulationDocument> pageDocuments(RegulationDocumentQueryRequest request) {
        LambdaQueryWrapper<RegulationDocument> wrapper = buildDocumentWrapper(request);
        IPage<RegulationDocument> page = documentMapper.selectPage(request.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    public PageResult<RegulationDocument> pageUpdates(RegulationDocumentQueryRequest request) {
        LambdaQueryWrapper<RegulationDocument> wrapper = buildDocumentWrapper(request)
                .in(RegulationDocument::getDocType, DOC_TYPE_REGULATORY_UPDATE, DOC_TYPE_INDUSTRY_UPDATE);
        IPage<RegulationDocument> page = documentMapper.selectPage(request.toPage(), wrapper);
        return PageResult.from(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument getDocument(Long id) {
        RegulationDocument document = loadDocument(id);
        document.setViewCount((document.getViewCount() == null ? 0 : document.getViewCount()) + 1);
        documentMapper.updateById(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument createDocument(RegulationDocumentRequest request) {
        ensureDocCodeUnique(request.getDocCode(), null);
        RegulationDocument document = new RegulationDocument();
        applyDocument(document, request);
        documentMapper.insert(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument updateDocument(Long id, RegulationDocumentRequest request) {
        RegulationDocument document = loadDocument(id);
        ensureDocCodeUnique(request.getDocCode(), id);
        applyDocument(document, request);
        documentMapper.updateById(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument publishDocument(Long id) {
        RegulationDocument document = loadDocument(id);
        document.setStatus(STATUS_PUBLISHED);
        if (document.getPublishDate() == null) {
            document.setPublishDate(LocalDate.now());
        }
        documentMapper.updateById(document);
        return document;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument archiveDocument(Long id) {
        RegulationDocument document = loadDocument(id);
        document.setStatus(STATUS_ARCHIVED);
        documentMapper.updateById(document);
        return document;
    }

    private LambdaQueryWrapper<RegulationDocument> buildDocumentWrapper(RegulationDocumentQueryRequest request) {
        LambdaQueryWrapper<RegulationDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(request.getKeyword()), query -> query
                        .like(RegulationDocument::getTitle, request.getKeyword())
                        .or()
                        .like(RegulationDocument::getSummary, request.getKeyword())
                        .or()
                        .like(RegulationDocument::getContent, request.getKeyword())
                        .or()
                        .like(RegulationDocument::getTags, request.getKeyword())
                        .or()
                        .like(RegulationDocument::getSourceOrg, request.getKeyword()))
                .eq(StringUtils.hasText(request.getDocType()), RegulationDocument::getDocType, request.getDocType())
                .eq(request.getCategoryId() != null, RegulationDocument::getCategoryId, request.getCategoryId())
                .eq(StringUtils.hasText(request.getStatus()), RegulationDocument::getStatus, request.getStatus())
                .eq(StringUtils.hasText(request.getSourceType()), RegulationDocument::getSourceType, request.getSourceType())
                .eq(request.getImportantFlag() != null, RegulationDocument::getImportantFlag, request.getImportantFlag())
                .orderByDesc(RegulationDocument::getImportantFlag)
                .orderByDesc(RegulationDocument::getPublishDate)
                .orderByDesc(RegulationDocument::getUpdatedTime);
        return wrapper;
    }

    private void applyCategory(RegulationCategory category, RegulationCategoryRequest request) {
        BeanUtils.copyProperties(request, category);
        if (!StringUtils.hasText(category.getCategoryType())) {
            category.setCategoryType("GENERAL");
        }
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        if (!StringUtils.hasText(category.getStatus())) {
            category.setStatus(STATUS_ENABLED);
        }
    }

    private void applyDocument(RegulationDocument document, RegulationDocumentRequest request) {
        BeanUtils.copyProperties(request, document);
        RegulationCategory category = request.getCategoryId() == null ? null : loadCategory(request.getCategoryId());
        document.setCategoryName(category == null ? null : category.getCategoryName());
        if (!StringUtils.hasText(document.getSourceType())) {
            document.setSourceType("INTERNAL");
        }
        if (!StringUtils.hasText(document.getStatus())) {
            document.setStatus("DRAFT");
        }
        if (document.getImportantFlag() == null) {
            document.setImportantFlag(false);
        }
        if (document.getViewCount() == null) {
            document.setViewCount(0);
        }
    }

    private RegulationCategory loadCategory(Long id) {
        RegulationCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资料分类不存在，id=" + id);
        }
        return category;
    }

    private RegulationDocument loadDocument(Long id) {
        RegulationDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "法规资料不存在，id=" + id);
        }
        return document;
    }

    private void ensureCategoryCodeUnique(String categoryCode, Long excludeId) {
        LambdaQueryWrapper<RegulationCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegulationCategory::getCategoryCode, categoryCode);
        if (excludeId != null) {
            wrapper.ne(RegulationCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类编码已存在：" + categoryCode);
        }
    }

    private void ensureDocCodeUnique(String docCode, Long excludeId) {
        LambdaQueryWrapper<RegulationDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegulationDocument::getDocCode, docCode);
        if (excludeId != null) {
            wrapper.ne(RegulationDocument::getId, excludeId);
        }
        if (documentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "资料编码已存在：" + docCode);
        }
    }

    private void syncCategoryName(RegulationCategory category) {
        LambdaQueryWrapper<RegulationDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegulationDocument::getCategoryId, category.getId());
        List<RegulationDocument> documents = documentMapper.selectList(wrapper);
        for (RegulationDocument document : documents) {
            document.setCategoryName(category.getCategoryName());
            documentMapper.updateById(document);
        }
    }

    private long countType(List<RegulationDocument> documents, String docType) {
        return documents.stream().filter(doc -> docType.equals(doc.getDocType())).count();
    }
}
