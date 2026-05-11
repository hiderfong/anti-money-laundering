package com.insurance.aml.module.case_.model.converter;

import com.insurance.aml.module.case_.model.dto.CaseCreateRequest;
import com.insurance.aml.module.case_.model.dto.CaseDetailVO;
import com.insurance.aml.module.case_.model.dto.CaseVO;
import com.insurance.aml.module.case_.model.entity.Case;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Case 对象转换器
 * 使用 MapStruct 替代 BeanUtils.copyProperties
 *
 * 注意：
 * - CaseVO 中的 alertNo、investigationCount、hasStrReport 字段
 *   需要在 Service 层手动设置（Case 实体中没有这些字段）
 * - CaseDetailVO 继承 CaseVO，额外的 investigations/attachments/strReport/statusLogs
 *   也需要在 Service 层手动组装
 */
@Mapper(componentModel = "spring")
public interface CaseConverter {

    /**
     * Case 实体 -> CaseVO
     * Case 未继承 BaseEntity，字段一一对应自动映射
     * alertNo / investigationCount / hasStrReport 不映射，由 Service 层补充
     */
    @Named("toCaseVO")
    @Mapping(target = "alertNo", ignore = true)
    @Mapping(target = "investigationCount", ignore = true)
    @Mapping(target = "hasStrReport", ignore = true)
    CaseVO toVO(Case caseEntity);

    /**
     * Case 实体列表 -> CaseVO 列表
     */
    @Named("toCaseVOList")
    List<CaseVO> toVOList(List<Case> cases);

    /**
     * Case 实体 -> CaseDetailVO
     * 继承 CaseVO 的字段自动映射，额外的关联数据在 Service 层设置
     */
    @Named("toCaseDetailVO")
    @Mapping(target = "alertNo", ignore = true)
    @Mapping(target = "investigationCount", ignore = true)
    @Mapping(target = "hasStrReport", ignore = true)
    @Mapping(target = "investigations", ignore = true)
    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "strReport", ignore = true)
    @Mapping(target = "statusLogs", ignore = true)
    CaseDetailVO toDetailVO(Case caseEntity);

    /**
     * CaseCreateRequest -> Case 实体
     * 忽略由框架自动填充的字段
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "caseNo", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "customerName", ignore = true)
    @Mapping(target = "caseStatus", ignore = true)
    @Mapping(target = "investigatorId", ignore = true)
    @Mapping(target = "reviewerId", ignore = true)
    @Mapping(target = "approverId", ignore = true)
    @Mapping(target = "submitTime", ignore = true)
    @Mapping(target = "closeTime", ignore = true)
    @Mapping(target = "closeReason", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdTime", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedTime", ignore = true)
    Case toEntity(CaseCreateRequest request);
}
