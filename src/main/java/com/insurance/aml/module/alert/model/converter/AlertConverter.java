package com.insurance.aml.module.alert.model.converter;

import com.insurance.aml.module.alert.model.dto.AlertVO;
import com.insurance.aml.module.alert.model.entity.Alert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Alert 对象转换器
 * 使用 MapStruct 替代 BeanUtils.copyProperties
 *
 * 注意：AlertVO 中的 ruleDetails 字段为 List<AlertRuleDetail>，
 * 需要在 Service 层手动设置（或通过 @AfterMapping 注入），
 * 因为 Alert 实体中不直接持有该关联数据。
 */
@Mapper(componentModel = "spring")
public interface AlertConverter {

    /**
     * Alert 实体 -> AlertVO
     * Alert 未继承 BaseEntity，字段一一对应自动映射
     */
    AlertVO toVO(Alert alert);

    /**
     * Alert 实体列表 -> AlertVO 列表
     */
    List<AlertVO> toVOList(List<Alert> alerts);

    /**
     * AlertVO -> Alert 实体（反向转换，用于数据回写场景）
     */
    Alert toEntity(AlertVO alertVO);
}
