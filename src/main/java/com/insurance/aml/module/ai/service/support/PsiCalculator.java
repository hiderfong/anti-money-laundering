package com.insurance.aml.module.ai.service.support;

/**
 * Population Stability Index 计算工具（纯函数，无状态）。
 *
 * <p>histogram 把数值序列按等宽分箱（越界值落入首/末箱）；psi 比较两个等长计数向量，
 * 用 ε 平滑避免 ln(0) / 除零。结果落在 [0, ∞)，越大漂移越严重。</p>
 */
public final class PsiCalculator {

    private static final double EPS = 1e-6;

    private PsiCalculator() {
    }

    public static int[] histogram(double[] values, int bins, double lo, double hi) {
        int safeBins = Math.max(1, bins);
        int[] counts = new int[safeBins];
        if (values == null || values.length == 0) {
            return counts;
        }
        double width = (hi - lo) / safeBins;
        for (double v : values) {
            if (!Double.isFinite(v)) {
                continue;
            }
            int idx;
            if (width <= 0) {
                idx = 0;
            } else {
                idx = (int) Math.floor((v - lo) / width);
                if (idx < 0) {
                    idx = 0;
                } else if (idx >= safeBins) {
                    idx = safeBins - 1;
                }
            }
            counts[idx]++;
        }
        return counts;
    }

    public static double psi(int[] expected, int[] actual) {
        if (expected == null || actual == null
                || expected.length != actual.length || expected.length == 0) {
            return Double.NaN;
        }
        long expTotal = 0L;
        long actTotal = 0L;
        for (int c : expected) {
            expTotal += c;
        }
        for (int c : actual) {
            actTotal += c;
        }
        if (expTotal == 0L || actTotal == 0L) {
            return Double.NaN;
        }
        double psi = 0.0;
        for (int i = 0; i < expected.length; i++) {
            double e = Math.max((double) expected[i] / expTotal, EPS);
            double a = Math.max((double) actual[i] / actTotal, EPS);
            psi += (a - e) * Math.log(a / e);
        }
        return psi;
    }
}
