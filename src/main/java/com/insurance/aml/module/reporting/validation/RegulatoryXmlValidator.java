package com.insurance.aml.module.reporting.validation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报送前XML安全解析与最小业务规则校验。正式监管XSD可在适配器落地时替换规则集。
 */
@Component
public class RegulatoryXmlValidator {
    private static final Map<String, List<String>> REQUIRED_TAGS = Map.of(
            "LARGE_TXN", List.of("ReportNo", "ReportDate", "InstitutionCode", "CustomerId",
                    "TransactionId", "Amount", "Currency"),
            "SUSPICIOUS", List.of("ReportNo", "InstitutionCode", "CaseId", "CustomerId",
                    "Content", "AnalysisOpinion")
    );

    public RegulatoryValidationResult validate(String reportType, String xml) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(xml)) {
            return new RegulatoryValidationResult(false, List.of("XML报文不能为空"));
        }
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXParseException {
                    throw exception;
                }
            });
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            List<String> requiredTags = REQUIRED_TAGS.get(reportType);
            if (requiredTags == null) {
                return new RegulatoryValidationResult(false, List.of("不支持的报告类型：" + reportType));
            }
            requiredTags.forEach(tag -> {
                if (!StringUtils.hasText(text(document, tag))) {
                    errors.add("缺少必填字段 " + tag);
                }
            });
            if ("LARGE_TXN".equals(reportType) && StringUtils.hasText(text(document, "Amount"))) {
                try {
                    if (new BigDecimal(text(document, "Amount")).signum() <= 0) {
                        errors.add("交易金额必须大于0");
                    }
                } catch (NumberFormatException exception) {
                    errors.add("交易金额格式不正确");
                }
            }
        } catch (Exception exception) {
            errors.add("XML格式错误或包含不安全结构");
        }
        return new RegulatoryValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private String text(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }
}
