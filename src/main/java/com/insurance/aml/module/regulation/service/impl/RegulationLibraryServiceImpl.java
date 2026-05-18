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
 * 法规及资料库服务实现类
 * 管理法规分类、法规文档的全生命周期：创建、发布、归档、查询
 * 支持分类名称同步更新到关联文档
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegulationLibraryServiceImpl implements RegulationLibraryService {

    /** 启用状态 */
    private static final String STATUS_ENABLED = "ENABLED";
    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 已归档状态 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    /** 监管更新文档类型 */
    private static final String DOC_TYPE_REGULATORY_UPDATE = "REGULATORY_UPDATE";
    /** 行业更新文档类型 */
    private static final String DOC_TYPE_INDUSTRY_UPDATE = "INDUSTRY_UPDATE";

    private final RegulationCategoryMapper categoryMapper;
    private final RegulationDocumentMapper documentMapper;

    /**
     * 获取法规库概览统计
     * 统计文档总数、各类型数量、已发布数量、重要文档数量及分类数
     */
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

    /**
     * 查询法规分类列表
     * 支持按状态筛选，按排序号和创建时间升序排列
     */
    @Override
    public List<RegulationCategory> listCategories(String status) {
        LambdaQueryWrapper<RegulationCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), RegulationCategory::getStatus, status)
                .orderByAsc(RegulationCategory::getSortOrder)
                .orderByAsc(RegulationCategory::getCreatedTime);
        return categoryMapper.selectList(wrapper);
    }

    /**
     * 创建法规分类
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationCategory createCategory(RegulationCategoryRequest request) {
        ensureCategoryCodeUnique(request.getCategoryCode(), null);
        RegulationCategory category = new RegulationCategory();
        applyCategory(category, request);
        categoryMapper.insert(category);
        return category;
    }

    /**
     * 更新法规分类
     * 更新后同步分类名称到关联的所有文档
     */
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

    /**
     * 分页查询法规文档
     */
    @Override
    public PageResult<RegulationDocument> pageDocuments(RegulationDocumentQueryRequest request) {
        LambdaQueryWrapper<RegulationDocument> wrapper = buildDocumentWrapper(request);
        IPage<RegulationDocument> page = documentMapper.selectPage(request.toPage(), wrapper);
        return PageResult.from(page);
    }

    /**
     * 分页查询法规更新记录（监管更新和行业更新）
     */
    @Override
    public PageResult<RegulationDocument> pageUpdates(RegulationDocumentQueryRequest request) {
        LambdaQueryWrapper<RegulationDocument> wrapper = buildDocumentWrapper(request)
                .in(RegulationDocument::getDocType, DOC_TYPE_REGULATORY_UPDATE, DOC_TYPE_INDUSTRY_UPDATE);
        IPage<RegulationDocument> page = documentMapper.selectPage(request.toPage(), wrapper);
        return PageResult.from(page);
    }

    /**
     * 获取法规文档详情
     * 自动增加浏览次数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument getDocument(Long id) {
        RegulationDocument document = loadDocument(id);
        document.setViewCount((document.getViewCount() == null ? 0 : document.getViewCount()) + 1);
        documentMapper.updateById(document);
        return document;
    }

    /**
     * 创建法规文档
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument createDocument(RegulationDocumentRequest request) {
        ensureDocCodeUnique(request.getDocCode(), null);
        RegulationDocument document = new RegulationDocument();
        applyDocument(document, request);
        documentMapper.insert(document);
        return document;
    }

    /**
     * 更新法规文档
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument updateDocument(Long id, RegulationDocumentRequest request) {
        RegulationDocument document = loadDocument(id);
        ensureDocCodeUnique(request.getDocCode(), id);
        applyDocument(document, request);
        documentMapper.updateById(document);
        return document;
    }

    /**
     * 发布法规文档
     * 首次发布时自动设置发布日期
     */
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

    /**
     * 归档法规文档
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulationDocument archiveDocument(Long id) {
        RegulationDocument document = loadDocument(id);
        document.setStatus(STATUS_ARCHIVED);
        documentMapper.updateById(document);
        return document;
    }

    /**
     * 构建法规文档查询条件
     * 支持关键词全文检索、文档类型、分类、状态、来源类型、重要标记筛选
     */
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

    /**
     * 应用分类请求到实体
     */
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

    /**
     * 应用文档请求到实体
     */
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

    /**
     * 加载分类，不存在则抛出异常
     */
    private RegulationCategory loadCategory(Long id) {
        RegulationCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资料分类不存在，id=" + id);
        }
        return category;
    }

    /**
     * 加载文档，不存在则抛出异常
     */
    private RegulationDocument loadDocument(Long id) {
        RegulationDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "法规资料不存在，id=" + id);
        }
        return document;
    }

    /**
     * 确保分类编码唯一
     */
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

    /**
     * 确保文档编码唯一
     */
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

    /**
     * 同步分类名称到关联文档
     */
    private void syncCategoryName(RegulationCategory category) {
        LambdaQueryWrapper<RegulationDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RegulationDocument::getCategoryId, category.getId());
        List<RegulationDocument> documents = documentMapper.selectList(wrapper);
        for (RegulationDocument document : documents) {
            document.setCategoryName(category.getCategoryName());
            documentMapper.updateById(document);
        }
    }

    /**
     * 统计指定文档类型的数量
     */
    private long countType(List<RegulationDocument> documents, String docType) {
        return documents.stream().filter(doc -> docType.equals(doc.getDocType())).count();
    }
}
