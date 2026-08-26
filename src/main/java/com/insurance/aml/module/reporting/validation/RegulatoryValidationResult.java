package com.insurance.aml.module.reporting.validation;

import java.util.List;

public record RegulatoryValidationResult(boolean valid, List<String> errors) {
    public String summary() {
        return String.join("；", errors);
    }
}
