import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const frontendUrl = process.env.FRONTEND_URL || process.env.BASE_URL || 'http://127.0.0.1:5173'
const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || ['admin', '123'].join('')
const runId = process.env.E2E_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
const prefix = process.env.E2E_PREFIX || 'E2E'
const screenshotDir = process.env.SCREENSHOT_DIR || path.join('/tmp', 'aml-frontend-browser-e2e')
const headless = process.env.HEADLESS !== 'false'
const slowMo = Number(process.env.PLAYWRIGHT_SLOW_MO || 0)
const navigationTimeout = Number(process.env.PLAYWRIGHT_NAVIGATION_TIMEOUT || 90000)
const assertionTimeout = Number(process.env.PLAYWRIGHT_ASSERTION_TIMEOUT || 20000)
const browserIp = process.env.E2E_BROWSER_IP || ''

const numericRun = runId.replace(/\D/g, '') || new Date().toISOString().replace(/\D/g, '').slice(0, 14)
const idTail = numericRun.slice(-4).padStart(4, '0')
const phoneTail = numericRun.slice(-8).padStart(8, '0')
const customerName = `${prefix}案件闭环UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900505${idTail}`
const customerPhone = `135${phoneTail}`
const customerEmail = `case_${numericRun}@test.local`
const alertSummary = `${prefix}案件管理UI闭环验证 ${runId}`
const investigationContent = `核查客户资金来源、交易目的和交易对手关系 ${runId}`
const investigationConclusion = '需提交审批'

const frameworkOverlayPatterns = [
  'Internal server error',
  'plugin:vite',
  'Failed to compile',
  'Uncaught RuntimeError',
  'ResizeObserver loop completed with undelivered notifications'
]

const ignoredConsolePatterns = [
  /Download the Vue Devtools extension/i,
  /Running in development mode/i
]

const results = []
const runtimeIssues = []

function pass(message) {
  results.push({ ok: true, message })
  console.log(`  \x1b[32m✓\x1b[0m ${message}`)
}

function fail(message, detail = '') {
  results.push({ ok: false, message, detail })
  console.log(`  \x1b[31m✗\x1b[0m ${message}`)
  if (detail) {
    console.log(`    Detail: ${detail.slice(0, 1200)}`)
  }
}

function info(message) {
  console.log(`\x1b[33m▶ ${message}\x1b[0m`)
}

function isIgnoredConsole(message) {
  return ignoredConsolePatterns.some(pattern => pattern.test(message))
}

function displayCaseCustomerName(name, id) {
  const raw = String(name || '').trim()
  const lower = raw.toLowerCase()
  const isLegacy = /^客户\d+$/.test(raw) || lower.includes('e2e') || /[åæèéçä]/.test(raw)
  if (raw && !isLegacy) {
    return raw
  }
  return id ? `客户ID ${id}` : '-'
}

async function installE2EApiHeaders(context) {
  await context.route('**/api/**', async route => {
    const headers = {
      ...route.request().headers(),
      'x-e2e-run-id': runId,
      ...(browserIp ? { 'x-forwarded-for': browserIp } : {})
    }
    await route.continue({ headers })
  })
}

async function launchBrowser() {
  const baseOptions = {
    headless,
    slowMo,
    args: ['--no-sandbox', '--disable-dev-shm-usage']
  }

  if (process.env.PLAYWRIGHT_EXECUTABLE_PATH) {
    return chromium.launch({
      ...baseOptions,
      executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH
    })
  }

  const preferredChannels = process.env.PLAYWRIGHT_CHANNEL
    ? [process.env.PLAYWRIGHT_CHANNEL]
    : ['chrome', 'msedge']

  const errors = []
  for (const channel of preferredChannels) {
    try {
      return await chromium.launch({ ...baseOptions, channel })
    } catch (error) {
      errors.push(`${channel}: ${error.message.split('\n')[0]}`)
    }
  }

  try {
    return await chromium.launch(baseOptions)
  } catch (error) {
    errors.push(`bundled chromium: ${error.message.split('\n')[0]}`)
    throw new Error([
      'Unable to launch a browser for case-workflow-e2e.',
      'Install Chrome/Edge, set PLAYWRIGHT_CHANNEL=chrome, or run `npx playwright install chromium` in frontend.',
      ...errors.map(item => `- ${item}`)
    ].join('\n'))
  }
}

