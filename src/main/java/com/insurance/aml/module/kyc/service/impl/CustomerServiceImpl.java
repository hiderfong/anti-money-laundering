package com.insurance.aml.module.kyc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.service.impl.BaseServiceXImpl;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客户服务实现类
 */
@Slf4j
@Service
public class CustomerServiceImpl extends BaseServiceXImpl<CustomerMapper, Customer> implements CustomerService {

    @Autowired
    private CustomerBeneficialOwnerMapper beneficialOwnerMapper;

    @Autowired
    private VerificationRecordMapper verificationRecordMapper;

    @Autowired
    private CustomerRiskRatingLogMapper riskRatingLogMapper;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private EncryptUtils encryptUtils;

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
        customer.setRiskLevel("LOW");
        customer.setRiskScore(0);
        customer.setIsPep(false);
        customer.setIsSanctioned(false);
        customer.setKycStatus("INCOMPLETE");
        customer.setStatus("ACTIVE");

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
                .eq(CustomerBeneficialOwner::getStatus, "ACTIVE");
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
            newRiskLevel = "HIGH";
        } else if (score >= 30) {
            newRiskLevel = "MEDIUM";
        } else {
            newRiskLevel = "LOW";
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

    // ==================== 私有方法 ====================

    /**
     * 客户实体转换为VO
     */
    private CustomerVO convertToVO(Customer customer) {
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
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

    /**
     * 判断是否为高风险职业
     * 预留扩展，当前简单匹配关键词
     */
    private boolean isHighRiskOccupation(String occupation) {
        if (!StringUtils.hasText(occupation)) {
            return false;
        }
        String[] highRiskKeywords = {"博彩", "典当", "贵金属", "珠宝", "地下钱庄", "换汇"};
        for (String keyword : highRiskKeywords) {
            if (occupation.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
