package com.insurance.aml.module.screening.service;

import com.insurance.aml.common.result.PageQuery;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.screening.model.dto.ReviewRequest;
import com.insurance.aml.module.screening.model.dto.ScreeningResultVO;

import java.util.List;

/**
 * 制裁名单筛查服务接口
 * 提供客户筛查、批量筛查、结果审核等能力
 */
public interface ScreeningService {

    /**
     * 对单个客户进行制裁名单筛查
     *
     * @param customerId    客户ID
     * @param screeningType 筛查类型（CUSTOMER_ONBOARD/INFO_CHANGE/TRANSACTION/PERIODIC/BATCH）
     * @return 命中数量
     */
    Long screenCustomer(Long customerId, String screeningType);

    /**
     * 批量筛查多个客户
     *
     * @param customerIds 客户ID列表
     * @return 各客户筛查命中数的列表（与customerIds顺序对应）
     */
    List<Long> screenBatch(List<Long> customerIds);

    /**
     * 审核筛查命中结果
     *
     * @param req 审核请求
     */
    void reviewHit(ReviewRequest req);

    /**
     * 分页查询筛查结果
     *
     * @param pageQuery    分页参数
     * @param customerId   客户ID（可选，null表示不过滤）
     * @param reviewStatus 审核状态（可选，null表示不过滤）
     * @return 分页结果
     */
    PageResult<ScreeningResultVO> pageResults(PageQuery pageQuery, Long customerId, String reviewStatus);
}
