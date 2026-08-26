package com.insurance.aml.module.reporting.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.aml.common.enums.ReportStatus;
import com.insurance.aml.common.enums.SubmitStatus;
import com.insurance.aml.common.exception.BusinessException;
import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.common.result.ResultCode;
import com.insurance.aml.common.util.IdGenerator;
import com.insurance.aml.common.util.SecurityUtils;
import com.insurance.aml.module.casemgmt.mapper.StrReportMapper;
import com.insurance.aml.module.casemgmt.model.entity.StrReport;
import com.insurance.aml.module.integration.mapper.IntegrationConnectorMapper;
import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.kyc.mapper.CustomerMapper;
import com.insurance.aml.module.kyc.model.entity.Customer;
import com.insurance.aml.module.monitoring.mapper.TransactionMapper;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.reporting.gateway.RegulatoryGatewayAdapter;
import com.insurance.aml.module.reporting.gateway.RegulatoryGatewayAdapterRegistry;
import com.insurance.aml.module.reporting.gateway.RegulatoryGatewayResult;
import com.insurance.aml.module.reporting.gateway.RegulatorySubmissionEnvelope;
import com.insurance.aml.module.reporting.mapper.LargeTxnReportMapper;
import com.insurance.aml.module.reporting.mapper.RegulatoryReceiptMapper;
import com.insurance.aml.module.reporting.mapper.RegulatorySubmissionMapper;
import com.insurance.aml.module.reporting.mapper.ReportSubmitLogMapper;
import com.insurance.aml.module.reporting.model.dto.RegulatoryReceiptRequest;
import com.insurance.aml.module.reporting.model.dto.RegulatoryResubmitRequest;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionDetailVO;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionOverviewVO;
import com.insurance.aml.module.reporting.model.dto.RegulatorySubmissionQuery;
import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.reporting.model.entity.RegulatoryReceipt;
import com.insurance.aml.module.reporting.model.entity.RegulatorySubmission;
import com.insurance.aml.module.reporting.model.entity.ReportSubmitLog;
import com.insurance.aml.module.reporting.security.RegulatoryPayloadSigner;
import com.insurance.aml.module.reporting.security.RegulatorySignature;
import com.insurance.aml.module.reporting.service.RegulatorySubmissionService;
import com.insurance.aml.module.reporting.service.XmlGeneratorService;
import com.insurance.aml.module.reporting.validation.RegulatoryValidationResult;
import com.insurance.aml.module.reporting.validation.RegulatoryXmlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一监管报送服务。源报告保存当前业务状态，本表按版本保存每次对外提交的完整证据链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatorySubmissionServiceImpl implements RegulatorySubmissionService {
    private static final String LARGE_TXN = "LARGE_TXN";
    private static final String SUSPICIOUS = "SUSPICIOUS";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final String PENDING = "PENDING";
    private static final String FAILED = "FAILED";
    private static final String SUBMITTED = "SUBMITTED";

    private final RegulatorySubmissionMapper submissionMapper;
    private final RegulatoryReceiptMapper receiptMapper;
    private final IntegrationConnectorMapper connectorMapper;
    private final LargeTxnReportMapper largeTxnReportMapper;
    private final StrReportMapper strReportMapper;
    private final CustomerMapper customerMapper;
    private final TransactionMapper transactionMapper;
    private final ReportSubmitLogMapper submitLogMapper;
    private final XmlGeneratorService xmlGeneratorService;
    private final RegulatoryXmlValidator xmlValidator;
    private final RegulatoryPayloadSigner payloadSigner;
    private final RegulatoryGatewayAdapterRegistry gatewayRegistry;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Override
    public RegulatorySubmissionOverviewVO overview() {
        long total = submissionMapper.selectCount(null);
        long accepted = countStatus(ACCEPTED);
        long rejected = countStatus(REJECTED);
        long failed = countStatus(FAILED);
        long pending = submissionMapper.selectCount(new LambdaQueryWrapper<RegulatorySubmission>()
                .eq(RegulatorySubmission::getStatus, SUBMITTED)
                .eq(RegulatorySubmission::getReceiptStatus, PENDING));
        long resubmissions = submissionMapper.selectCount(new LambdaQueryWrapper<RegulatorySubmission>()
                .gt(RegulatorySubmission::getVersionNo, 1));
        long finalCount = accepted + rejected;
        double rate = finalCount == 0 ? 0D : accepted * 100D / finalCount;
        return RegulatorySubmissionOverviewVO.builder()
                .totalSubmissions(total)
                .pendingReceipts(pending)
                .acceptedSubmissions(accepted)
                .rejectedSubmissions(rejected)
                .failedSubmissions(failed)
                .resubmissions(resubmissions)
                .acceptanceRate(Math.round(rate * 10D) / 10D)
                .build();
    }

    @Override
    public PageResult<RegulatorySubmission> page(RegulatorySubmissionQuery query) {
        LambdaQueryWrapper<RegulatorySubmission> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(item -> item.like(RegulatorySubmission::getSubmissionNo, query.getKeyword())
                    .or().like(RegulatorySubmission::getReportNo, query.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(query.getReportType()), RegulatorySubmission::getReportType, query.getReportType())
                .eq(StringUtils.hasText(query.getStatus()), RegulatorySubmission::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getReceiptStatus()), RegulatorySubmission::getReceiptStatus, query.getReceiptStatus())
                .eq(query.getConnectorId() != null, RegulatorySubmission::getConnectorId, query.getConnectorId())
                .orderByDesc(RegulatorySubmission::getCreatedTime);
        IPage<RegulatorySubmission> page = submissionMapper.selectPage(query.toPage(), wrapper);
        enrichConnectors(page.getRecords());
        return PageResult.from(page);
    }

    @Override
    public RegulatorySubmissionDetailVO detail(Long id) {
        RegulatorySubmission submission = loadSubmission(id);
        enrichConnectors(List.of(submission));
        List<RegulatoryReceipt> receipts = receiptMapper.selectList(new LambdaQueryWrapper<RegulatoryReceipt>()
                .eq(RegulatoryReceipt::getSubmissionId, id)
                .orderByAsc(RegulatoryReceipt::getReceivedTime));
        return RegulatorySubmissionDetailVO.builder().submission(submission).receipts(receipts).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulatorySubmission submitInitial(String reportType, Long reportId, Long connectorId) {
        String normalizedType = normalizeReportType(reportType);
        ReportPayload payload = loadPayload(normalizedType, reportId, true, null);
        RegulatorySubmission latest = latestSubmission(normalizedType, reportId);
        if (latest != null) {
            if (ACCEPTED.equals(latest.getStatus())) {
                throw new BusinessException(ResultCode.REPORT_ALREADY_ACCEPTED, "该报告已获得监管受理回执");
            }
            throw new BusinessException(ResultCode.REPORT_RESUBMIT_REQUIRED,
                    "该报告已有报送记录，请从原记录发起重报");
        }
        IntegrationConnector connector = resolveConnector(connectorId);
        return execute(payload, connector, 1, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulatorySubmission resubmit(Long originalSubmissionId, RegulatoryResubmitRequest request) {
        RegulatorySubmission original = loadSubmission(originalSubmissionId);
        if (!REJECTED.equals(original.getStatus()) && !FAILED.equals(original.getStatus())) {
            throw new BusinessException(ResultCode.REPORT_RESUBMIT_NOT_ALLOWED, "仅退回或失败的报送允许重报");
        }
        ReportPayload payload = loadPayload(original.getReportType(), original.getReportId(), false,
                request.getCorrectedPayload());
        IntegrationConnector connector = resolveConnector(
                request.getConnectorId() == null ? original.getConnectorId() : request.getConnectorId());
        return execute(payload, connector, original.getVersionNo() + 1, original.getId(), request.getCorrectionNote());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulatorySubmission pollReceipt(Long submissionId) {
        RegulatorySubmission submission = loadSubmission(submissionId);
        if (!SUBMITTED.equals(submission.getStatus()) || !StringUtils.hasText(submission.getExternalRequestId())) {
            throw new BusinessException(ResultCode.REPORT_RECEIPT_NOT_PENDING, "当前报送没有待查询回执");
        }
        IntegrationConnector connector = loadConnector(submission.getConnectorId());
        RegulatoryGatewayResult result = gatewayRegistry.resolve(connector.getTransportType())
                .queryReceipt(connector, submission.getExternalRequestId());
        applyGatewayResult(submission, result, "POLL");
        submissionMapper.updateById(submission);
        updateSourceReport(submission);
        return enrich(submission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegulatorySubmission applyReceipt(Long submissionId, RegulatoryReceiptRequest request, String source) {
        RegulatorySubmission submission = loadSubmission(submissionId);
        String receiptStatus = normalizeReceiptStatus(request.getReceiptStatus());
        RegulatoryGatewayResult result = RegulatoryGatewayResult.builder()
                .transmitted(true)
                .externalRequestId(submission.getExternalRequestId())
                .receiptStatus(receiptStatus)
                .receiptNo(request.getReceiptNo())
                .receiptCode(request.getReceiptCode())
                .receiptMessage(request.getReceiptMessage())
                .receiptPayload(request.getReceiptPayload())
                .build();
        applyGatewayResult(submission, result, StringUtils.hasText(source) ? source : "CALLBACK");
        submissionMapper.updateById(submission);
        updateSourceReport(submission);
        return enrich(submission);
    }

    private RegulatorySubmission execute(ReportPayload payload, IntegrationConnector connector, int version,
                                         Long parentId, String correctionNote) {
        RegulatorySubmission submission = new RegulatorySubmission();
        submission.setSubmissionNo(idGenerator.generate("SUB"));
        submission.setReportType(payload.reportType());
        submission.setReportId(payload.reportId());
        submission.setReportNo(payload.reportNo());
        submission.setVersionNo(version);
        submission.setParentSubmissionId(parentId);
        submission.setConnectorId(connector.getId());
        submission.setStatus("PREPARING");
        submission.setSchemaVersion("AML-MIS-LOCAL-1.0");
        submission.setPayloadFormat("XML");
        submission.setCorrectionNote(correctionNote);
        submission.setRetryCount(Math.max(0, version - 1));
        submissionMapper.insert(submission);

        String stage = "GENERATE";
        try {
            String xml = payload.xml();
            submission.setPayloadContent(xml);
            stage = "VALIDATE";
            RegulatoryValidationResult validation = xmlValidator.validate(payload.reportType(), xml);
            if (!validation.valid()) {
                throw new BusinessException(ResultCode.REPORT_VALIDATION_FAIL, validation.summary());
            }
            stage = "SIGN";
            RegulatorySignature signature = payloadSigner.sign(xml, connector);
            submission.setPayloadHash(signature.payloadHash());
            submission.setSignatureAlgorithm(signature.algorithm());
            submission.setSignatureValue(signature.signatureValue());

            stage = "TRANSMIT";
            submission.setSubmittedBy(currentUsername());
            submission.setSubmittedTime(LocalDateTime.now());
            RegulatorySubmissionEnvelope envelope = RegulatorySubmissionEnvelope.builder()
                    .submissionNo(submission.getSubmissionNo())
                    .reportType(submission.getReportType())
                    .reportNo(submission.getReportNo())
                    .versionNo(submission.getVersionNo())
                    .schemaVersion(submission.getSchemaVersion())
                    .payload(xml)
                    .payloadHash(submission.getPayloadHash())
                    .signatureAlgorithm(submission.getSignatureAlgorithm())
                    .signatureValue(submission.getSignatureValue())
                    .build();
            RegulatoryGatewayAdapter adapter = gatewayRegistry.resolve(connector.getTransportType());
            RegulatoryGatewayResult result = adapter.submit(connector, envelope);
            applyGatewayResult(submission, result, "GATEWAY");
        } catch (Exception exception) {
            submission.setStatus(FAILED);
            submission.setFailureStage(stage);
            submission.setErrorMessage(message(exception));
            submission.setCompletedTime(LocalDateTime.now());
            log.warn("监管报送失败，submissionNo={}, stage={}, error={}",
                    submission.getSubmissionNo(), stage, submission.getErrorMessage());
        }
        submissionMapper.updateById(submission);
        updateSourceReport(submission);
        writeLegacySubmitLog(submission);
        return enrich(submission);
    }

    private void applyGatewayResult(RegulatorySubmission submission, RegulatoryGatewayResult result, String source) {
        if (!result.isTransmitted()) {
            submission.setStatus(FAILED);
            submission.setFailureStage("TRANSMIT");
            submission.setErrorMessage(result.getErrorMessage());
            submission.setCompletedTime(LocalDateTime.now());
            return;
        }
        submission.setExternalRequestId(result.getExternalRequestId());
        String receiptStatus = normalizeReceiptStatus(result.getReceiptStatus());
        submission.setReceiptStatus(receiptStatus);
        submission.setReceiptNo(result.getReceiptNo());
        submission.setReceiptTime(LocalDateTime.now());
        submission.setReturnCode(result.getReceiptCode());
        submission.setReturnMessage(result.getReceiptMessage());
        submission.setStatus(ACCEPTED.equals(receiptStatus) ? ACCEPTED
                : REJECTED.equals(receiptStatus) ? REJECTED : SUBMITTED);
        if (!PENDING.equals(receiptStatus)) {
            submission.setCompletedTime(LocalDateTime.now());
        }
        saveReceipt(submission, result, source);
    }

    private void saveReceipt(RegulatorySubmission submission, RegulatoryGatewayResult result, String source) {
        RegulatoryReceipt receipt = new RegulatoryReceipt();
        receipt.setSubmissionId(submission.getId());
        receipt.setReceiptNo(result.getReceiptNo());
        receipt.setReceiptStatus(normalizeReceiptStatus(result.getReceiptStatus()));
        receipt.setReceiptCode(result.getReceiptCode());
        receipt.setReceiptMessage(result.getReceiptMessage());
        receipt.setReceiptPayload(result.getReceiptPayload());
        receipt.setReceivedTime(LocalDateTime.now());
        receipt.setReceiptSource(source);
        receiptMapper.insert(receipt);
    }

    private ReportPayload loadPayload(String reportType, Long reportId, boolean enforceInitialStatus,
                                      String correctedPayload) {
        if (LARGE_TXN.equals(reportType)) {
            LargeTxnReport report = largeTxnReportMapper.selectById(reportId);
            if (report == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "大额交易报告不存在");
            }
            if (enforceInitialStatus && !ReportStatus.REVIEWED.getCode().equals(report.getReportStatus())) {
                throw new BusinessException(ResultCode.REPORT_STATUS_INVALID, "只有已审核的大额交易报告才能报送");
            }
            Transaction transaction = transactionMapper.selectById(report.getTransactionId());
            Customer customer = customerMapper.selectById(report.getCustomerId());
            if (transaction == null || customer == null) {
                throw new BusinessException(ResultCode.REPORT_VALIDATION_FAIL, "报告关联的客户或交易不存在");
            }
            String xml = StringUtils.hasText(correctedPayload) ? correctedPayload
                    : xmlGeneratorService.generateLargeTxnXml(report, customer, transaction);
            return new ReportPayload(LARGE_TXN, reportId, report.getReportNo(), xml);
        }
        StrReport report = strReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "可疑交易报告不存在");
        }
        if (enforceInitialStatus && !ReportStatus.APPROVED.getCode().equals(report.getReportStatus())) {
            throw new BusinessException(ResultCode.REPORT_STATUS_INVALID, "只有已批准的可疑交易报告才能报送");
        }
        String xml = StringUtils.hasText(correctedPayload) ? correctedPayload
                : xmlGeneratorService.generateSuspiciousTxnXml(report);
        return new ReportPayload(SUSPICIOUS, reportId, report.getReportNo(), xml);
    }

    private void updateSourceReport(RegulatorySubmission submission) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", submission.getStatus());
        response.put("submissionNo", submission.getSubmissionNo());
        response.put("versionNo", submission.getVersionNo());
        response.put("receiptNo", submission.getReceiptNo());
        response.put("returnCode", submission.getReturnCode());
        response.put("message", submission.getReturnMessage() == null
                ? submission.getErrorMessage() : submission.getReturnMessage());
        String responseJson = json(response);
        if (LARGE_TXN.equals(submission.getReportType())) {
            LargeTxnReport report = largeTxnReportMapper.selectById(submission.getReportId());
            if (report == null) {
                return;
            }
            if (ACCEPTED.equals(submission.getStatus())) {
                report.setReportStatus(submission.getVersionNo() > 1 ? "RESUBMITTED" : ReportStatus.SUBMITTED.getCode());
            } else if (REJECTED.equals(submission.getStatus()) || FAILED.equals(submission.getStatus())) {
                report.setReportStatus(FAILED);
            } else if (SUBMITTED.equals(submission.getStatus())) {
                report.setReportStatus(ReportStatus.SUBMITTED.getCode());
            }
            report.setXmlContent(submission.getPayloadContent());
            report.setSubmitResponse(responseJson);
            report.setSubmittedBy(submission.getSubmittedBy());
            report.setSubmittedTime(submission.getSubmittedTime());
            report.setUpdatedTime(LocalDateTime.now());
            largeTxnReportMapper.updateById(report);
            return;
        }
        StrReport report = strReportMapper.selectById(submission.getReportId());
        if (report == null) {
            return;
        }
        if (ACCEPTED.equals(submission.getStatus()) || SUBMITTED.equals(submission.getStatus())) {
            report.setReportStatus(ReportStatus.SUBMITTED.getCode());
        } else {
            report.setReportStatus(ReportStatus.APPROVED.getCode());
        }
        report.setSubmitResult(responseJson);
        report.setSubmitTime(submission.getSubmittedTime());
        report.setUpdatedTime(LocalDateTime.now());
        strReportMapper.updateById(report);
    }

    private void writeLegacySubmitLog(RegulatorySubmission submission) {
        ReportSubmitLog logEntity = new ReportSubmitLog();
        logEntity.setReportType(submission.getReportType());
        logEntity.setReportId(submission.getReportId());
        logEntity.setSubmissionId(submission.getId());
        logEntity.setSubmitTime(Objects.requireNonNullElse(submission.getSubmittedTime(), LocalDateTime.now()));
        logEntity.setSubmitStatus(FAILED.equals(submission.getStatus()) || REJECTED.equals(submission.getStatus())
                ? SubmitStatus.FAILED.getCode() : SubmitStatus.SUCCESS.getCode());
        logEntity.setRequestData(submission.getPayloadContent());
        logEntity.setResponseData(json(Map.of(
                "status", submission.getStatus(),
                "submissionNo", submission.getSubmissionNo(),
                "receiptStatus", Objects.requireNonNullElse(submission.getReceiptStatus(), ""))));
        logEntity.setErrorMessage(submission.getErrorMessage());
        logEntity.setRetryCount(submission.getRetryCount());
        logEntity.setMaxRetries(3);
        logEntity.setExternalRequestId(submission.getExternalRequestId());
        logEntity.setReceiptNo(submission.getReceiptNo());
        logEntity.setCreatedTime(LocalDateTime.now());
        submitLogMapper.insert(logEntity);
    }

    private IntegrationConnector resolveConnector(Long connectorId) {
        if (connectorId != null) {
            IntegrationConnector connector = loadConnector(connectorId);
            validateConnector(connector);
            return connector;
        }
        List<IntegrationConnector> connectors = connectorMapper.selectList(
                new LambdaQueryWrapper<IntegrationConnector>()
                        .eq(IntegrationConnector::getBusinessType, "REGULATORY_REPORTING")
                        .eq(IntegrationConnector::getStatus, "ENABLED"));
        return connectors.stream()
                .sorted((left, right) -> Boolean.compare(!"HEALTHY".equals(left.getHealthStatus()),
                        !"HEALTHY".equals(right.getHealthStatus())))
                .findFirst()
                .map(connector -> {
                    validateConnector(connector);
                    return connector;
                })
                .orElseThrow(() -> new BusinessException(ResultCode.REPORT_CONNECTOR_NOT_CONFIGURED,
                        "未配置启用的监管报送连接器"));
    }

    private IntegrationConnector loadConnector(Long id) {
        IntegrationConnector connector = connectorMapper.selectById(id);
        if (connector == null) {
            throw new BusinessException(ResultCode.INTEGRATION_CONNECTOR_NOT_FOUND, "连接器不存在");
        }
        return connector;
    }

    private void validateConnector(IntegrationConnector connector) {
        if (!"REGULATORY_REPORTING".equals(connector.getBusinessType())) {
            throw new BusinessException(ResultCode.REPORT_CONNECTOR_NOT_CONFIGURED, "所选连接器不是监管报送类型");
        }
        if (!"ENABLED".equals(connector.getStatus())) {
            throw new BusinessException(ResultCode.INTEGRATION_CONNECTOR_DISABLED, "监管报送连接器已停用");
        }
    }

    private RegulatorySubmission latestSubmission(String reportType, Long reportId) {
        return submissionMapper.selectOne(new LambdaQueryWrapper<RegulatorySubmission>()
                .eq(RegulatorySubmission::getReportType, reportType)
                .eq(RegulatorySubmission::getReportId, reportId)
                .orderByDesc(RegulatorySubmission::getVersionNo)
                .last("LIMIT 1"));
    }

    private RegulatorySubmission loadSubmission(Long id) {
        RegulatorySubmission submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw new BusinessException(ResultCode.REPORT_SUBMISSION_NOT_FOUND, "监管报送记录不存在");
        }
        return submission;
    }

    private void enrichConnectors(List<RegulatorySubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return;
        }
        Map<Long, String> names = connectorMapper.selectBatchIds(submissions.stream()
                        .map(RegulatorySubmission::getConnectorId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(IntegrationConnector::getId,
                        IntegrationConnector::getConnectorName, (left, right) -> left));
        submissions.forEach(item -> item.setConnectorName(names.get(item.getConnectorId())));
    }

    private RegulatorySubmission enrich(RegulatorySubmission submission) {
        enrichConnectors(List.of(submission));
        return submission;
    }

    private long countStatus(String status) {
        return submissionMapper.selectCount(new LambdaQueryWrapper<RegulatorySubmission>()
                .eq(RegulatorySubmission::getStatus, status));
    }

    private String normalizeReportType(String reportType) {
        String normalized = reportType == null ? "" : reportType.trim().toUpperCase(Locale.ROOT);
        if (!LARGE_TXN.equals(normalized) && !SUSPICIOUS.equals(normalized)) {
            throw new BusinessException(ResultCode.REPORT_TYPE_UNSUPPORTED, "仅支持大额交易和可疑交易报告");
        }
        return normalized;
    }

    private String normalizeReceiptStatus(String status) {
        String normalized = status == null ? PENDING : status.trim().toUpperCase(Locale.ROOT);
        if (!ACCEPTED.equals(normalized) && !REJECTED.equals(normalized) && !PENDING.equals(normalized)) {
            throw new BusinessException(ResultCode.REPORT_RECEIPT_INVALID, "回执状态必须为 PENDING、ACCEPTED 或 REJECTED");
        }
        return normalized;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "system";
    }

    private String message(Exception exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"status\":\"SERIALIZATION_FAILED\"}";
        }
    }

    private record ReportPayload(String reportType, Long reportId, String reportNo, String xml) {
    }
}
