package com.insurance.aml.module.integration.service;

import com.insurance.aml.common.result.PageResult;
import com.insurance.aml.module.integration.model.dto.IntegrationConnectorQuery;
import com.insurance.aml.module.integration.model.dto.IntegrationConnectorRequest;
import com.insurance.aml.module.integration.model.dto.IntegrationJobQuery;
import com.insurance.aml.module.integration.model.dto.IntegrationJobRequest;
import com.insurance.aml.module.integration.model.dto.IntegrationOverviewVO;
import com.insurance.aml.module.integration.model.dto.IntegrationRunQuery;
import com.insurance.aml.module.integration.model.entity.IntegrationConnector;
import com.insurance.aml.module.integration.model.entity.IntegrationJob;
import com.insurance.aml.module.integration.model.entity.IntegrationRun;

import java.util.List;

public interface IntegrationService {
    IntegrationOverviewVO overview();

    PageResult<IntegrationConnector> pageConnectors(IntegrationConnectorQuery query);

    List<IntegrationConnector> listEnabledConnectors();

    IntegrationConnector createConnector(IntegrationConnectorRequest request);

    IntegrationConnector updateConnector(Long id, IntegrationConnectorRequest request);

    IntegrationRun testConnection(Long connectorId);

    PageResult<IntegrationJob> pageJobs(IntegrationJobQuery query);

    IntegrationJob createJob(IntegrationJobRequest request);

    IntegrationJob updateJob(Long id, IntegrationJobRequest request);

    IntegrationRun triggerJob(Long jobId);

    IntegrationRun retryRun(Long runId);

    PageResult<IntegrationRun> pageRuns(IntegrationRunQuery query);

    IntegrationRun getRun(Long runId);

    void runDueJobs();
}
