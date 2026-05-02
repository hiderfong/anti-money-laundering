package com.insurance.aml.module.kyc.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.service.BaseServiceX;
import com.insurance.aml.module.kyc.model.dto.*;
import com.insurance.aml.module.kyc.model.entity.Customer;

/**
 * 客户服务接口
 */
public interface CustomerService extends BaseServiceX<Customer> {

    /**
     * 创建客户
     *
     * @param req 客户创建请求
     * @return 创建的客户实体
     */
    Customer createCustomer(CustomerCreateRequest req);

    /**
     * 更新客户信息
     *
     * @param req 客户更新请求
     * @return 更新后的客户实体
     */
    Customer updateCustomer(CustomerUpdateRequest req);

    /**
     * 获取客户详情
     *
     * @param id 客户ID
     * @return 客户详情视图对象
     */
    CustomerVO getCustomerDetail(Long id);

    /**
     * 分页查询客户列表
     *
     * @param req 分页查询请求
     * @return 分页结果
     */
    PageResult<CustomerVO> pageQueryCustomers(CustomerQueryRequest req);

    /**
     * 获取客户360度视图
     * 整合客户基本信息、受益所有人、验证记录、风险评级历史
     *
     * @param id 客户ID
     * @return 客户360度视图对象
     */
    Customer360VO getCustomer360View(Long id);

    /**
     * 触发客户风险评估
     * 根据PEP状态、制裁状态等因素计算风险评分
     *
     * @param customerId 客户ID
     */
    void assessRiskLevel(Long customerId);
}
