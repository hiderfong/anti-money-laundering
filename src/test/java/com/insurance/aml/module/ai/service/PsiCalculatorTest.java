package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.service.support.PsiCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PSI 计算器测试")
class PsiCalculatorTest {

    @Test
    @DisplayName("histogram 把 [lo,hi) 均匀分箱，越界值落入首/末箱")
    void histogram_bucketsAndClampsOutOfRange() {
        double[] values = {-1.0, 0.0, 0.05, 0.15, 0.99, 2.0};
        int[] h = PsiCalculator.histogram(values, 10, 0.0, 1.0);
        assertEquals(10, h.length);
        assertEquals(3, h[0]);
        assertEquals(1, h[1]);
        assertEquals(2, h[9]);
    }

    @Test
    @DisplayName("相同分布 PSI 约等于 0")
    void psi_identicalDistributions_nearZero() {
        int[] a = {10, 20, 30, 40};
        int[] b = {10, 20, 30, 40};
        assertEquals(0.0, PsiCalculator.psi(a, b), 1e-9);
    }

    @Test
    @DisplayName("显著不同的分布 PSI 较大")
    void psi_differentDistributions_large() {
        int[] expected = {90, 5, 3, 2};
        int[] actual = {2, 3, 5, 90};
        assertTrue(PsiCalculator.psi(expected, actual) > 0.25,
                "强烈反转的分布 PSI 应超过严重阈值");
    }

    @Test
    @DisplayName("单边零计数被 ε 平滑，不产生 NaN/Infinity")
    void psi_zeroBin_smoothedFinite() {
        int[] expected = {50, 50, 0};
        int[] actual = {40, 40, 20};
        double psi = PsiCalculator.psi(expected, actual);
        assertTrue(Double.isFinite(psi), "零计数应被平滑为有限值, got " + psi);
    }

    @Test
    @DisplayName("长度不一致或全零返回 NaN")
    void psi_invalidInputs_nan() {
        assertTrue(Double.isNaN(PsiCalculator.psi(new int[]{1, 2}, new int[]{1, 2, 3})));
        assertTrue(Double.isNaN(PsiCalculator.psi(new int[]{0, 0}, new int[]{1, 1})));
    }

    @Test
    @DisplayName("bins=1 时同总量分布 PSI 为 0")
    void psi_singleBin_zero() {
        int[] h = PsiCalculator.histogram(new double[]{0.2, 0.5, 0.9}, 1, 0.0, 1.0);
        assertEquals(1, h.length);
        assertEquals(3, h[0]);
        assertEquals(0.0, PsiCalculator.psi(h, h), 1e-9);
    }
}
