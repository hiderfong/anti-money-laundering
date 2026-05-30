<template>
  <div class="model-page">
    <section class="page-heading">
      <div>
        <h1>模型管理</h1>
        <p>覆盖反洗钱模型创建、测试、部署、监控、迭代与归档的全生命周期治理</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>新增模型
      </el-button>
    </section>

    <div class="metric-grid">
      <div class="metric-card">
        <span>模型总数</span>
        <strong>{{ overview.totalModels }}</strong>
      </div>
      <div class="metric-card">
        <span>草稿</span>
        <strong>{{ overview.draftModels }}</strong>
      </div>
      <div class="metric-card warning">
        <span>测试中/已测试</span>
        <strong>{{ overview.testingModels }}</strong>
      </div>
      <div class="metric-card success">
        <span>已部署</span>
        <strong>{{ overview.deployedModels }}</strong>
      </div>
      <div class="metric-card">
        <span>监控中</span>
        <strong>{{ overview.monitoringModels }}</strong>
      </div>
      <div class="metric-card warning">
        <span>需关注</span>
        <strong>{{ overview.attentionModels }}</strong>
      </div>
    </div>

    <section class="visual-grid">
      <div class="chart-card">
        <div class="chart-card-header">
          <div>
            <h2>模型生命周期分布</h2>
            <p>按草稿、测试、部署、监控、迭代、归档展示治理进度</p>
          </div>
          <el-tag type="info" size="small">生命周期</el-tag>
        </div>
        <div ref="lifecycleChartRef" class="chart-box"></div>
      </div>

      <div class="chart-card">
        <div class="chart-card-header">
          <div>
            <h2>模型运行健康矩阵</h2>
            <p>横轴准确率、纵轴召回率，气泡越大表示误报或漂移越需关注</p>
          </div>
          <el-tag type="warning" size="small">监控指标</el-tag>
        </div>
        <div ref="healthChartRef" class="chart-box"></div>
      </div>
    </section>

    <section class="ai-review-panel">
      <div class="ai-review-header">
        <div>
          <h2>AI评分监控与待复核池</h2>
          <p>暂无专职合规复核时，系统基于处置链路生成弱标签，用于风险排序和后续批量抽查</p>
        </div>
        <div class="ai-review-header-actions">
          <el-button :loading="aiReviewExporting" @click="exportAiReviewPool">
            <el-icon><Download /></el-icon>导出清单
          </el-button>
          <el-button @click="refreshAiReview">
            <el-icon><Refresh /></el-icon>刷新监控
          </el-button>
        </div>
      </div>

      <div class="ai-review-metrics">
        <div class="ai-review-metric danger">
          <span>高风险及以上</span>
          <strong>{{ toNumber(aiReviewOverview.highOrCriticalCount) }}</strong>
        </div>
        <div class="ai-review-metric warning">
          <span>待延后复核</span>
          <strong>{{ toNumber(aiReviewOverview.pendingReviewCount) }}</strong>
        </div>
        <div class="ai-review-metric success">
          <span>疑似有效风险</span>
          <strong>{{ toNumber(aiReviewOverview.likelyTruePositiveCount) }}</strong>
        </div>
        <div class="ai-review-metric">
          <span>尚未确认</span>
          <strong>{{ toNumber(aiReviewOverview.unconfirmedCount) }}</strong>
        </div>
        <div class="ai-review-metric danger">
          <span>高分无规则印证</span>
          <strong>{{ toNumber(aiReviewOverview.highScoreNoRuleHitCount) }}</strong>
        </div>
      </div>

      <div class="ai-review-toolbar">
        <el-select v-model="aiReviewQuery.subjectType" placeholder="主体类型" clearable @change="loadAiReviewPool">
          <el-option label="客户" value="CUSTOMER" />
          <el-option label="交易" value="TRANSACTION" />
          <el-option label="预警" value="ALERT" />
        </el-select>
        <el-select v-model="aiReviewQuery.riskLevel" placeholder="风险等级" clearable @change="loadAiReviewPool">
          <el-option v-for="item in aiRiskLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="aiReviewQuery.autoLabel" placeholder="系统弱标签" clearable @change="loadAiReviewPool">
          <el-option label="疑似有效风险" value="LIKELY_TRUE_POSITIVE" />
          <el-option label="疑似误报" value="LIKELY_FALSE_POSITIVE" />
          <el-option label="尚未确认" value="UNCONFIRMED" />
        </el-select>
        <el-input-number v-model="aiReviewQuery.minScore" :min="0" :max="100" :step="5" controls-position="right" @change="loadAiReviewPool" />
        <el-checkbox v-model="aiReviewQuery.pendingOnly" @change="loadAiReviewPool">仅待复核</el-checkbox>
      </div>

      <el-table :data="aiReviewItems" v-loading="aiReviewLoading" stripe border style="width: 100%">
        <el-table-column prop="priorityLevel" label="优先级" width="86" fixed="left">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priorityLevel)" size="small">{{ row.priorityLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主体" min-width="210" fixed="left">
          <template #default="{ row }">
            <div class="ai-subject-cell">
              <strong>{{ row.subjectName || '-' }}</strong>
              <span>{{ subjectTypeText(row.subjectType) }} · {{ row.subjectId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="AI风险分" width="130">
          <template #default="{ row }">
            <div class="ai-score-cell">
              <strong>{{ row.score }}</strong>
              <el-progress :percentage="Number(row.score || 0)" :show-text="false" :color="scoreColor(row.score)" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="110">
          <template #default="{ row }">
            <el-tag :type="aiRiskType(row.riskLevel)" size="small">{{ aiRiskLevelText(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="autoLabelText" label="系统弱标签" width="130">
          <template #default="{ row }">
            <el-tag :type="autoLabelType(row.autoLabel)" size="small">{{ row.autoLabelText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewStatus" label="复核状态" width="130">
          <template #default="{ row }">
            <el-tag :type="reviewStatusType(row.reviewStatus)" size="small">{{ reviewStatusText(row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="manualReviewLabelText" label="人工确认" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.manualReviewLabel" :type="manualReviewLabelType(row.manualReviewLabel)" size="small">
              {{ row.manualReviewLabelText }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="跟进任务" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.followUpTaskId" type="success" size="small">已生成 #{{ row.followUpTaskId }}</el-tag>
            <el-tag v-else type="warning" size="small">待生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="verificationBasis" label="系统判断依据" min-width="260" show-overflow-tooltip />
        <el-table-column prop="factorSummary" label="主要贡献因子" min-width="280" show-overflow-tooltip />
        <el-table-column label="模型版本" width="120">
          <template #default="{ row }">v{{ row.modelVersion }}</template>
        </el-table-column>
        <el-table-column prop="scoredAt" label="评分时间" min-width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" size="small" @click="openAiReview(row)">
                {{ row.manualReviewLabel ? '更新复核' : '登记复核' }}
              </el-button>
              <el-button
                link
                type="warning"
                size="small"
                :disabled="Boolean(row.followUpTaskId)"
                @click="openAiFollowUp(row)"
              >
                {{ row.followUpTaskId ? '已生成任务' : '生成任务' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="aiReviewQuery.page"
        v-model:page-size="aiReviewQuery.size"
        :total="aiReviewTotal"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="ai-review-pagination"
        @current-change="loadAiReviewPool"
        @size-change="aiReviewQuery.page = 1; loadAiReviewPool()"
      />
    </section>

    <section class="table-panel">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="模型编码 / 名称 / 责任人" clearable @keyup.enter="loadModels" />
        <el-select v-model="query.modelType" placeholder="模型类型" clearable @change="loadModels">
          <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.scenario" placeholder="业务场景" clearable @change="loadModels">
          <el-option v-for="item in scenarioOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.lifecycleStatus" placeholder="生命周期" clearable @change="loadModels">
          <el-option v-for="item in lifecycleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="loadModels"><el-icon><Refresh /></el-icon>查询</el-button>
      </div>

      <el-table :data="models" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="modelCode" label="模型编码" min-width="150" fixed="left" />
        <el-table-column prop="modelName" label="模型名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="modelType" label="类型" width="120">
          <template #default="{ row }">{{ optionLabel(modelTypeOptions, row.modelType) }}</template>
        </el-table-column>
        <el-table-column prop="scenario" label="场景" min-width="160">
          <template #default="{ row }">{{ optionLabel(scenarioOptions, row.scenario) }}</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column prop="lifecycleStatus" label="生命周期" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.lifecycleStatus)" size="small">{{ optionLabel(lifecycleOptions, row.lifecycleStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="monitorStatus" label="监控状态" width="120">
          <template #default="{ row }">
            <el-tag :type="monitorType(row.monitorStatus)" size="small">{{ optionLabel(monitorOptions, row.monitorStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="110">
          <template #default="{ row }">
            <el-tag :type="riskType(row.riskLevel)" size="small">{{ optionLabel(riskOptions, row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="owner" label="责任人" width="130" />
        <el-table-column label="准确率" width="110">
          <template #default="{ row }">{{ formatPercent(row.precisionRate) }}</template>
        </el-table-column>
        <el-table-column label="召回率" width="110">
          <template #default="{ row }">{{ formatPercent(row.recallRate) }}</template>
        </el-table-column>
        <el-table-column label="误报率" width="110">
          <template #default="{ row }">{{ formatPercent(row.falsePositiveRate) }}</template>
        </el-table-column>
        <el-table-column label="漂移分数" width="110">
          <template #default="{ row }">{{ formatScore(row.driftScore) }}</template>
        </el-table-column>
        <el-table-column prop="deploymentEnv" label="部署环境" width="100" />
        <el-table-column prop="updatedTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="success" size="small" @click="openLifecycle(row, 'test')">测试</el-button>
              <el-button link type="warning" size="small" @click="openLifecycle(row, 'deploy')">部署</el-button>
              <el-button link type="info" size="small" @click="openLifecycle(row, 'monitor')">监控</el-button>
              <el-button link type="primary" size="small" @click="openLifecycle(row, 'iterate')">迭代</el-button>
              <el-button link type="warning" size="small" @click="openLifecycle(row, 'rollback')">回滚</el-button>
              <el-button link type="danger" size="small" @click="openLifecycle(row, 'archive')">归档</el-button>
              <el-button link type="info" size="small" @click="openLogs(row)">记录</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="aiReviewDialogVisible" title="登记AI评分复核结果" width="560px" destroy-on-close>
      <el-form :model="aiReviewForm" label-width="96px">
        <el-form-item label="评分对象">
          <el-input :model-value="currentAiReviewItem ? `${subjectTypeText(currentAiReviewItem.subjectType)} · ${currentAiReviewItem.subjectName}` : ''" disabled />
        </el-form-item>
        <el-form-item label="AI风险分">
          <el-input :model-value="currentAiReviewItem ? `${currentAiReviewItem.score} / ${aiRiskLevelText(currentAiReviewItem.riskLevel)}` : ''" disabled />
        </el-form-item>
        <el-form-item label="系统判断">
          <el-input :model-value="currentAiReviewItem?.verificationBasis || '-'" type="textarea" :rows="2" disabled />
        </el-form-item>
        <el-form-item label="复核标签" required>
          <el-select v-model="aiReviewForm.reviewLabel" style="width: 100%;">
            <el-option v-for="item in manualReviewLabelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="复核备注">
          <el-input v-model="aiReviewForm.reviewComment" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiReviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiReviewSubmitting" @click="submitAiReview">保存复核结果</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="aiFollowUpDialogVisible" title="生成AI评分跟进任务" width="620px" destroy-on-close>
      <el-form :model="aiFollowUpForm" label-width="104px">
        <el-form-item label="评分对象">
          <el-input :model-value="currentAiFollowUpItem ? `${subjectTypeText(currentAiFollowUpItem.subjectType)} · ${currentAiFollowUpItem.subjectName}` : ''" disabled />
        </el-form-item>
        <el-form-item label="风险结果">
          <el-input :model-value="currentAiFollowUpItem ? `${currentAiFollowUpItem.score}分 / ${aiRiskLevelText(currentAiFollowUpItem.riskLevel)} / ${currentAiFollowUpItem.priorityLevel}` : ''" disabled />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="任务类型">
            <el-select v-model="aiFollowUpForm.taskType" style="width: 100%;">
              <el-option label="整改核查" value="RECTIFICATION" />
              <el-option label="持续监控" value="MONITORING" />
            </el-select>
          </el-form-item>
          <el-form-item label="严重程度">
            <el-select v-model="aiFollowUpForm.severity" style="width: 100%;">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item label="责任部门">
            <el-input v-model="aiFollowUpForm.responsibleDept" />
          </el-form-item>
          <el-form-item label="责任人">
            <el-input v-model="aiFollowUpForm.responsiblePerson" />
          </el-form-item>
          <el-form-item label="问题分类">
            <el-input v-model="aiFollowUpForm.issueCategory" />
          </el-form-item>
          <el-form-item label="跟进期限">
            <el-date-picker v-model="aiFollowUpForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%;" />
          </el-form-item>
        </div>
        <el-form-item label="补充说明">
          <el-input v-model="aiFollowUpForm.comment" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiFollowUpDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiFollowUpSubmitting" @click="submitAiFollowUp">生成跟进任务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="modelDialogVisible" :title="editingModelId ? '编辑模型' : '新增模型'" width="760px" destroy-on-close>
      <el-form :model="modelForm" label-width="110px">
        <div class="form-grid">
          <el-form-item label="模型编码"><el-input v-model="modelForm.modelCode" placeholder="如 AMLM-STR-006" /></el-form-item>
          <el-form-item label="模型名称"><el-input v-model="modelForm.modelName" placeholder="请输入模型名称" /></el-form-item>
          <el-form-item label="模型类型">
            <el-select v-model="modelForm.modelType" style="width: 100%;">
              <el-option v-for="item in modelTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务场景">
            <el-select v-model="modelForm.scenario" style="width: 100%;">
              <el-option v-for="item in scenarioOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="算法类型"><el-input v-model="modelForm.algorithmType" placeholder="预留真实模型实现" /></el-form-item>
          <el-form-item label="版本"><el-input v-model="modelForm.version" placeholder="1.0.0" /></el-form-item>
          <el-form-item label="责任人"><el-input v-model="modelForm.owner" /></el-form-item>
          <el-form-item label="治理等级">
            <el-select v-model="modelForm.governanceLevel" style="width: 100%;">
              <el-option label="L1 高影响模型" value="L1" />
              <el-option label="L2 重要模型" value="L2" />
              <el-option label="L3 一般模型" value="L3" />
            </el-select>
          </el-form-item>
          <el-form-item label="风险等级">
            <el-select v-model="modelForm.riskLevel" style="width: 100%;">
              <el-option v-for="item in riskOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="生命周期">
            <el-select v-model="modelForm.lifecycleStatus" style="width: 100%;">
              <el-option v-for="item in lifecycleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="训练数据"><el-input v-model="modelForm.trainingDataset" /></el-form-item>
        <el-form-item label="验证数据"><el-input v-model="modelForm.validationDataset" /></el-form-item>
        <el-form-item label="迭代计划"><el-input v-model="modelForm.iterationPlan" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="模型说明"><el-input v-model="modelForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="配置JSON"><el-input v-model="modelForm.configJson" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitModel">保存模型</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lifecycleDialogVisible" :title="lifecycleTitle" width="620px" destroy-on-close>
      <el-form :model="lifecycleForm" label-width="110px">
        <el-form-item label="当前模型"><el-input :model-value="currentModel?.modelName" disabled /></el-form-item>
        <el-form-item v-if="currentAction === 'deploy'" label="部署环境">
          <el-select v-model="lifecycleForm.deploymentEnv" style="width: 100%;">
            <el-option label="开发环境" value="DEV" />
            <el-option label="测试环境" value="UAT" />
            <el-option label="生产环境" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentAction === 'test'" label="测试数据集">
          <el-input v-model="lifecycleForm.testDataset" placeholder="如 最近三个月人工复核样本" />
        </el-form-item>
        <el-form-item v-if="currentAction === 'monitor'" label="监控状态">
          <el-select v-model="lifecycleForm.monitorStatus" style="width: 100%;">
            <el-option v-for="item in monitorOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentAction === 'iterate'" label="目标版本">
          <el-input v-model="lifecycleForm.targetVersion" placeholder="留空则自动增加补丁版本" />
        </el-form-item>
        <el-form-item v-if="currentAction === 'iterate'" label="迭代计划">
          <el-input v-model="lifecycleForm.iterationPlan" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item v-if="currentAction === 'rollback'" label="回滚版本">
          <el-input v-model="lifecycleForm.targetVersion" placeholder="请输入上一稳定版本，如 1.0.0" />
        </el-form-item>
        <el-form-item v-if="currentAction === 'rollback'" label="回滚后状态">
          <el-select v-model="lifecycleForm.monitorStatus" style="width: 100%;">
            <el-option label="需关注" value="ATTENTION" />
            <el-option label="正常" value="NORMAL" />
            <el-option label="已漂移" value="DRIFTED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentAction === 'archive'" label="归档原因">
          <el-input v-model="lifecycleForm.archiveReason" type="textarea" :rows="3" />
        </el-form-item>
        <div v-if="currentAction === 'test' || currentAction === 'monitor'" class="metric-form-grid">
          <el-form-item label="准确率">
            <el-input-number v-model="lifecycleForm.precisionRate" :min="0" :max="1" :step="0.01" />
          </el-form-item>
          <el-form-item label="召回率">
            <el-input-number v-model="lifecycleForm.recallRate" :min="0" :max="1" :step="0.01" />
          </el-form-item>
          <el-form-item label="误报率">
            <el-input-number v-model="lifecycleForm.falsePositiveRate" :min="0" :max="1" :step="0.01" />
          </el-form-item>
          <el-form-item label="漂移分数">
            <el-input-number v-model="lifecycleForm.driftScore" :min="0" :max="1" :step="0.01" />
          </el-form-item>
        </div>
        <el-form-item label="操作摘要"><el-input v-model="lifecycleForm.actionSummary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="制品/报告"><el-input v-model="lifecycleForm.artifactRef" placeholder="模型包、测试报告或监控报告引用" /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="lifecycleForm.operator" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lifecycleDialogVisible = false">取消</el-button>
        <el-button :type="lifecycleButtonType" :loading="submitting" @click="submitLifecycle">{{ lifecycleConfirmText }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logsDialogVisible" title="生命周期记录" width="860px">
      <el-table :data="logs" v-loading="logLoading" stripe border>
        <el-table-column prop="actionTime" label="时间" min-width="170" fixed="left" />
        <el-table-column prop="actionType" label="动作" width="100">
          <template #default="{ row }">{{ actionLabel(row.actionType) }}</template>
        </el-table-column>
        <el-table-column prop="fromStatus" label="原状态" width="120">
          <template #default="{ row }">{{ optionLabel(lifecycleOptions, row.fromStatus) || '-' }}</template>
        </el-table-column>
        <el-table-column prop="toStatus" label="新状态" width="120">
          <template #default="{ row }">{{ optionLabel(lifecycleOptions, row.toStatus) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="actionSummary" label="摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="artifactRef" label="制品/报告" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import type { ECharts } from 'echarts'
import { ElMessage } from 'element-plus'
import { Download, Plus, Refresh } from '@element-plus/icons-vue'
import { modelApi } from '@/api/modules'
import type { AiRiskFollowUpTaskRequest, AiRiskReviewPoolItem, AiRiskReviewPoolOverview, AmlModel, ModelLifecycleLog } from '@/api/types'
import { disposeEchart, getEcharts } from '@/utils/echarts'

type LifecycleAction = 'test' | 'deploy' | 'monitor' | 'iterate' | 'rollback' | 'archive'

const loading = ref(false)
const submitting = ref(false)
const logLoading = ref(false)
const models = ref<AmlModel[]>([])
const logs = ref<ModelLifecycleLog[]>([])
const aiReviewItems = ref<AiRiskReviewPoolItem[]>([])
const currentModel = ref<AmlModel | null>(null)
const currentAction = ref<LifecycleAction>('test')
const lifecycleChartRef = ref<HTMLElement | null>(null)
const healthChartRef = ref<HTMLElement | null>(null)
const editingModelId = ref<string>('')
const modelDialogVisible = ref(false)
const lifecycleDialogVisible = ref(false)
const logsDialogVisible = ref(false)
const aiReviewLoading = ref(false)
const aiReviewExporting = ref(false)
const aiReviewSubmitting = ref(false)
const aiReviewTotal = ref(0)
const aiReviewDialogVisible = ref(false)
const aiFollowUpDialogVisible = ref(false)
const aiFollowUpSubmitting = ref(false)
const currentAiReviewItem = ref<AiRiskReviewPoolItem | null>(null)
const currentAiFollowUpItem = ref<AiRiskReviewPoolItem | null>(null)
let lifecycleChart: ECharts | null = null
let healthChart: ECharts | null = null
let resizeHandler: (() => void) | null = null

const overview = reactive({
  totalModels: 0,
  draftModels: 0,
  testingModels: 0,
  deployedModels: 0,
  monitoringModels: 0,
  iterationModels: 0,
  archivedModels: 0,
  attentionModels: 0,
  averageFalsePositiveRate: 0,
  averageDriftScore: 0
})

const aiReviewOverview = reactive<AiRiskReviewPoolOverview>({
  totalScores: 0,
  pendingReviewCount: 0,
  likelyTruePositiveCount: 0,
  likelyFalsePositiveCount: 0,
  unconfirmedCount: 0,
  highOrCriticalCount: 0,
  corroboratedCount: 0,
  highScoreNoRuleHitCount: 0,
  lowScoreWithDispositionCount: 0,
  latestScoredAt: ''
})

const query = reactive({
  keyword: '',
  modelType: '',
  scenario: '',
  lifecycleStatus: '',
  riskLevel: ''
})

const modelForm = reactive({
  modelCode: '',
  modelName: '',
  modelType: 'HYBRID',
  scenario: 'TRANSACTION_MONITORING',
  algorithmType: '',
  version: '1.0.0',
  lifecycleStatus: 'DRAFT',
  owner: '',
  governanceLevel: 'L2',
  riskLevel: 'MEDIUM',
  trainingDataset: '',
  validationDataset: '',
  iterationPlan: '',
  description: '',
  configJson: ''
})

const lifecycleForm = reactive({
  operator: '',
  actionSummary: '',
  artifactRef: '',
  deploymentEnv: 'UAT',
  testDataset: '',
  targetVersion: '',
  iterationPlan: '',
  archiveReason: '',
  monitorStatus: 'NORMAL',
  precisionRate: 0.9,
  recallRate: 0.85,
  falsePositiveRate: 0.12,
  driftScore: 0.05
})

const aiReviewQuery = reactive({
  page: 1,
  size: 10,
  subjectType: '',
  riskLevel: '',
  autoLabel: '',
  minScore: 65,
  pendingOnly: true
})

const aiReviewForm = reactive({
  reviewLabel: 'TRUE_POSITIVE',
  reviewComment: ''
})

const aiFollowUpForm = reactive<AiRiskFollowUpTaskRequest>({
  taskType: 'RECTIFICATION',
  issueCategory: 'AI高风险核查',
  severity: 'HIGH',
  responsibleDept: '反洗钱合规部',
  responsiblePerson: '',
  deadline: '',
  comment: ''
})

const modelTypeOptions = [
  { label: '规则模型', value: 'RULE' },
  { label: '统计模型', value: 'STATISTICAL' },
  { label: '图谱模型', value: 'GRAPH' },
  { label: '机器学习模型', value: 'ML' },
  { label: '混合评分模型', value: 'HYBRID' },
  { label: '名单相似度模型', value: 'NAME_MATCHING' }
]

const scenarioOptions = [
  { label: '客户风险识别', value: 'CUSTOMER_RISK' },
  { label: '交易监测', value: 'TRANSACTION_MONITORING' },
  { label: '名单筛查', value: 'SCREENING' },
  { label: '网络分析', value: 'NETWORK_ANALYSIS' },
  { label: '监管报送', value: 'REPORTING' }
]

const lifecycleOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '测试中', value: 'TESTING' },
  { label: '测试通过', value: 'TEST_PASSED' },
  { label: '已部署', value: 'DEPLOYED' },
  { label: '监控中', value: 'MONITORING' },
  { label: '迭代中', value: 'ITERATING' },
  { label: '已归档', value: 'ARCHIVED' }
]

const monitorOptions = [
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '正常', value: 'NORMAL' },
  { label: '需关注', value: 'ATTENTION' },
  { label: '已漂移', value: 'DRIFTED' }
]

const riskOptions = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' }
]

const aiRiskLevelOptions = [
  { label: '低风险', value: 'LOW' },
  { label: '中风险', value: 'MEDIUM' },
  { label: '高风险', value: 'HIGH' },
  { label: '极高风险', value: 'CRITICAL' }
]

const manualReviewLabelOptions = [
  { label: '确认有效风险', value: 'TRUE_POSITIVE' },
  { label: '确认误报', value: 'FALSE_POSITIVE' },
  { label: '继续观察', value: 'NEEDS_MONITORING' }
]

const lifecycleTitle = computed(() => ({
  test: '登记模型测试',
  deploy: '登记模型部署',
  monitor: '刷新模型监控',
  iterate: '登记模型迭代',
  rollback: '登记模型回滚',
  archive: '归档模型'
}[currentAction.value]))

const lifecycleConfirmText = computed(() => ({
  test: '保存测试结果',
  deploy: '确认部署',
  monitor: '刷新监控',
  iterate: '进入迭代',
  rollback: '确认回滚',
  archive: '确认归档'
}[currentAction.value]))

const lifecycleButtonType = computed(() => {
  if (currentAction.value === 'archive') return 'danger'
  if (currentAction.value === 'rollback') return 'warning'
  return 'primary'
})

async function loadOverview() {
  const res: any = await modelApi.getOverview()
  Object.assign(overview, res.data || {})
}

async function loadModels() {
  loading.value = true
  try {
    const res: any = await modelApi.getPage({
      page: 1,
      size: 50,
      keyword: query.keyword || undefined,
      modelType: query.modelType || undefined,
      scenario: query.scenario || undefined,
      lifecycleStatus: query.lifecycleStatus || undefined,
      riskLevel: query.riskLevel || undefined
    })
    models.value = res.data?.list || []
  } finally {
    loading.value = false
  }
}

async function loadAiReviewOverview() {
  const res: any = await modelApi.getAiRiskReviewOverview()
  Object.assign(aiReviewOverview, res.data || {})
}

async function loadAiReviewPool() {
  aiReviewLoading.value = true
  try {
    const res: any = await modelApi.getAiRiskReviewPool({
      page: aiReviewQuery.page,
      size: aiReviewQuery.size,
      subjectType: aiReviewQuery.subjectType || undefined,
      riskLevel: aiReviewQuery.riskLevel || undefined,
      autoLabel: aiReviewQuery.autoLabel || undefined,
      minScore: aiReviewQuery.minScore,
      pendingOnly: aiReviewQuery.pendingOnly
    })
    aiReviewItems.value = res.data?.list || []
    aiReviewTotal.value = Number(res.data?.total || 0)
  } finally {
    aiReviewLoading.value = false
  }
}

async function refreshAiReview() {
  aiReviewQuery.page = 1
  await Promise.all([loadAiReviewOverview(), loadAiReviewPool()])
}

function openAiReview(row: AiRiskReviewPoolItem) {
  currentAiReviewItem.value = row
  aiReviewForm.reviewLabel = row.manualReviewLabel || labelFromAutoLabel(row.autoLabel)
  aiReviewForm.reviewComment = row.manualReviewComment || ''
  aiReviewDialogVisible.value = true
}

async function submitAiReview() {
  if (!currentAiReviewItem.value) return
  aiReviewSubmitting.value = true
  try {
    await modelApi.reviewAiRiskScore(currentAiReviewItem.value.id, {
      reviewLabel: aiReviewForm.reviewLabel,
      reviewComment: aiReviewForm.reviewComment || undefined
    })
    ElMessage.success('AI评分复核结果已登记')
    aiReviewDialogVisible.value = false
    await refreshAiReview()
  } finally {
    aiReviewSubmitting.value = false
  }
}

function openAiFollowUp(row: AiRiskReviewPoolItem) {
  if (row.followUpTaskId) {
    ElMessage.info(`已生成跟进任务：${row.followUpTaskId}`)
    return
  }
  currentAiFollowUpItem.value = row
  Object.assign(aiFollowUpForm, {
    taskType: row.riskLevel === 'LOW' || row.riskLevel === 'MEDIUM' ? 'MONITORING' : 'RECTIFICATION',
    issueCategory: row.riskLevel === 'LOW' || row.riskLevel === 'MEDIUM' ? 'AI持续监控' : 'AI高风险核查',
    severity: defaultFollowUpSeverity(row),
    responsibleDept: '反洗钱合规部',
    responsiblePerson: '',
    deadline: defaultFollowUpDeadline(row),
    comment: row.verificationBasis || ''
  })
  aiFollowUpDialogVisible.value = true
}

async function submitAiFollowUp() {
  if (!currentAiFollowUpItem.value) return
  if (!aiFollowUpForm.deadline) {
    ElMessage.warning('请选择跟进期限')
    return
  }
  aiFollowUpSubmitting.value = true
  try {
    await modelApi.createAiRiskFollowUpTask(currentAiFollowUpItem.value.id, {
      ...aiFollowUpForm,
      responsiblePerson: aiFollowUpForm.responsiblePerson || undefined,
      comment: aiFollowUpForm.comment || undefined
    })
    ElMessage.success('已生成整改中心跟进任务')
    aiFollowUpDialogVisible.value = false
    await refreshAiReview()
  } finally {
    aiFollowUpSubmitting.value = false
  }
}

async function exportAiReviewPool() {
  aiReviewExporting.value = true
  try {
    const res: any = await modelApi.exportAiRiskReviewPool({
      page: 1,
      size: 10000,
      subjectType: aiReviewQuery.subjectType || undefined,
      riskLevel: aiReviewQuery.riskLevel || undefined,
      autoLabel: aiReviewQuery.autoLabel || undefined,
      minScore: aiReviewQuery.minScore,
      pendingOnly: aiReviewQuery.pendingOnly
    })
    const blob = res instanceof Blob ? res : new Blob([res], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `AI评分待复核清单-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    ElMessage.success('AI评分待复核清单已导出')
  } finally {
    aiReviewExporting.value = false
  }
}

function openCreate() {
  editingModelId.value = ''
  Object.assign(modelForm, {
    modelCode: '',
    modelName: '',
    modelType: 'HYBRID',
    scenario: 'TRANSACTION_MONITORING',
    algorithmType: '',
    version: '1.0.0',
    lifecycleStatus: 'DRAFT',
    owner: '',
    governanceLevel: 'L2',
    riskLevel: 'MEDIUM',
    trainingDataset: '',
    validationDataset: '',
    iterationPlan: '',
    description: '',
    configJson: '{"features":[],"threshold":"TBD"}'
  })
  modelDialogVisible.value = true
}

function openEdit(row: AmlModel) {
  editingModelId.value = row.id
  Object.assign(modelForm, {
    modelCode: row.modelCode,
    modelName: row.modelName,
    modelType: row.modelType,
    scenario: row.scenario,
    algorithmType: row.algorithmType,
    version: row.version,
    lifecycleStatus: row.lifecycleStatus,
    owner: row.owner,
    governanceLevel: row.governanceLevel,
    riskLevel: row.riskLevel,
    trainingDataset: row.trainingDataset,
    validationDataset: row.validationDataset,
    iterationPlan: row.iterationPlan,
    description: row.description,
    configJson: row.configJson
  })
  modelDialogVisible.value = true
}

async function submitModel() {
  if (!modelForm.modelCode || !modelForm.modelName) {
    ElMessage.warning('请填写模型编码和模型名称')
    return
  }
  submitting.value = true
  try {
    if (editingModelId.value) {
      await modelApi.update(editingModelId.value, { ...modelForm })
      ElMessage.success('模型信息已更新')
    } else {
      await modelApi.create({ ...modelForm })
      ElMessage.success('模型已创建')
    }
    modelDialogVisible.value = false
    await Promise.all([loadOverview(), loadModels()])
  } finally {
    submitting.value = false
  }
}

function openLifecycle(row: AmlModel, action: LifecycleAction) {
  currentModel.value = row
  currentAction.value = action
  Object.assign(lifecycleForm, {
    operator: '',
    actionSummary: defaultActionSummary(row, action),
    artifactRef: '',
    deploymentEnv: row.deploymentEnv || 'UAT',
    testDataset: row.validationDataset || '样例验证集',
    targetVersion: '',
    iterationPlan: row.iterationPlan || '',
    archiveReason: '',
    monitorStatus: row.monitorStatus || 'NORMAL',
    precisionRate: Number(row.precisionRate || 0.9),
    recallRate: Number(row.recallRate || 0.85),
    falsePositiveRate: Number(row.falsePositiveRate || 0.12),
    driftScore: Number(row.driftScore || 0.05)
  })
  lifecycleDialogVisible.value = true
}

async function submitLifecycle() {
  if (!currentModel.value) return
  if (currentAction.value === 'rollback' && !lifecycleForm.targetVersion) {
    ElMessage.warning('请填写回滚目标版本')
    return
  }
  submitting.value = true
  try {
    const id = currentModel.value.id
    const payload = { ...lifecycleForm }
    if (currentAction.value === 'test') await modelApi.test(id, payload)
    if (currentAction.value === 'deploy') await modelApi.deploy(id, payload)
    if (currentAction.value === 'monitor') await modelApi.monitor(id, payload)
    if (currentAction.value === 'iterate') await modelApi.iterate(id, payload)
    if (currentAction.value === 'rollback') await modelApi.rollback(id, payload)
    if (currentAction.value === 'archive') await modelApi.archive(id, payload)
    ElMessage.success(`${lifecycleTitle.value}已完成`)
    lifecycleDialogVisible.value = false
    await Promise.all([loadOverview(), loadModels()])
  } finally {
    submitting.value = false
  }
}

async function openLogs(row: AmlModel) {
  currentModel.value = row
  logsDialogVisible.value = true
  logLoading.value = true
  try {
    const res: any = await modelApi.getLogs(row.id, { page: 1, size: 100 })
    logs.value = res.data?.list || []
  } finally {
    logLoading.value = false
  }
}

function defaultActionSummary(row: AmlModel, action: LifecycleAction) {
  const name = row.modelName
  return {
    test: `${name} 完成样例数据测试并记录指标`,
    deploy: `${name} 完成部署登记，等待监控周期验证`,
    monitor: `${name} 刷新本期运行监控指标`,
    iterate: `${name} 基于监控反馈进入迭代优化`,
    rollback: `${name} 回滚至上一稳定版本并保留治理记录`,
    archive: `${name} 停止使用并归档治理材料`
  }[action]
}

function optionLabel(options: Array<{ label: string; value: string }>, value?: string) {
  return options.find(item => item.value === value)?.label || value || ''
}

function actionLabel(value: string) {
  return {
    CREATE: '创建',
    UPDATE: '更新',
    TEST: '测试',
    DEPLOY: '部署',
    MONITOR: '监控',
    ITERATE: '迭代',
    ROLLBACK: '回滚',
    ARCHIVE: '归档'
  }[value] || value
}

function statusType(status: string) {
  if (status === 'DEPLOYED' || status === 'MONITORING') return 'success'
  if (status === 'TESTING' || status === 'TEST_PASSED' || status === 'ITERATING') return 'warning'
  if (status === 'ARCHIVED') return 'info'
  return 'info'
}

function monitorType(status: string) {
  if (status === 'NORMAL') return 'success'
  if (status === 'ATTENTION') return 'warning'
  if (status === 'DRIFTED') return 'danger'
  return 'info'
}

function riskType(level: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}

function formatPercent(value?: number) {
  if (value === undefined || value === null) return '-'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function formatScore(value?: number) {
  if (value === undefined || value === null) return '-'
  return Number(value).toFixed(4)
}

function toNumber(value: number | string | undefined) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n : 0
}

function subjectTypeText(value: string) {
  return { CUSTOMER: '客户', TRANSACTION: '交易', ALERT: '预警' }[value] || value || '-'
}

function aiRiskLevelText(value: string) {
  return optionLabel(aiRiskLevelOptions, value)
}

function aiRiskType(level: string) {
  if (level === 'CRITICAL' || level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  if (level === 'LOW') return 'success'
  return 'info'
}

function autoLabelType(value: string) {
  if (value === 'LIKELY_TRUE_POSITIVE') return 'success'
  if (value === 'LIKELY_FALSE_POSITIVE') return 'info'
  return 'warning'
}

function priorityType(value: string) {
  if (value === 'P0') return 'danger'
  if (value === 'P1') return 'warning'
  if (value === 'P2') return 'primary'
  return 'info'
}

function reviewStatusText(value: string) {
  return {
    PENDING_REVIEW: '待延后复核',
    DEFERRED_REVIEW: '高优先级留痕',
    AUTO_WEAK_LABELED: '系统弱标注',
    MANUAL_REVIEWED: '已人工确认'
  }[value] || value || '-'
}

function reviewStatusType(value: string) {
  if (value === 'MANUAL_REVIEWED') return 'success'
  if (value === 'PENDING_REVIEW' || value === 'DEFERRED_REVIEW') return 'warning'
  return 'info'
}

function manualReviewLabelType(value: string) {
  if (value === 'TRUE_POSITIVE') return 'danger'
  if (value === 'FALSE_POSITIVE') return 'info'
  return 'warning'
}

function labelFromAutoLabel(value: string) {
  if (value === 'LIKELY_TRUE_POSITIVE') return 'TRUE_POSITIVE'
  if (value === 'LIKELY_FALSE_POSITIVE') return 'FALSE_POSITIVE'
  return 'NEEDS_MONITORING'
}

function defaultFollowUpSeverity(row: AiRiskReviewPoolItem): 'HIGH' | 'MEDIUM' | 'LOW' {
  if (row.riskLevel === 'CRITICAL' || row.riskLevel === 'HIGH' || Number(row.score || 0) >= 65) return 'HIGH'
  if (row.riskLevel === 'MEDIUM' || Number(row.score || 0) >= 35) return 'MEDIUM'
  return 'LOW'
}

function defaultFollowUpDeadline(row: AiRiskReviewPoolItem) {
  const date = new Date()
  const score = Number(row.score || 0)
  const days = score >= 85 || row.riskLevel === 'CRITICAL' ? 3 : score >= 65 || row.riskLevel === 'HIGH' ? 7 : 14
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

function scoreColor(score: number) {
  const value = Number(score || 0)
  if (value >= 85) return '#991b1b'
  if (value >= 65) return '#dc2626'
  if (value >= 35) return '#d97706'
  return '#16a34a'
}

function countModelsByStatus(statuses: string[]) {
  return models.value.filter(item => statuses.includes(item.lifecycleStatus)).length
}

function numericRate(value: unknown, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

async function renderModelVisualizations() {
  await nextTick()
  await Promise.all([renderLifecycleChart(), renderHealthChart()])
}

async function renderLifecycleChart() {
  const container = lifecycleChartRef.value
  if (!container || !container.isConnected) return
  const echarts = await getEcharts()
  if (!lifecycleChart) lifecycleChart = echarts.init(container)

  const stages = [
    { label: '草稿', count: countModelsByStatus(['DRAFT']), color: '#64748b' },
    { label: '测试', count: countModelsByStatus(['TESTING', 'TEST_PASSED']), color: '#d97706' },
    { label: '部署', count: countModelsByStatus(['DEPLOYED']), color: '#2563eb' },
    { label: '监控', count: countModelsByStatus(['MONITORING']), color: '#16a34a' },
    { label: '迭代', count: countModelsByStatus(['ITERATING']), color: '#7c3aed' },
    { label: '归档', count: countModelsByStatus(['ARCHIVED']), color: '#94a3b8' }
  ]

  lifecycleChart.setOption({
    color: stages.map(item => item.color),
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 36, right: 18, top: 28, bottom: 32 },
    xAxis: {
      type: 'category',
      data: stages.map(item => item.label),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#475569' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { color: '#64748b' }
    },
    series: [{
      type: 'bar',
      data: stages.map(item => ({ value: item.count, itemStyle: { color: item.color, borderRadius: [5, 5, 0, 0] } })),
      barMaxWidth: 34,
      label: { show: true, position: 'top', color: '#334155', fontWeight: 600 }
    }]
  }, true)
  lifecycleChart.resize()
}

async function renderHealthChart() {
  const container = healthChartRef.value
  if (!container || !container.isConnected) return
  const echarts = await getEcharts()
  if (!healthChart) healthChart = echarts.init(container)

  const data = models.value.map(item => {
    const precision = numericRate(item.precisionRate, 0.72) * 100
    const recall = numericRate(item.recallRate, 0.68) * 100
    const falsePositive = numericRate(item.falsePositiveRate, 0.1) * 100
    const drift = numericRate(item.driftScore, 0.04) * 100
    const radius = Math.max(12, Math.min(34, 10 + falsePositive * 0.8 + drift * 1.2))
    return {
      name: item.modelName,
      value: [precision, recall, radius, falsePositive, drift],
      itemStyle: {
        color: item.riskLevel === 'HIGH' ? '#dc2626' : item.riskLevel === 'MEDIUM' ? '#d97706' : '#16a34a'
      },
      raw: item
    }
  })

  healthChart.setOption({
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: any) => {
        const item = params.data?.raw || {}
        return [
          `<strong>${params.name}</strong>`,
          `生命周期：${optionLabel(lifecycleOptions, item.lifecycleStatus)}`,
          `风险等级：${optionLabel(riskOptions, item.riskLevel)}`,
          `准确率：${params.value?.[0]?.toFixed?.(1) || 0}%`,
          `召回率：${params.value?.[1]?.toFixed?.(1) || 0}%`,
          `误报率：${params.value?.[3]?.toFixed?.(1) || 0}%`,
          `漂移分数：${params.value?.[4]?.toFixed?.(1) || 0}%`
        ].join('<br/>')
      }
    },
    grid: { left: 42, right: 20, top: 24, bottom: 36 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      name: '准确率',
      nameTextStyle: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { formatter: '{value}%', color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      name: '召回率',
      nameTextStyle: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } },
      axisLabel: { formatter: '{value}%', color: '#64748b' }
    },
    series: [{
      type: 'scatter',
      data,
      symbolSize: (value: number[]) => value[2],
      label: {
        show: data.length <= 8,
        formatter: '{b}',
        position: 'right',
        color: '#334155',
        fontSize: 11
      },
      markArea: {
        silent: true,
        itemStyle: { color: 'rgba(22, 163, 74, 0.06)' },
        data: [[{ xAxis: 80, yAxis: 80 }, { xAxis: 100, yAxis: 100 }]]
      }
    }]
  }, true)
  healthChart.resize()
}

function disposeModelCharts() {
  disposeEchart(lifecycleChart)
  disposeEchart(healthChart)
  lifecycleChart = null
  healthChart = null
}

onMounted(() => {
  Promise.all([loadOverview(), loadModels(), loadAiReviewOverview(), loadAiReviewPool()]).then(renderModelVisualizations)
  resizeHandler = () => {
    lifecycleChart?.resize()
    healthChart?.resize()
  }
  window.addEventListener('resize', resizeHandler)
})

watch(models, () => renderModelVisualizations(), { deep: true })

onUnmounted(() => {
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  resizeHandler = null
  disposeModelCharts()
})
</script>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 26px;
  font-weight: 650;
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--text-secondary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  min-height: 86px;
  padding: 16px 18px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.metric-card span {
  display: block;
  color: var(--text-secondary);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  color: var(--text-primary);
  font-size: 28px;
  line-height: 1;
}

.metric-card.success strong {
  color: var(--color-success);
}

.metric-card.warning strong {
  color: var(--color-warning);
}

.visual-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 12px;
}

.chart-card {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-sm);
}

.chart-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.chart-card-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 700;
}

.chart-card-header p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.chart-box {
  height: 290px;
}

.table-panel {
  padding: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 8px;
}

.ai-review-panel {
  padding: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}

.ai-review-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.ai-review-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 17px;
  font-weight: 700;
}

.ai-review-header p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.ai-review-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ai-review-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.ai-review-metric {
  min-height: 74px;
  padding: 13px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
  background: #f8fafc;
}

.ai-review-metric span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.ai-review-metric strong {
  display: block;
  margin-top: 7px;
  color: #111827;
  font-size: 24px;
  line-height: 1;
}

.ai-review-metric.success strong {
  color: #16a34a;
}

.ai-review-metric.warning strong {
  color: #d97706;
}

.ai-review-metric.danger strong {
  color: #dc2626;
}

.ai-review-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.ai-review-toolbar .el-select {
  width: 150px;
}

.ai-subject-cell {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.ai-subject-cell strong {
  color: #111827;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-subject-cell span {
  color: #64748b;
  font-size: 12px;
}

.ai-score-cell {
  display: grid;
  gap: 5px;
}

.ai-score-cell strong {
  color: #111827;
  font-size: 14px;
}

.ai-review-pagination {
  justify-content: flex-end;
  margin-top: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.toolbar .el-input {
  width: 240px;
}

.toolbar .el-select {
  width: 160px;
}

.table-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}

.metric-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 10px;
}

@media (max-width: 1180px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .visual-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .visual-grid,
  .form-grid,
  .metric-form-grid {
    grid-template-columns: 1fr;
  }

  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar .el-input,
  .toolbar .el-select,
  .toolbar .el-button {
    width: 100%;
  }
}
</style>
