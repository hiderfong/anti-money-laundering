package com.insurance.aml.module.ai.service;

import com.insurance.aml.module.ai.service.support.AiRiskSupervisedModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import smile.classification.LogisticRegression;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AI监督模型容器测试")
class AiRiskSupervisedModelTest {

    private AiRiskSupervisedModel newModel(Path dir) {
        AiRiskSupervisedModel m = new AiRiskSupervisedModel();
        ReflectionTestUtils.setField(m, "modelPath", dir.toString());
        return m;
    }

    private LogisticRegression trainTiny() {
        double[][] x = {{0,0,0,0,0,0,0,0,0,0,0,0}, {9,9,9,9,9,9,9,9,9,9,9,9},
                        {0,0,0,0,0,0,0,0,0,0,0,1}, {8,8,8,8,8,8,8,8,8,8,8,8}};
        int[] y = {0, 1, 0, 1};
        return LogisticRegression.fit(x, y);
    }

    @Test
    @DisplayName("未训练时predictProbability返回empty")
    void notReady_returnsEmpty() {
        AiRiskSupervisedModel m = new AiRiskSupervisedModel();
        assertFalse(m.isReady());
        assertTrue(m.predictProbability(new double[12]).isEmpty());
    }

    @Test
    @DisplayName("replace后就绪且能给出概率，存盘加载往返一致")
    void replaceThenSaveLoadRoundTrip(@TempDir Path dir) {
        AiRiskSupervisedModel m = newModel(dir);
        m.replace(trainTiny(), 4, 2, 2, 1.0, 1.0);
        assertTrue(m.isReady());
        Optional<Double> p = m.predictProbability(new double[]{9,9,9,9,9,9,9,9,9,9,9,9});
        assertTrue(p.isPresent());
        assertTrue(p.get() >= 0.0 && p.get() <= 1.0);

        AiRiskSupervisedModel reloaded = newModel(dir);
        reloaded.init();
        assertTrue(reloaded.isReady());
        assertEquals(4, reloaded.getSampleCount());
    }
}