async function pageText(page, selector = 'body') {
  try {
    return await page.locator(selector).innerText({ timeout: assertionTimeout })
  } catch (error) {
    return ''
  }
}

async function assertNoOverlay(page, label) {
  const bodyText = await pageText(page)
  const overlay = frameworkOverlayPatterns.find(pattern => bodyText.includes(pattern))
  if (overlay) {
    fail(`${label} 未出现框架错误覆盖层`, `matched: ${overlay}`)
    return false
  }
  pass(`${label} 未出现框架错误覆盖层`)
  return true
}

async function assertNoRuntimeIssues(label, issueStart = 0) {
  const issues = runtimeIssues.slice(issueStart)
  if (issues.length === 0) {
    pass(`${label} 无新增 console/pageerror`)
    return true
  }
  fail(`${label} 出现新增 console/pageerror`, JSON.stringify(issues, null, 2))
  return false
}

async function waitForApiJson(page, predicate, action) {
  const [response] = await Promise.all([
    page.waitForResponse(response => predicate(response), { timeout: assertionTimeout }),
    action()
  ])
  const text = await response.text()
  let body = null
  try {
    body = text ? JSON.parse(text) : null
  } catch (error) {
    body = { raw: text }
  }
  return { response, body }
}

async function login(page) {
  await page.goto(`${frontendUrl}/login`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
  await page.locator('input[placeholder="用户名"]').fill(username)
  await page.locator('input[placeholder="密码"]').fill(password)
  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: assertionTimeout }),
    page.getByRole('button', { name: /登\s*录/ }).click()
  ])
  await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
  pass(`${username} 登录后进入 /dashboard`)
}

async function authedFetch(page, url, options = {}) {
  return page.evaluate(async ({ requestUrl, requestOptions, e2eRunId }) => {
    const token = localStorage.getItem('aml_token')
    const response = await fetch(requestUrl, {
      ...requestOptions,
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'X-E2E-Run-Id': e2eRunId,
        ...(requestOptions.headers || {})
      }
    })
    return {
      status: response.status,
      body: await response.json()
    }
  }, { requestUrl: url, requestOptions: options, e2eRunId: runId })
}

