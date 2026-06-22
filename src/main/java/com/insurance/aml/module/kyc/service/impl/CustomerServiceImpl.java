package com.insurance.aml.module.kyc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.service.impl.BaseServiceXImpl;
import com.insurance.aml.common.enums.CustomerStatus;
import com.insurance.aml.common.enums.KycStatus;
import com.insurance.aml.common.enums.RiskLevel;
import com.insurance.aml.common.util.EncryptUtils;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.kyc.mapper.CustomerBeneficialOwnerMapper;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.mapper.CustomerRiskRatingLogMapper;
import com.insurance.aml.module.kyc.mapper.VerificationRecordMapper;
import com.insurance.aml.module.kyc.model.dto.*;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.kyc.model.entity.CustomerBeneficialOwner;
import com.insurance.aml.module.kyc.model.entity.CustomerRiskRatingLog;
import com.insurance.aml.module.kyc.model.entity.VerificationRecord;
import com.insurance.aml.module.kyc.service.CustomerService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.insurance.aml.module.kyc.service.support.CustomerGraphSupport.*;

/**
 * 客户服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerServiceImpl extends BaseServiceXImpl<CustomerMapper, Customer> implements CustomerService {
    private final CustomerBeneficialOwnerMapper beneficialOwnerMapper;
    private final VerificationRecordMapper verificationRecordMapper;
    private final CustomerRiskRatingLogMapper riskRatingLogMapper;
    private final IdGenerator idGenerator;
    private final EncryptUtils encryptUtils;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建客户
     * 校验证件号唯一性，生成客户编号，加密敏感字段后保存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Customer createCustomer(CustomerCreateRequest req) {
        log.info("创建客户，客户名称：{}，客户类型：{}", req.getName(), req.getCustomerType());

        // 校验证件号唯一性（加密存储，需要查询全部记录比对，或使用索引）
        if (StringUtils.hasText(req.getIdNumber())) {
            LambdaQueryWrapper<Customer> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(Customer::getIdType, req.getIdType())
                    .eq(Customer::getIdNumber, encryptUtils.encrypt(req.getIdNumber()));
            if (baseMapper.selectCount(checkWrapper) > 0) {
                throw new BusinessException(ResultCode.CUSTOMER_ID_EXISTS, "该证件号已存在");
            }
        }

        // 构建客户实体
        Customer customer = new Customer();
        BeanUtils.copyProperties(req, customer);

        // 生成客户编号
        customer.setCustomerNo(idGenerator.generateCustomerNo());

        // 加密敏感字段
        if (StringUtils.hasText(req.getIdNumber())) {
            customer.setIdNumber(encryptUtils.encrypt(req.getIdNumber()));
        }
        if (StringUtils.hasText(req.getPhone())) {
            customer.setPhone(encryptUtils.encrypt(req.getPhone()));
        }

        // 设置默认值
        customer.setRiskLevel(RiskLevel.LOW.getCode());
        customer.setRiskScore(0);
        customer.setIsPep(false);
        customer.setIsSanctioned(false);
        customer.setKycStatus(KycStatus.INCOMPLETE.getCode());
        customer.setStatus(CustomerStatus.ACTIVE.getCode());

        // 保存
        baseMapper.insert(customer);
        log.info("客户创建成功，客户ID：{}，客户编号：{}", customer.getId(), customer.getCustomerNo());
        return customer;
    }

    /**
     * 更新客户信息
     * 仅更新非空字段，加密敏感字段后保存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "customer", key = "'customer::' + #req.id")
    public Customer updateCustomer(CustomerUpdateRequest req) {
        log.info("更新客户信息，客户ID：{}", req.getId());

        // 查询客户是否存在
        Customer customer = baseMapper.selectById(req.getId());
        if (customer == null) {
            throw new BusinessException(ResultCode.CUSTOMER_NOT_FOUND);
        }

        // 仅更新非空字段
        if (req.getNameEn() != null) customer.setNameEn(req.getNameEn());
        if (req.getGender() != null) customer.setGender(req.getGender());
        if (req.getNationality() != null) customer.setNationality(req.getNationality());
        if (req.getBirthDate() != null) customer.setBirthDate(req.getBirthDate());
        if (req.getIdType() != null) customer.setIdType(req.getIdType());
        if (req.getIdNumber() != null) customer.setIdNumber(encryptUtils.encrypt(req.getIdNumber()));
        if (req.getIdIssuingAuthority() != null) customer.setIdIssuingAuthority(req.getIdIssuingAuthority());
        if (req.getIdExpiryDate() != null) customer.setIdExpiryDate(req.getIdExpiryDate());
        if (req.getAddress() != null) customer.setAddress(req.getAddress());
        if (req.getResidenceAddress() != null) customer.setResidenceAddress(req.getResidenceAddress());
        if (req.getPhone() != null) customer.setPhone(encryptUtils.encrypt(req.getPhone()));
        if (req.getEmail() != null) customer.setEmail(req.getEmail());
        if (req.getOccupation() != null) customer.setOccupation(req.getOccupation());
        if (req.getEmployer() != null) customer.setEmployer(req.getEmployer());
        if (req.getJobTitle() != null) customer.setJobTitle(req.getJobTitle());
        if (req.getAnnualIncomeRange() != null) customer.setAnnualIncomeRange(req.getAnnualIncomeRange());
        if (req.getTaxResidentStatus() != null) customer.setTaxResidentStatus(req.getTaxResidentStatus());
        if (req.getUnifiedCreditCode() != null) customer.setUnifiedCreditCode(req.getUnifiedCreditCode());
        if (req.getEnterpriseType() != null) customer.setEnterpriseType(req.getEnterpriseType());
        if (req.getRegisteredCapital() != null) customer.setRegisteredCapital(req.getRegisteredCapital());
        if (req.getBusinessScope() != null) customer.setBusinessScope(req.getBusinessScope());
        if (req.getLegalRepresentative() != null) customer.setLegalRepresentative(req.getLegalRepresentative());

        // 保存更新
        baseMapper.updateById(customer);
        log.info("客户信息更新成功，客户ID：{}", customer.getId());
        return customer;
    }

    /**
     * 获取客户详情
     * 加载客户基本信息和受益所有人列表，转换为VO并脱敏
     */
    @Override
    @Cacheable(value = "customer", key = "'customer::' + #id")
    public CustomerVO getCustomerDetail(Long id) {
        log.debug("获取客户详情，客户ID：{}", id);

        // 查询客户
        Customer customer = baseMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ResultCode.CUSTOMER_NOT_FOUND);
        }

        // 转换为VO
        CustomerVO vo = convertToVO(customer);

        // 加载受益所有人
        LambdaQueryWrapper<CustomerBeneficialOwner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(CustomerBeneficialOwner::getCustomerId, id)
                .eq(CustomerBeneficialOwner::getStatus, CustomerStatus.ACTIVE.getCode());
        List<CustomerBeneficialOwner> owners = beneficialOwnerMapper.selectList(ownerWrapper);
        vo.setBeneficialOwners(owners.stream()
                .map(this::convertOwnerToVO)
                .collect(Collectors.toList()));

        return vo;
    }

    /**
     * 分页查询客户列表
     * 根据查询条件构建LambdaQueryWrapper，执行分页查询
     */
    @Override
    public PageResult<CustomerVO> pageQueryCustomers(CustomerQueryRequest req) {
        log.debug("分页查询客户列表，查询条件：{}", req);

        // 构建查询条件
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(req.getName()), Customer::getName, req.getName())
                .eq(StringUtils.hasText(req.getCustomerType()), Customer::getCustomerType, req.getCustomerType())
                .eq(StringUtils.hasText(req.getRiskLevel()), Customer::getRiskLevel, req.getRiskLevel())
                .eq(StringUtils.hasText(req.getKycStatus()), Customer::getKycStatus, req.getKycStatus())
                .like(StringUtils.hasText(req.getIdNumber()), Customer::getIdNumber, req.getIdNumber())
                .like(StringUtils.hasText(req.getPhone()), Customer::getPhone, req.getPhone())
                .eq(StringUtils.hasText(req.getStatus()), Customer::getStatus, req.getStatus())
                .eq(req.getPepFlag() != null, Customer::getIsPep, req.getPepFlag())
                .eq(req.getSanctionedFlag() != null, Customer::getIsSanctioned, req.getSanctionedFlag())
                .orderByDesc(Customer::getCreatedTime);

        // 执行分页查询
        IPage<Customer> page = pageQuery(req, wrapper);

        // 转换为VO列表
        List<CustomerVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return PageResult.<CustomerVO>builder()
                .total(page.getTotal())
                .list(voList)
                .page((int) page.getCurrent())
                .size((int) page.getSize())
                .totalPages((int) page.getPages())
                .build();
    }

    /**
     * 获取客户360度视图
     * 整合客户基本信息、受益所有人、验证记录、风险评级历史、预警数量
     */
    @Override
    public Customer360VO getCustomer360View(Long id) {
        log.debug("获取客户360度视图，客户ID：{}", id);

        Customer360VO result = new Customer360VO();

        // 加载客户详情（含受益所有人）
        CustomerVO customerVO = getCustomerDetail(id);
        result.setCustomer(customerVO);
        result.setBeneficialOwners(customerVO.getBeneficialOwners());

        // 加载验证历史记录
        LambdaQueryWrapper<VerificationRecord> verificationWrapper = new LambdaQueryWrapper<>();
        verificationWrapper.eq(VerificationRecord::getCustomerId, id)
                .orderByDesc(VerificationRecord::getCreatedTime);
        List<VerificationRecord> verifications = verificationRecordMapper.selectList(verificationWrapper);
        result.setVerificationHistory(verifications);

        // 加载风险评级变更历史
        LambdaQueryWrapper<CustomerRiskRatingLog> ratingWrapper = new LambdaQueryWrapper<>();
        ratingWrapper.eq(CustomerRiskRatingLog::getCustomerId, id)
                .orderByDesc(CustomerRiskRatingLog::getChangedTime);
        List<CustomerRiskRatingLog> ratingLogs = riskRatingLogMapper.selectList(ratingWrapper);
        result.setRiskRatingHistory(ratingLogs);

        // 预警数量（预留，暂返回0）
        result.setAlertCount(0);

        return result;
    }

    /**
     * 获取客户关系图谱
     */
    @Override
    public CustomerRelationshipGraphVO getCustomerRelationshipGraph(Long id) {
        log.debug("获取客户关系图谱，客户ID：{}", id);

        CustomerVO customerVO = getCustomerDetail(id);
        CustomerRelationshipGraphVO graph = new CustomerRelationshipGraphVO();
        Map<String, CustomerRelationshipGraphVO.Node> nodes = new LinkedHashMap<>();
        List<CustomerRelationshipGraphVO.Link> links = new ArrayList<>();

        String customerNodeId = "customer-" + id;
        addNode(nodes, node(customerNodeId, customerVO.getName(), "CUSTOMER", "客户")
                .riskLevel(customerVO.getRiskLevel())
                .riskScore(customerVO.getRiskScore())
                .status(customerVO.getStatus())
                .detail(Map.of(
                        "customerNo", nullToBlank(customerVO.getCustomerNo()),
                        "customerType", nullToBlank(customerVO.getCustomerType()),
                        "kycStatus", nullToBlank(customerVO.getKycStatus()),
                        "isPep", Boolean.TRUE.equals(customerVO.getIsPep()),
                        "isSanctioned", Boolean.TRUE.equals(customerVO.getIsSanctioned())
                ))
                .build());

        if (Boolean.TRUE.equals(customerVO.getIsPep())) {
            String pepNodeId = "pep-" + id;
            addNode(nodes, node(pepNodeId, "PEP敏感身份", "PEP", "PEP/制裁名单")
                    .riskLevel("HIGH")
                    .status(nullToBlank(customerVO.getPepType()))
                    .detail(Map.of("pepType", nullToBlank(customerVO.getPepType())))
                    .build());
            links.add(link(customerNodeId, pepNodeId, "身份标签", null, "HIGH"));
        }
        if (Boolean.TRUE.equals(customerVO.getIsSanctioned())) {
            String sanctionNodeId = "sanction-" + id;
            addNode(nodes, node(sanctionNodeId, "制裁名单标记", "SANCTION", "PEP/制裁名单")
                    .riskLevel("CRITICAL")
                    .status("命中")
                    .build());
            links.add(link(customerNodeId, sanctionNodeId, "名单风险", null, "CRITICAL"));
        }

        addBeneficialOwnerNodes(customerNodeId, customerVO.getBeneficialOwners(), nodes, links);
        List<Map<String, Object>> policies = addPolicyAndProductNodes(id, customerNodeId, nodes, links);
        List<Map<String, Object>> transactions = addTransactionNodes(id, customerNodeId, nodes, links);
        List<Map<String, Object>> alerts = addAlertNodes(id, customerNodeId, nodes, links);
        List<Map<String, Object>> cases = addCaseNodes(id, customerNodeId, nodes, links);
        List<Map<String, Object>> strReports = addStrReportNodes(id, customerNodeId, nodes, links);
        List<Map<String, Object>> screeningResults = addWatchlistNodes(id, customerNodeId, nodes, links);

        graph.setNodes(new ArrayList<>(nodes.values()));
        graph.setLinks(links);
        fillGraphSummary(graph, customerVO, policies, transactions, alerts, cases, strReports, screeningResults);
        graph.setInsights(buildGraphInsights(customerVO, graph.getSummary()));
        return graph;
    }

    /**
     * 触发客户风险评估
     * 根据PEP状态、制裁状态等因素计算风险评分，确定风险等级
     * 评分规则：PEP +30分，被制裁 +50分，高风险职业 +20分
     * 评分阈值：>60 高风险，30-60 中风险，<30 低风险
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assessRiskLevel(Long customerId) {
        log.info("触发客户风险评估，客户ID：{}", customerId);

        // 查询客户
        Customer customer = baseMapper.selectById(customerId);
        if (customer == null) {
            throw new BusinessException(ResultCode.CUSTOMER_NOT_FOUND);
        }

        // 记录变更前状态
        String oldRiskLevel = customer.getRiskLevel();
        Integer oldRiskScore = customer.getRiskScore();

        // 计算风险评分
        int score = 0;
        StringBuilder reasonBuilder = new StringBuilder();

        // PEP状态加分
        if (Boolean.TRUE.equals(customer.getIsPep())) {
            score += 30;
            reasonBuilder.append("PEP身份(+30); ");
        }

        // 制裁状态加分
        if (Boolean.TRUE.equals(customer.getIsSanctioned())) {
            score += 50;
            reasonBuilder.append("被制裁状态(+50); ");
        }

        // 高风险职业/行业加分（预留扩展）
        if (StringUtils.hasText(customer.getOccupation())
                && isHighRiskOccupation(customer.getOccupation())) {
            score += 20;
            reasonBuilder.append("高风险职业(+20); ");
        }

        // 确定风险等级
        String newRiskLevel;
        if (score > 60) {
            newRiskLevel = RiskLevel.HIGH.getCode();
        } else if (score >= 30) {
            newRiskLevel = RiskLevel.MEDIUM.getCode();
        } else {
            newRiskLevel = RiskLevel.LOW.getCode();
        }

        // 更新客户风险信息
        customer.setRiskScore(score);
        customer.setRiskLevel(newRiskLevel);
        customer.setRiskUpdateTime(LocalDateTime.now());
        baseMapper.updateById(customer);

        // 记录风险评级变更日志
        CustomerRiskRatingLog logEntity = new CustomerRiskRatingLog();
        logEntity.setCustomerId(customerId);
        logEntity.setOldRiskLevel(oldRiskLevel);
        logEntity.setNewRiskLevel(newRiskLevel);
        logEntity.setOldRiskScore(oldRiskScore);
        logEntity.setNewRiskScore(score);
        logEntity.setChangeReason(reasonBuilder.length() > 0
                ? reasonBuilder.toString() : "定期风险评估");
        logEntity.setChangeType("AUTO");
        logEntity.setChangedBy(String.valueOf(SecurityUtils.getCurrentUserId()));
        logEntity.setChangedTime(LocalDateTime.now());
        riskRatingLogMapper.insert(logEntity);

        log.info("客户风险评估完成，客户ID：{}，风险等级：{} -> {}，评分：{} -> {}",
                customerId, oldRiskLevel, newRiskLevel, oldRiskScore, score);
    }

    private void addBeneficialOwnerNodes(String customerNodeId,
                                         List<CustomerBeneficialOwnerVO> owners,
                                         Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                         List<CustomerRelationshipGraphVO.Link> links) {
        if (owners == null) {
            return;
        }
        for (CustomerBeneficialOwnerVO owner : owners) {
            String ownerNodeId = "owner-" + owner.getId();
            BigDecimal ownership = owner.getOwnershipPercentage();
            String riskLevel = ownership != null && ownership.compareTo(BigDecimal.valueOf(25)) >= 0 ? "HIGH" : "MEDIUM";
            addNode(nodes, node(ownerNodeId, firstNonBlank(owner.getOwnerName(), owner.getRelationship(), "受益所有人"),
                    "BENEFICIAL_OWNER", "受益人")
                    .riskLevel(riskLevel)
                    .status(owner.getStatus())
                    .detail(Map.of(
                            "relationship", nullToBlank(owner.getRelationship()),
                            "controlType", nullToBlank(owner.getControlType()),
                            "ownershipPercentage", ownership == null ? "" : ownership.toPlainString()
                    ))
                    .build());
            links.add(link(customerNodeId, ownerNodeId,
                    ownership == null ? "受益/控制" : "受益/控制 " + ownership.stripTrailingZeros().toPlainString() + "%",
                    ownership, riskLevel));
        }
    }

    private List<Map<String, Object>> addPolicyAndProductNodes(Long customerId,
                                                                String customerNodeId,
                                                                Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                                                List<CustomerRelationshipGraphVO.Link> links) {
        List<Map<String, Object>> policies = jdbcTemplate.queryForList("""
                SELECT p.id AS policyId,
                       p.customer_id AS policyCustomerId,
                       p.policy_no AS policyNo,
                       p.policy_status AS policyStatus,
                       p.payment_mode AS paymentMode,
                       p.channel AS channel,
                       p.premium AS premium,
                       p.sum_insured AS sumInsured,
                       p.beneficiary_info AS beneficiaryInfo,
                       pr.id AS productId,
                       pr.product_name AS productName,
                       pr.product_type AS productType,
                       pr.risk_level AS productRiskLevel,
                       pr.risk_score AS productRiskScore
                FROM t_policy p
                LEFT JOIN t_product pr ON pr.id = p.product_id
                WHERE p.customer_id = ?
                   OR EXISTS (
                       SELECT 1
                       FROM t_transaction tx
                       WHERE tx.policy_id = p.id
                         AND tx.customer_id = ?
                   )
                ORDER BY p.created_time DESC
                LIMIT 12
                """, customerId, customerId);

        for (Map<String, Object> row : policies) {
            String policyId = stringValue(row, "policyId");
            String policyNodeId = "policy-" + policyId;
            BigDecimal premium = decimalValue(row, "premium");
            String policyCustomerId = stringValue(row, "policyCustomerId");
            addNode(nodes, node(policyNodeId, firstNonBlank(stringValue(row, "policyNo"), "保单" + policyId),
                    "POLICY", "保单")
                    .status(stringValue(row, "policyStatus"))
                    .amount(premium)
                    .detail(copyDetail(row))
                    .build());
            links.add(link(customerNodeId, policyNodeId,
                    String.valueOf(customerId).equals(policyCustomerId) ? "持有保单" : "交易关联保单",
                    premium, null));

            String productId = stringValue(row, "productId");
            if (StringUtils.hasText(productId)) {
                String productNodeId = "product-" + productId;
                addNode(nodes, node(productNodeId, firstNonBlank(stringValue(row, "productName"), "保险产品" + productId),
                        "PRODUCT", "产品")
                        .riskLevel(stringValue(row, "productRiskLevel"))
                        .riskScore(intValue(row, "productRiskScore"))
                        .detail(copyDetail(row))
                        .build());
                links.add(link(policyNodeId, productNodeId, "承保产品", null, stringValue(row, "productRiskLevel")));
            }

            String beneficiaryInfo = stringValue(row, "beneficiaryInfo");
            if (StringUtils.hasText(beneficiaryInfo)) {
                String beneficiaryNodeId = "policy-beneficiary-" + policyId;
                addNode(nodes, node(beneficiaryNodeId, "保单受益安排", "POLICY_BENEFICIARY", "受益人")
                        .detail(Map.of("beneficiaryInfo", beneficiaryInfo))
                        .build());
                links.add(link(policyNodeId, beneficiaryNodeId, "约定受益人", null, null));
            }
        }
        return policies;
    }

    private List<Map<String, Object>> addTransactionNodes(Long customerId,
                                                           String customerNodeId,
                                                           Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                                           List<CustomerRelationshipGraphVO.Link> links) {
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList("""
                SELECT id,
                       transaction_no AS transactionNo,
                       policy_id AS policyId,
                       transaction_type AS transactionType,
                       amount,
                       currency,
                       payment_method AS paymentMethod,
                       counterparty_name AS counterpartyName,
                       counterparty_account AS counterpartyAccount,
                       counterparty_bank AS counterpartyBank,
                       is_cross_border AS crossBorder,
                       transaction_time AS transactionTime,
                       status
                FROM t_transaction
                WHERE customer_id = ?
                ORDER BY transaction_time DESC
                LIMIT 16
                """, customerId);

        for (Map<String, Object> row : transactions) {
            String transactionId = stringValue(row, "id");
            String transactionNodeId = "transaction-" + transactionId;
            BigDecimal amount = decimalValue(row, "amount");
            boolean crossBorder = booleanValue(row, "crossBorder");
            addNode(nodes, node(transactionNodeId, firstNonBlank(stringValue(row, "transactionNo"), "交易" + transactionId),
                    "TRANSACTION", "交易")
                    .riskLevel(crossBorder ? "HIGH" : null)
                    .status(stringValue(row, "status"))
                    .amount(amount)
                    .detail(copyDetail(row))
                    .build());
            links.add(link(customerNodeId, transactionNodeId,
                    firstNonBlank(stringValue(row, "transactionType"), "发生交易"), amount, crossBorder ? "HIGH" : null));

            String policyId = stringValue(row, "policyId");
            if (StringUtils.hasText(policyId) && nodes.containsKey("policy-" + policyId)) {
                links.add(link("policy-" + policyId, transactionNodeId, "保单交易", amount, null));
            }

            String counterparty = stringValue(row, "counterpartyName");
            if (StringUtils.hasText(counterparty)) {
                String counterpartyNodeId = "counterparty-" + normalizeId(counterparty);
                addNode(nodes, node(counterpartyNodeId, counterparty, "COUNTERPARTY", "交易对手")
                        .detail(Map.of(
                                "account", nullToBlank(stringValue(row, "counterpartyAccount")),
                                "bank", nullToBlank(stringValue(row, "counterpartyBank"))
                        ))
                        .build());
                links.add(link(transactionNodeId, counterpartyNodeId, "交易对手", amount, crossBorder ? "HIGH" : null));
            }
        }
        return transactions;
    }

    private List<Map<String, Object>> addAlertNodes(Long customerId,
                                                     String customerNodeId,
                                                     Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                                     List<CustomerRelationshipGraphVO.Link> links) {
        List<Map<String, Object>> alerts = jdbcTemplate.queryForList("""
                SELECT id,
                       alert_no AS alertNo,
                       alert_type AS alertType,
                       risk_score AS riskScore,
                       risk_level AS riskLevel,
                       source_rule_codes AS sourceRuleCodes,
                       alert_summary AS alertSummary,
                       status,
                       process_result AS processResult,
                       related_transaction_ids AS relatedTransactionIds,
                       created_time AS createdTime
                FROM t_alert
                WHERE customer_id = ?
                ORDER BY created_time DESC
                LIMIT 12
                """, customerId);

        for (Map<String, Object> row : alerts) {
            String alertId = stringValue(row, "id");
            String alertNodeId = "alert-" + alertId;
            addNode(nodes, node(alertNodeId, firstNonBlank(stringValue(row, "alertNo"), stringValue(row, "alertType"), "预警" + alertId),
                    "ALERT", "预警")
                    .riskLevel(stringValue(row, "riskLevel"))
                    .riskScore(intValue(row, "riskScore"))
                    .status(stringValue(row, "status"))
                    .detail(copyDetail(row))
                    .build());
            links.add(link(customerNodeId, alertNodeId, "触发预警", null, stringValue(row, "riskLevel")));

            for (String transactionId : splitIds(stringValue(row, "relatedTransactionIds"))) {
                if (nodes.containsKey("transaction-" + transactionId)) {
                    links.add(link("transaction-" + transactionId, alertNodeId, "触发", null, stringValue(row, "riskLevel")));
                }
            }

            String alertType = stringValue(row, "alertType").toUpperCase(Locale.ROOT);
            if (alertType.contains("SANCTION") && nodes.containsKey("sanction-" + customerId)) {
                links.add(link("sanction-" + customerId, alertNodeId, "名单命中预警", null, "CRITICAL"));
            }
            if (alertType.contains("PEP") && nodes.containsKey("pep-" + customerId)) {
                links.add(link("pep-" + customerId, alertNodeId, "PEP预警", null, "HIGH"));
            }
        }
        return alerts;
    }

    private List<Map<String, Object>> addCaseNodes(Long customerId,
                                                    String customerNodeId,
                                                    Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                                    List<CustomerRelationshipGraphVO.Link> links) {
        List<Map<String, Object>> cases = jdbcTemplate.queryForList("""
                SELECT id,
                       case_no AS caseNo,
                       alert_id AS alertId,
                       case_status AS caseStatus,
                       case_type AS caseType,
                       priority,
                       summary,
                       submit_time AS submitTime,
                       close_time AS closeTime,
                       created_time AS createdTime
                FROM t_case
                WHERE customer_id = ?
                ORDER BY created_time DESC
                LIMIT 10
                """, customerId);

        for (Map<String, Object> row : cases) {
            String caseId = stringValue(row, "id");
            String caseNodeId = "case-" + caseId;
            String riskLevel = intValue(row, "priority") != null && intValue(row, "priority") >= 80 ? "HIGH" : "MEDIUM";
            addNode(nodes, node(caseNodeId, firstNonBlank(stringValue(row, "caseNo"), "案件" + caseId),
                    "CASE", "案件")
                    .riskLevel(riskLevel)
                    .riskScore(intValue(row, "priority"))
                    .status(stringValue(row, "caseStatus"))
                    .detail(copyDetail(row))
                    .build());

            String alertId = stringValue(row, "alertId");
            if (StringUtils.hasText(alertId) && nodes.containsKey("alert-" + alertId)) {
                links.add(link("alert-" + alertId, caseNodeId, "升级案件", null, riskLevel));
            } else {
                links.add(link(customerNodeId, caseNodeId, "关联案件", null, riskLevel));
            }
        }
        return cases;
    }

    private List<Map<String, Object>> addStrReportNodes(Long customerId,
                                                         String customerNodeId,
                                                         Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                                         List<CustomerRelationshipGraphVO.Link> links) {
        List<Map<String, Object>> reports = jdbcTemplate.queryForList("""
                SELECT id,
                       report_no AS reportNo,
                       case_id AS caseId,
                       report_type AS reportType,
                       report_status AS reportStatus,
                       analysis_opinion AS analysisOpinion,
                       measures_taken AS measuresTaken,
                       submit_time AS submitTime,
                       submit_result AS submitResult,
                       created_time AS createdTime
                FROM t_str_report
                WHERE customer_id = ?
                ORDER BY created_time DESC
                LIMIT 10
                """, customerId);

        for (Map<String, Object> row : reports) {
            String reportId = stringValue(row, "id");
            String reportNodeId = "str-" + reportId;
            addNode(nodes, node(reportNodeId, firstNonBlank(stringValue(row, "reportNo"), "STR" + reportId),
                    "STR", "STR")
                    .riskLevel("HIGH")
                    .status(stringValue(row, "reportStatus"))
                    .detail(copyDetail(row))
                    .build());

            String caseId = stringValue(row, "caseId");
            if (StringUtils.hasText(caseId) && nodes.containsKey("case-" + caseId)) {
                links.add(link("case-" + caseId, reportNodeId, "形成STR", null, "HIGH"));
            } else {
                links.add(link(customerNodeId, reportNodeId, "关联STR", null, "HIGH"));
            }
        }
        return reports;
    }

    private List<Map<String, Object>> addWatchlistNodes(Long customerId,
                                                         String customerNodeId,
                                                         Map<String, CustomerRelationshipGraphVO.Node> nodes,
                                                         List<CustomerRelationshipGraphVO.Link> links) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
                SELECT id,
                       watchlist_entry_id AS watchlistEntryId,
                       watchlist_name AS watchlistName,
                       match_score AS matchScore,
                       match_type AS matchType,
                       match_field AS matchField,
                       review_status AS reviewStatus,
                       review_result AS reviewResult,
                       created_time AS createdTime
                FROM t_screening_result
                WHERE customer_id = ?
                ORDER BY created_time DESC
                LIMIT 10
                """, customerId);

        for (Map<String, Object> row : results) {
            String watchlistKey = firstNonBlank(stringValue(row, "watchlistEntryId"), stringValue(row, "id"));
            String watchlistNodeId = "watchlist-" + watchlistKey;
            BigDecimal matchScore = decimalValue(row, "matchScore");
            String riskLevel = matchScore != null && matchScore.compareTo(BigDecimal.valueOf(90)) >= 0 ? "CRITICAL" : "HIGH";
            addNode(nodes, node(watchlistNodeId, firstNonBlank(stringValue(row, "watchlistName"), "名单命中" + watchlistKey),
                    "WATCHLIST", "PEP/制裁名单")
                    .riskLevel(riskLevel)
                    .status(stringValue(row, "reviewStatus"))
                    .amount(matchScore)
                    .detail(copyDetail(row))
                    .build());
            links.add(link(customerNodeId, watchlistNodeId,
                    matchScore == null ? "名单筛查命中" : "名单筛查命中 " + matchScore.stripTrailingZeros().toPlainString(),
                    matchScore, riskLevel));
        }
        return results;
    }

    // ==================== 私有方法 ====================

    /**
     * 客户实体转换为VO
     */
    private CustomerVO convertToVO(Customer customer) {
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        vo.setIdNumber(decryptIfPresent(customer.getIdNumber()));
        vo.setPhone(decryptIfPresent(customer.getPhone()));
        return vo;
    }

    /**
     * 受益所有人实体转换为VO
     */
    private CustomerBeneficialOwnerVO convertOwnerToVO(CustomerBeneficialOwner owner) {
        CustomerBeneficialOwnerVO vo = new CustomerBeneficialOwnerVO();
        BeanUtils.copyProperties(owner, vo);
        return vo;
    }

    private String decryptIfPresent(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return encryptUtils.decrypt(value);
        } catch (RuntimeException e) {
            log.warn("敏感字段解密失败，保留原值: {}", e.getMessage());
            return value;
        }
    }

}
