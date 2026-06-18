package com.insurance.aml.module.product.controller;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.Result;
import com.insurance.aml.module.product.model.dto.*;
import com.insurance.aml.module.product.model.entity.Product;
import com.insurance.aml.module.product.model.entity.ProductRiskAssessment;
import com.insurance.aml.module.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品管理控制器
 * 提供产品CRUD、产品风险评估等接口
 */
@RestController
@RequestMapping("/products")
@Tag(name = "产品管理")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 创建产品
     */
    @PostMapping
    @Operation(summary = "创建产品", description = "创建新的保险产品")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('product:manage')")
    public Result<Product> createProduct(@Valid @RequestBody ProductCreateRequest req) {
        log.info("创建产品请求，productCode={}", req.getProductCode());
        Product product = productService.createProduct(req);
        return Result.success(product);
    }

    /**
     * 更新产品信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新产品", description = "更新指定产品的信息")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('product:manage')")
    public Result<Product> updateProduct(
            @Parameter(description = "产品ID") @PathVariable Long id,
            @Valid @RequestBody ProductCreateRequest req) {
        log.info("更新产品请求，id={}", id);
        Product product = productService.updateProduct(id, req);
        return Result.success(product);
    }

    /**
     * 获取产品详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取产品详情", description = "根据ID获取产品详细信息")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('product:view')")
    public Result<ProductVO> getProductDetail(
            @Parameter(description = "产品ID") @PathVariable Long id) {
        ProductVO vo = productService.getProductDetail(id);
        return Result.success(vo);
    }

    /**
     * 分页查询产品列表
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询产品", description = "按条件分页查询产品列表")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('product:view')")
    public Result<PageResult<ProductVO>> pageQueryProducts(ProductQueryRequest req) {
        PageResult<ProductVO> result = productService.pageQueryProducts(req);
        return Result.success(result);
    }

    /**
     * 产品风险评估
     */
    @PostMapping("/{id}/assess")
    @Operation(summary = "产品风险评估", description = "对指定产品进行风险评估打分")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('product:manage')")
    public Result<ProductRiskAssessment> assessProductRisk(
            @Parameter(description = "产品ID") @PathVariable Long id,
            @Valid @RequestBody ProductRiskAssessRequest req) {
        req.setProductId(id);
        log.info("产品风险评估请求，productId={}", id);
        ProductRiskAssessment assessment = productService.assessProductRisk(req);
        return Result.success(assessment);
    }

    /**
     * 获取产品评估历史
     */
    @GetMapping("/{id}/assessments")
    @Operation(summary = "查询评估历史", description = "获取指定产品的历史评估记录")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('product:view')")
    public Result<List<ProductRiskAssessment>> getAssessmentHistory(
            @Parameter(description = "产品ID") @PathVariable Long id) {
        List<ProductRiskAssessment> list = productService.getAssessmentHistory(id);
        return Result.success(list);
    }
}
