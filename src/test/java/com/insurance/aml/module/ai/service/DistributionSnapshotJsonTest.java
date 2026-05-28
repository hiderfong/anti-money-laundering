package com.insurance.aml.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.module.ai.model.dto.DistributionSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DistributionSnapshot JSON 往返测试")
class DistributionSnapshotJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("序列化后反序列化字段一致")
    void roundTrip() throws Exception {
        DistributionSnapshot snap = DistributionSnapshot.builder()
                .bins(4)
                .lo(0.0)
                .hi(1.0)
                .counts(new int[]{1, 2, 3, 4})
                .total(10)
                .capturedAt(LocalDateTime.of(2026, 5, 21, 4, 0, 0))
                .build();

        String json = mapper.writeValueAsString(snap);
        DistributionSnapshot back = mapper.readValue(json, DistributionSnapshot.class);

        assertEquals(4, back.getBins());
        assertEquals(0.0, back.getLo());
        assertEquals(1.0, back.getHi());
        assertArrayEquals(new int[]{1, 2, 3, 4}, back.getCounts());
        assertEquals(10, back.getTotal());
        assertEquals(LocalDateTime.of(2026, 5, 21, 4, 0, 0), back.getCapturedAt());
    }
}
