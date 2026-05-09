package com.insurance.aml.module.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.enums.StatusEnum;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.service.impl.BaseServiceXImpl;
import com.insurance.aml.module.product.mapper.ProductMapper;
import com.insurance.aml.module.product.mapper.ProductRiskAssessmentMapper;
import com.insurance.aml.module.product.model.dto.*;
import com.insurance.aml.module.product.model.entity.Product;
import com.insurance.aml.module.product.model.entity.ProductRiskAssessment;
import com.insurance.aml.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 产品管理服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductServiceImpl extends BaseServiceXImpl<ProductMapper, Product> implements ProductService {

    private final ProductMapper productMapper;
    private final ProductRiskAssessmentMapper assessmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product createProduct(ProductCreateRequest req) {
        log.info("创建产品，productCode={}", req.getProductCode());

        // 校验产品编码唯一性
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductCode, req.getProductCode());
        if (productMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("产品编码已存在：" + req.getProductCode());
        }

        Product product = new Product();
        BeanUtils.copyProperties(req, product);
        product.setRiskLevel(RiskLevel.LOW.getCode());
        product.setRiskScore(0);
        product.setStatus(StatusEnum.ACTIVE.getCode());
        productMapper.insert(product);
        log.info("产品创建成功，id={}", product.getId());
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product updateProduct(Long id, ProductCreateRequest req) {
        log.info("更新产品，id={}", id);
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new RuntimeException("产品不存在，id=" + id);
        }
        BeanUtils.copyProperties(req, product);
        product.setId(id);
        productMapper.updateById(product);
        log.info("产品更新成功，id={}", id);
        return product;
    }

    @Override
    public PageResult<ProductVO> pageQueryProducts(ProductQueryRequest req) {
        log.info("分页查询产品，productName={}, productType={}, riskLevel={}, status={}",
                req.getProductName(), req.getProductType(), req.getRiskLevel(), req.getStatus());

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(req.getProductName()), Product::getProductName, req.getProductName())
                .eq(StringUtils.hasText(req.getProductType()), Product::getProductType, req.getProductType())
                .eq(StringUtils.hasText(req.getRiskLevel()), Product::getRiskLevel, req.getRiskLevel())
                .eq(StringUtils.hasText(req.getStatus()), Product::getStatus, req.getStatus())
                .orderByDesc(Product::getCreatedTime);

        Page<Product> pageParam = new Page<>(req.getPage(), req.getSize());
        Page<Product> page = productMapper.selectPage(pageParam, wrapper);
        // 将实体转换为VO
        Page<ProductVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(p -> {
            ProductVO vo = new ProductVO();
            BeanUtils.copyProperties(p, vo);
            return vo;
        }).toList());
        return PageResult.from(voPage);
    }

    @Override
    public ProductVO getProductDetail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new RuntimeException("产品不存在，id=" + id);
        }
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductRiskAssessment assessProductRisk(ProductRiskAssessRequest req) {
        log.info("产品风险评估，productId={}, assessor={}", req.getProductId(), req.getAssessor());

        Product product = productMapper.selectById(req.getProductId());
        if (product == null) {
            throw new RuntimeException("产品不存在，id=" + req.getProductId());
        }

        // 计算加权总评分：客户群体*20% + 缴费方式*20% + 产品结构*20% + 退保风险*15% + 受益人风险*15% + 销售渠道*10%
        int totalScore = (req.getClientGroupScore() * 20
                + req.getPaymentModeScore() * 20
                + req.getProductStructureScore() * 20
                + req.getSurrenderScore() * 15
                + req.getBeneficiaryScore() * 15
                + req.getChannelScore() * 10) / 100;

        // 确定风险等级：>60=HIGH, 30-60=MEDIUM, <30=LOW
        String riskLevel;
        if (totalScore > 60) {
            riskLevel = RiskLevel.HIGH.getCode();
        } else if (totalScore >= 30) {
            riskLevel = RiskLevel.MEDIUM.getCode();
        } else {
            riskLevel = RiskLevel.LOW.getCode();
        }

        // 创建评估记录
        ProductRiskAssessment assessment = new ProductRiskAssessment();
        assessment.setProductId(req.getProductId());
        assessment.setAssessmentDate(LocalDate.now());
        assessment.setAssessor(req.getAssessor());
        assessment.setClientGroupScore(req.getClientGroupScore());
        assessment.setPaymentModeScore(req.getPaymentModeScore());
        assessment.setProductStructureScore(req.getProductStructureScore());
        assessment.setSurrenderScore(req.getSurrenderScore());
        assessment.setBeneficiaryScore(req.getBeneficiaryScore());
        assessment.setChannelScore(req.getChannelScore());
        assessment.setTotalScore(totalScore);
        assessment.setRiskLevel(riskLevel);
        assessment.setStatus(ReportStatus.DRAFT.getCode());
        assessmentMapper.insert(assessment);

        // 同步更新产品的风险等级和评分
        product.setRiskLevel(riskLevel);
        product.setRiskScore(totalScore);
        productMapper.updateById(product);

        log.info("产品风险评估完成，productId={}, totalScore={}, riskLevel={}", req.getProductId(), totalScore, riskLevel);
        return assessment;
    }

    @Override
    public List<ProductRiskAssessment> getAssessmentHistory(Long productId) {
        log.info("查询产品评估历史，productId={}", productId);
        LambdaQueryWrapper<ProductRiskAssessment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductRiskAssessment::getProductId, productId)
                .orderByDesc(ProductRiskAssessment::getAssessmentDate);
        return assessmentMapper.selectList(wrapper);
    }
}