async function prepareCaseFixture(page) {
  const customerResult = await authedFetch(page, '/api/kyc/customers', {
    method: 'POST',
    body: JSON.stringify({
      customerType: 'INDIVIDUAL',
      name: customerName,
      nationality: 'CN',
      idType: 'IDCARD',
      idNumber: customerIdNumber,
      phone: customerPhone,
      email: customerEmail,
      address: '广东省深圳市福田区深南大道2000号',
      occupation: '贸易公司实际控制人',
      employer: '深圳华远进出口有限公司',
      jobTitle: '总经理'
    })
  })
  const customerId = customerResult.body?.data?.id
  if (customerResult.status === 200 && customerResult.body?.code === 200 && customerId) {
    pass(`准备案件测试客户成功，id=${customerId}`)
  } else {
    fail('准备案件测试客户失败', JSON.stringify(customerResult, null, 2))
  }

  const alertResult = await authedFetch(page, '/api/alerts/manual', {
    method: 'POST',
    body: JSON.stringify({
      customerId,
      customerName,
      alertType: 'SUSPICIOUS_TX',
      riskScore: 92,
      riskLevel: 'CRITICAL',
      sourceRuleCodes: 'CASE_UI_COMPLEX_FLOW',
      alertSummary,
      ruleCode: 'CASE_UI_COMPLEX_FLOW',
      ruleName: '案件闭环复杂资金链路',
      matchScore: 98,
      matchDetail: `案件管理 UI 闭环验证 ${runId}`
    })
  })
  const alert = alertResult.body?.data
  if (alertResult.status === 200 && alertResult.body?.code === 200 && alert?.id) {
    pass(`准备案件来源预警成功，alertId=${alert.id}`)
  } else {
    fail('准备案件来源预警失败', JSON.stringify(alertResult, null, 2))
  }

  const assignResult = await authedFetch(page, '/api/alerts/assign', {
    method: 'POST',
    body: JSON.stringify({
      alertId: alert?.id,
      assignTo: 1,
      assignReason: `案件 UI E2E 指派 ${runId}`
    })
  })
  if (assignResult.status === 200 && assignResult.body?.code === 200) {
    pass('案件来源预警指派成功')
  } else {
    fail('案件来源预警指派失败', JSON.stringify(assignResult, null, 2))
  }

  const processResult = await authedFetch(page, '/api/alerts/process', {
    method: 'POST',
    body: JSON.stringify({
      alertId: alert?.id,
      processResult: 'CONFIRMED_SUSPICIOUS',
      processRemark: `案件管理 UI E2E 确认可疑 ${runId}`
    })
  })
  if (processResult.status === 200 && processResult.body?.code === 200) {
    pass('确认预警并自动创建案件成功')
  } else {
    fail('确认预警创建案件失败', JSON.stringify(processResult, null, 2))
  }

  const caseResult = await authedFetch(page, `/api/cases/page?customerId=${customerId}&size=5`)
  const caseItem = caseResult.body?.data?.list?.[0]
  if (caseResult.status === 200 && caseResult.body?.code === 200 && caseItem?.id && caseItem?.caseNo) {
    pass(`定位自动创建案件成功，caseNo=${caseItem.caseNo}`)
  } else {
    fail('定位自动创建案件失败', JSON.stringify(caseResult, null, 2))
  }

  return { customerId, alertId: alert?.id, caseId: caseItem?.id, caseNo: caseItem?.caseNo }
}

