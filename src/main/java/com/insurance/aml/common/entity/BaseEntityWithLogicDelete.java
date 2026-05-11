package com.insurance.aml.common.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带逻辑删除的实体基类
 * 继承 BaseEntity，增加逻辑删除字段
 * deleted: 0=正常, 1=已删除
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseEntityWithLogicDelete extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 逻辑删除标识：0=正常, 1=已删除
     */
    @TableLogic
    private Integer deleted;
}
