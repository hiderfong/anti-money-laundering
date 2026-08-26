package com.insurance.aml.module.organization.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 机构树节点。
 */
@Data
@Builder
public class OrganizationTreeVO {
    private Long id;
    private String orgCode;
    private String orgName;
    private String orgType;
    private String status;
    @Builder.Default
    private List<OrganizationTreeVO> children = new ArrayList<>();
}
