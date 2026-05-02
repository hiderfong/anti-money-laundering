package com.insurance.aml.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdGenerator 单元测试
 */
@ExtendWith(MockitoExtension.class)
class IdGeneratorTest {

    @InjectMocks
    private IdGenerator idGenerator;

    @Test
    @DisplayName("生成客户编号 - 非空、以CUS开头、长度大于10")
    void testGenerateCustomerNo() {
        String customerNo = idGenerator.generateCustomerNo();
        assertNotNull(customerNo, "客户编号不应为空");
        assertTrue(customerNo.startsWith("CUS"), "客户编号应以CUS开头");
        assertTrue(customerNo.length() > 10, "客户编号长度应大于10");
    }

    @Test
    @DisplayName("生成预警编号 - 以ALT开头")
    void testGenerateAlertNo() {
        String alertNo = idGenerator.generateAlertNo();
        assertNotNull(alertNo, "预警编号不应为空");
        assertTrue(alertNo.startsWith("ALT"), "预警编号应以ALT开头");
    }

    @Test
    @DisplayName("生成案件编号 - 以CAS开头")
    void testGenerateCaseNo() {
        String caseNo = idGenerator.generateCaseNo();
        assertNotNull(caseNo, "案件编号不应为空");
        assertTrue(caseNo.startsWith("CAS"), "案件编号应以CAS开头");
    }

    @Test
    @DisplayName("生成筛查编号 - 以SCR开头")
    void testGenerateScreeningNo() {
        String screeningNo = idGenerator.generateScreeningNo();
        assertNotNull(screeningNo, "筛查编号不应为空");
        assertTrue(screeningNo.startsWith("SCR"), "筛查编号应以SCR开头");
    }

    @Test
    @DisplayName("自定义前缀生成编号 - 以指定前缀开头")
    void testGenerateWithPrefix() {
        String id = idGenerator.generate("PREFIX");
        assertNotNull(id, "生成的编号不应为空");
        assertTrue(id.startsWith("PREFIX"), "编号应以PREFIX开头");
    }

    @Test
    @DisplayName("唯一性测试 - 生成100个ID全部唯一")
    void testUniqueness() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(idGenerator.generateCustomerNo());
        }
        assertEquals(100, ids.size(), "生成的100个客户编号应全部唯一");
    }
}
