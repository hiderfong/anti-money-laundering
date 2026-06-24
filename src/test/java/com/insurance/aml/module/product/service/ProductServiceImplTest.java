package com.insurance.aml.module.product.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.product.mapper.ProductMapper;
import com.insurance.aml.module.product.mapper.ProductRiskAssessmentMapper;
import com.insurance.aml.module.product.model.dto.ProductQueryRequest;
import com.insurance.aml.module.product.model.dto.ProductVO;
import com.insurance.aml.module.product.model.entity.Product;
import com.insurance.aml.module.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("产品服务测试")
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductRiskAssessmentMapper assessmentMapper;

    @InjectMocks
    private ProductServiceImpl service;

    @Test
    @DisplayName("分页查询：兼容旧数据 paymentMode 为空")
    void pageQueryProducts_nullPaymentMode_usesFallbackDescription() {
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("PROD001");
        product.setProductName("定期寿险A款");
        product.setProductType("LIFE");
        product.setPaymentMode(null);
        product.setHasCashValue(false);
        product.setHasInvestmentFeature(false);
        product.setBeneficiaryChangeable(true);
        product.setRiskLevel("LOW");
        product.setRiskScore(15);
        product.setStatus("ACTIVE");

        Page<Product> page = new Page<>(1, 5, 1);
        page.setRecords(List.of(product));
        when(productMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        ProductQueryRequest request = new ProductQueryRequest();
        request.setPage(1);
        request.setSize(5);

        PageResult<ProductVO> result = service.pageQueryProducts(request);

        assertEquals(1, result.getTotal());
        ProductVO vo = result.getList().getFirst();
        assertEquals("定期寿险A款", vo.getProductName());
        assertNotNull(vo.getDescription());
        assertTrue(vo.getDescription().contains("缴费方式未配置"));
    }

    @Test
    @DisplayName("分页查询：兼容旧数据 productType 为空")
    void pageQueryProducts_nullProductType_usesFallbackDescription() {
        Product product = new Product();
        product.setId(2L);
        product.setProductCode("LEGACY_NULL_TYPE");
        product.setProductName("历史迁移产品");
        product.setProductType(null);
        product.setPaymentMode("PERIODIC");
        product.setHasCashValue(true);
        product.setHasInvestmentFeature(false);
        product.setBeneficiaryChangeable(false);
        product.setRiskLevel("MEDIUM");
        product.setRiskScore(45);
        product.setStatus("ACTIVE");

        Page<Product> page = new Page<>(1, 5, 1);
        page.setRecords(List.of(product));
        when(productMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        ProductQueryRequest request = new ProductQueryRequest();
        request.setPage(1);
        request.setSize(5);

        PageResult<ProductVO> result = service.pageQueryProducts(request);

        assertEquals(1, result.getTotal());
        ProductVO vo = result.getList().getFirst();
        assertEquals("历史迁移产品", vo.getProductName());
        assertNotNull(vo.getDescription());
        assertTrue(vo.getDescription().startsWith("保险产品"));
    }
}
