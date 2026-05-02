package com.insurance.aml.module.reporting.service;

import com.insurance.aml.module.alert.model.dto.AlertVO;
import com.insurance.aml.module.kyc.model.dto.CustomerVO;
import com.insurance.aml.module.monitoring.model.dto.TransactionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据导出服务（CSV格式）
 */
@Service
@Slf4j
public class ExportService {

    /**
     * 导出告警数据为CSV
     */
    public byte[] exportAlertsToExcel(List<AlertVO> alerts) {
        log.info("导出告警数据，数量: {}", alerts.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        // 写入BOM头，Excel可正确识别UTF-8
        writer.print('\ufeff');
        // CSV表头
        writer.println("告警ID,告警类型,告警状态,客户ID,描述,创建时间");

        for (AlertVO alert : alerts) {
            writer.println(String.join(",",
                    escapeCsv(String.valueOf(alert.getId())),
                    escapeCsv(alert.getAlertType()),
                    escapeCsv(alert.getStatus()),
                    escapeCsv(String.valueOf(alert.getCustomerId())),
                    escapeCsv(alert.getAlertSummary()),
                    escapeCsv(alert.getCreatedTime() != null ? alert.getCreatedTime().toString() : "")
            ));
        }

        writer.flush();
        return out.toByteArray();
    }

    /**
     * 导出交易数据为CSV
     */
    public byte[] exportTransactionsToExcel(List<TransactionVO> transactions) {
        log.info("导出交易数据，数量: {}", transactions.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.print('\ufeff');
        writer.println("交易ID,客户ID,交易类型,交易金额,交易时间,交易状态");

        for (TransactionVO txn : transactions) {
            writer.println(String.join(",",
                    escapeCsv(String.valueOf(txn.getId())),
                    escapeCsv(String.valueOf(txn.getCustomerId())),
                    escapeCsv(txn.getTransactionType()),
                    escapeCsv(txn.getAmount() != null ? txn.getAmount().toString() : "0"),
                    escapeCsv(txn.getTransactionTime() != null ? txn.getTransactionTime().toString() : ""),
                    escapeCsv(txn.getStatus())
            ));
        }

        writer.flush();
        return out.toByteArray();
    }

    /**
     * 导出客户数据为CSV
     */
    public byte[] exportCustomersToExcel(List<CustomerVO> customers) {
        log.info("导出客户数据，数量: {}", customers.size());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

        writer.print('\ufeff');
        writer.println("客户ID,客户名称,证件号码,风险等级,KYC状态,创建时间");

        for (CustomerVO customer : customers) {
            writer.println(String.join(",",
                    escapeCsv(String.valueOf(customer.getId())),
                    escapeCsv(customer.getName()),
                    escapeCsv(customer.getIdNumber()),
                    escapeCsv(customer.getRiskLevel()),
                    escapeCsv(customer.getKycStatus()),
                    escapeCsv(customer.getCreatedTime() != null ? customer.getCreatedTime().toString() : "")
            ));
        }

        writer.flush();
        return out.toByteArray();
    }

    /**
     * CSV字段转义
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