async function changeCaseStatus(page, buttonName, nextStatusText, remark) {
  await page.getByRole('button', { name: buttonName }).first().click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '状态变更' }).last()
  await dialog.waitFor({ state: 'visible', timeout: assertionTimeout })
  await dialog.locator('textarea').fill(remark)
  await waitForApiJson(
    page,
    response => response.url().includes('/api/cases/')
      && response.url().includes('/status')
      && response.request().method() === 'PUT',
    () => dialog.getByRole('button', { name: '确认变更' }).click()
  )
  await page.getByText('状态变更成功').waitFor({ state: 'visible', timeout: assertionTimeout })
  await page.getByText(nextStatusText, { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
  pass(`案件状态流转为${nextStatusText}`)
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 案件管理 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  Customer: ${customerName}`)
  console.log(`  HEADLESS: ${headless}`)
  console.log(`  E2E_BROWSER_IP: ${browserIp || "(default)"}`)
  console.log('')

  await mkdir(screenshotDir, { recursive: true })
  const browser = await launchBrowser()
  const context = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    ignoreHTTPSErrors: true
  })
  await installE2EApiHeaders(context)
  const page = await context.newPage()

  page.on('console', message => {
    const type = message.type()
    const text = message.text()
    if ((type === 'error' || type === 'warning' || type === 'warn') && !isIgnoredConsole(text)) {
      runtimeIssues.push({ type, text })
    }
  })

  page.on('pageerror', error => {
    runtimeIssues.push({ type: 'pageerror', text: error.message })
  })

  try {
    info('1. 登录并准备已确认预警生成的案件')
    await login(page)
    const issueStart = runtimeIssues.length
    const { customerId, caseNo } = await prepareCaseFixture(page)
    const displayedCustomerName = displayCaseCustomerName(customerName, customerId)

    info('2. 进入案件管理并验证案件列表')
    await page.goto(`${frontendUrl}/cases`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText(caseNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(displayedCustomerName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(alertSummary, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/cases')
    pass('案件列表展示自动创建案件')
    await page.screenshot({ path: path.join(screenshotDir, `case-list-${runId}.png`), fullPage: false })

    info('3. 打开案件详情并验证时间轴')
    await page.getByRole('button', { name: '详情' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '案件详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(caseNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('案件处置时间轴').waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('案件创建').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('案件详情展示处置时间轴')
    await page.screenshot({ path: path.join(screenshotDir, `case-detail-${runId}.png`), fullPage: false })
    await detailDialog.locator('.el-dialog__headerbtn').click()
    await detailDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('4. 添加调查记录')
    await page.getByRole('button', { name: '调查记录' }).first().click()
    const investigationDialog = page.locator('.el-dialog').filter({ hasText: '添加调查记录' }).last()
    await investigationDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await investigationDialog.locator('textarea').nth(0).fill(investigationContent)
    await investigationDialog.locator('textarea').nth(1).fill(investigationConclusion)
    const investigationResult = await waitForApiJson(
      page,
      response => response.url().includes('/api/cases/')
        && response.url().includes('/investigation')
        && response.request().method() === 'POST',
      () => investigationDialog.getByRole('button', { name: '提交' }).click()
    )
    if (investigationResult.response.status() === 200 && investigationResult.body?.code === 200) {
      pass('案件调查记录接口提交成功')
    } else {
      fail('案件调查记录接口返回异常', JSON.stringify(investigationResult.body, null, 2))
      throw new Error('Case investigation API returned an unexpected response.')
    }
    await page.getByText('调查记录添加成功').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('案件调查记录可通过页面新增')

    info('5. 执行案件状态流转直到关闭')
    await changeCaseStatus(page, '开始调查', '调查中', `开始调查 ${runId}`)
    await changeCaseStatus(page, '提交审批', '待审批', `提交审批 ${runId}`)
    await changeCaseStatus(page, '审批通过', '已报送', `审批通过 ${runId}`)

    await page.getByRole('button', { name: '关闭案件' }).first().click()
    const closeDialog = page.locator('.el-dialog').filter({ hasText: '关闭案件' }).last()
    await closeDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await closeDialog.locator('textarea').fill(`案件管理 UI E2E 闭环关闭 ${runId}`)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/cases/')
        && response.url().includes('/close')
        && response.request().method() === 'POST',
      () => closeDialog.getByRole('button', { name: '确认关闭' }).click()
    )
    await page.getByText('案件已关闭').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已结案', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('案件可通过 UI 完成关闭闭环')
    await page.screenshot({ path: path.join(screenshotDir, `case-closed-${runId}.png`), fullPage: false })

    info('6. 复查详情包含调查记录和关闭状态')
    await page.getByRole('button', { name: '详情' }).first().click()
    const finalDetailDialog = page.locator('.el-dialog').filter({ hasText: '案件详情' }).last()
    await finalDetailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText(investigationContent, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText('案件关闭').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('案件详情沉淀调查记录和关闭时间轴')

    await assertNoOverlay(page, '案件管理 UI 闭环')
    await assertNoRuntimeIssues('案件管理 UI 闭环', issueStart)
  } finally {
    await browser.close()
  }

  const passed = results.filter(item => item.ok).length
  const failed = results.filter(item => !item.ok).length
  console.log('')
  console.log('==========================================')
  console.log(`  测试结果: \x1b[32m${passed} 通过\x1b[0m / \x1b[31m${failed} 失败\x1b[0m / ${results.length} 总计`)
  console.log('==========================================')
  console.log('')

  if (failed > 0) {
    process.exit(1)
  }
}

main().catch(error => {
  console.error(error.message || error)
  process.exit(1)
})
