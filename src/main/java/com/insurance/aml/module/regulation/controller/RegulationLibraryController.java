package com.insurance.aml.module.regulation.controller;

import com.insurance.aml.common.annotation.AuditLog;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.regulation.model.dto.RegulationCategoryRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationDocumentQueryRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationDocumentRequest;
import com.insurance.aml.module.regulation.model.dto.RegulationOverviewVO;
import com.insurance.aml.module.regulation.model.entity.RegulationCategory;
import com.insurance.aml.module.regulation.model.entity.RegulationDocument;
import com.insurance.aml.module.regulation.service.RegulationLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 法规及资料库管理。
 */
@RestController
@RequestMapping("/regulation-library")
@Tag(name = "法规及资料库")
@Slf4j
@RequiredArgsConstructor
public class RegulationLibraryController {

    private final RegulationLibraryService regulationLibraryService;

    @GetMapping("/overview")
    @Operation(summary = "法规资料库概览")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:view')")
    public Result<RegulationOverviewVO> overview() {
        return Result.success(regulationLibraryService.overview());
    }

    @GetMapping("/categories")
    @Operation(summary = "分类列表")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:view')")
    public Result<List<RegulationCategory>> listCategories(@RequestParam(required = false) String status) {
        return Result.success(regulationLibraryService.listCategories(status));
    }

    @PostMapping("/categories")
    @Operation(summary = "新增分类")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:manage')")
    @AuditLog(module = "法规资料库", operationType = "CREATE_CATEGORY", description = "新增法规资料分类")
    public Result<RegulationCategory> createCategory(@Valid @RequestBody RegulationCategoryRequest request) {
        log.info("新增法规资料分类，categoryCode={}", request.getCategoryCode());
        return Result.success(regulationLibraryService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "更新分类")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:manage')")
    @AuditLog(module = "法规资料库", operationType = "UPDATE_CATEGORY", description = "更新法规资料分类")
    public Result<RegulationCategory> updateCategory(
            @Parameter(description = "分类ID") @PathVariable Long id,
            @Valid @RequestBody RegulationCategoryRequest request) {
        return Result.success(regulationLibraryService.updateCategory(id, request));
    }

    @GetMapping("/documents/page")
    @Operation(summary = "法规资料全文检索")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:view')")
    public Result<PageResult<RegulationDocument>> pageDocuments(@Valid RegulationDocumentQueryRequest request) {
        return Result.success(regulationLibraryService.pageDocuments(request));
    }

    @GetMapping("/updates/page")
    @Operation(summary = "监管及行业动态查询")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:view')")
    public Result<PageResult<RegulationDocument>> pageUpdates(@Valid RegulationDocumentQueryRequest request) {
        return Result.success(regulationLibraryService.pageUpdates(request));
    }

    @GetMapping("/documents/{id}")
    @Operation(summary = "资料详情")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:view')")
    public Result<RegulationDocument> getDocument(@PathVariable Long id) {
        return Result.success(regulationLibraryService.getDocument(id));
    }

    @PostMapping("/documents")
    @Operation(summary = "新增资料")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:manage')")
    @AuditLog(module = "法规资料库", operationType = "CREATE_DOCUMENT", description = "新增法规资料")
    public Result<RegulationDocument> createDocument(@Valid @RequestBody RegulationDocumentRequest request) {
        log.info("新增法规资料，docCode={}", request.getDocCode());
        return Result.success(regulationLibraryService.createDocument(request));
    }

    @PutMapping("/documents/{id}")
    @Operation(summary = "更新资料")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:manage')")
    @AuditLog(module = "法规资料库", operationType = "UPDATE_DOCUMENT", description = "更新法规资料")
    public Result<RegulationDocument> updateDocument(
            @Parameter(description = "资料ID") @PathVariable Long id,
            @Valid @RequestBody RegulationDocumentRequest request) {
        return Result.success(regulationLibraryService.updateDocument(id, request));
    }

    @PostMapping("/documents/{id}/publish")
    @Operation(summary = "发布资料")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:manage')")
    @AuditLog(module = "法规资料库", operationType = "PUBLISH_DOCUMENT", description = "发布法规资料")
    public Result<RegulationDocument> publishDocument(@PathVariable Long id) {
        return Result.success(regulationLibraryService.publishDocument(id));
    }

    @PostMapping("/documents/{id}/archive")
    @Operation(summary = "归档资料")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('regulation:manage')")
    @AuditLog(module = "法规资料库", operationType = "ARCHIVE_DOCUMENT", description = "归档法规资料")
    public Result<RegulationDocument> archiveDocument(@PathVariable Long id) {
        return Result.success(regulationLibraryService.archiveDocument(id));
    }
}
