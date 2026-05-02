package com.insurance.aml.module.reporting.service;

import com.insurance.aml.module.reporting.model.entity.LargeTxnReport;
import com.insurance.aml.module.monitoring.model.entity.Transaction;
import com.insurance.aml.module.kyc.model.entity.Customer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * XML报文生成服务
 * 生成符合人民银行反洗钱监测分析系统要求的XML报文
 */
@Slf4j
@Service
public class XmlGeneratorService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成大额交易报告XML报文
     * 符合人民银行大额交易报告数据接口规范
     *
     * @param report 大额交易报告
     * @param customer 客户信息
     * @param transaction 交易信息
     * @return XML字符串
     */
    public String generateLargeTxnXml(LargeTxnReport report, Customer customer, Transaction transaction) {
        log.info("开始生成大额交易报告XML，报告编号：{}", report.getReportNo());

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<LargeTransactionReport>\n");

        // 报告头部 - 机构信息和报告元数据
        xml.append("  <Header>\n");
        xml.append("    <ReportNo>").append(escapeXml(report.getReportNo())).append("</ReportNo>\n");
        xml.append("    <ReportDate>").append(report.getReportDate() != null ? report.getReportDate().format(DATE_FMT) : "").append("</ReportDate>\n");
        xml.append("    <ReportType>LARGE_TXN</ReportType>\n");
        xml.append("    <InstitutionCode>INS001</InstitutionCode>\n");
        xml.append("    <InstitutionName>保险机构</InstitutionName>\n");
        xml.append("  </Header>\n");

        // 客户信息
        xml.append("  <CustomerInfo>\n");
        xml.append("    <CustomerId>").append(customer.getId()).append("</CustomerId>\n");
        xml.append("    <CustomerName>").append(escapeXml(customer.getName())).append("</CustomerName>\n");
        xml.append("    <CustomerType>").append(escapeXml(customer.getCustomerType())).append("</CustomerType>\n");
        xml.append("    <IdType>").append(escapeXml(customer.getIdType())).append("</IdType>\n");
        xml.append("    <IdNumber>").append(escapeXml(customer.getIdNumber())).append("</IdNumber>\n");
        xml.append("    <Nationality>").append(escapeXml(customer.getNationality())).append("</Nationality>\n");
        xml.append("    <Address>").append(escapeXml(customer.getAddress())).append("</Address>\n");
        xml.append("    <Phone>").append(escapeXml(customer.getPhone())).append("</Phone>\n");
        xml.append("  </CustomerInfo>\n");

        // 交易信息
        xml.append("  <TransactionInfo>\n");
        xml.append("    <TransactionId>").append(transaction.getId()).append("</TransactionId>\n");
        xml.append("    <TransactionNo>").append(escapeXml(transaction.getTransactionNo())).append("</TransactionNo>\n");
        xml.append("    <TransactionTime>").append(transaction.getTransactionTime() != null ? transaction.getTransactionTime().format(DATETIME_FMT) : "").append("</TransactionTime>\n");
        xml.append("    <TransactionType>").append(escapeXml(transaction.getTransactionType())).append("</TransactionType>\n");
        xml.append("    <Amount>").append(transaction.getAmount()).append("</Amount>\n");
        xml.append("    <Currency>").append(escapeXml(transaction.getCurrency())).append("</Currency>\n");
        xml.append("    <PaymentMethod>").append(escapeXml(transaction.getPaymentMethod())).append("</PaymentMethod>\n");
        xml.append("    <CounterpartyName>").append(escapeXml(transaction.getCounterpartyName())).append("</CounterpartyName>\n");
        xml.append("    <CounterpartyAccount>").append(escapeXml(transaction.getCounterpartyAccount())).append("</CounterpartyAccount>\n");
        xml.append("    <CounterpartyBank>").append(escapeXml(transaction.getCounterpartyBank())).append("</CounterpartyBank>\n");
        xml.append("    <IsCrossBorder>").append(Boolean.TRUE.equals(transaction.getIsCrossBorder()) ? "Y" : "N").append("</IsCrossBorder>\n");
        xml.append("  </TransactionInfo>\n");

        // 报告附加信息
        xml.append("  <ReportExtra>\n");
        xml.append("    <CounterpartyInfo>").append(escapeXml(report.getCounterpartyInfo())).append("</CounterpartyInfo>\n");
        xml.append("  </ReportExtra>\n");

        xml.append("</LargeTransactionReport>");

        log.info("大额交易报告XML生成完成，报告编号：{}", report.getReportNo());
        return xml.toString();
    }

    /**
     * 生成可疑交易报告XML报文（占位方法）
     * TODO: 待可疑交易报告实体定义完成后实现
     *
     * @param reportData 报告数据（JSON格式）
     * @return XML字符串
     */
    public String generateSuspiciousTxnXml(String reportData) {
        log.warn("可疑交易报告XML生成功能暂未实现");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<SuspiciousTransactionReport>\n  <!-- TODO: 待实现 -->\n</SuspiciousTransactionReport>";
    }

    /**
     * XML特殊字符转义
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
