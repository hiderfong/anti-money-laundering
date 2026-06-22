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
const customerName = `${prefix}STR闭环UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900606${idTail}`
const customerPhone = `134${phoneTail}`
const customerEmail = `str_${numericRun}@test.local`
const alertSummary = `${prefix}STR报告UI闭环验证 ${runId}`
const reportContent = `可疑交易报告：客户${customerName}存在多笔异常资金往来和交易目的说明不足，需按反洗钱要求提交监管。${runId}`
const reviewOpinion = `STR UI E2E 审核通过，证据链完整 ${runId}`

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

function parseJsonPreservingLongIds(text) {
  if (!text) return null
  const normalized = text.replace(/"([A-Za-z0-9_]*(?:id|Id|ID))"\s*:\s*(\d{16,})/g, '"$1":"$2"')
  return JSON.parse(normalized)
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
      'Unable to launch a browser for str-report-workflow-e2e.',
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
    body = parseJsonPreservingLongIds(text)
  } catch (error) {
    body = { raw: text }
  }
  return { response, body }
}

async function selectVisibleOption(page, optionText) {
  const option = page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: optionText })
    .first()
  await option.click({ timeout: assertionTimeout })
}

async function confirmMessageBox(page, apiPredicate, trigger) {
  await trigger()
  const box = page.locator('.el-message-box').last()
  await box.waitFor({ state: 'visible', timeout: assertionTimeout })
  return waitForApiJson(page, apiPredicate, () => box.getByRole('button', { name: '确认' }).click())
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
  const result = await page.evaluate(async ({ requestUrl, requestOptions, e2eRunId }) => {
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
      text: await response.text()
    }
  }, { requestUrl: url, requestOptions: options, e2eRunId: runId })

  let body = null
  try {
    body = parseJsonPreservingLongIds(result.text)
  } catch (error) {
    body = { raw: result.text }
  }
  return { status: result.status, body }
}

async function prepareCaseForStr(page) {
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
      address: '北京市西城区金融大街88号',
      occupation: '企业法定代表人',
      employer: '北京汇融供应链管理有限公司',
      jobTitle: '法定代表人'
    })
  })
  const customerId = customerResult.body?.data?.id
  if (customerResult.status === 200 && customerResult.body?.code === 200 && customerId) {
    pass(`准备 STR 测试客户成功，id=${customerId}`)
  } else {
    fail('准备 STR 测试客户失败', JSON.stringify(customerResult, null, 2))
  }

  const alertResult = await authedFetch(page, '/api/alerts/manual', {
    method: 'POST',
    body: JSON.stringify({
      customerId,
      customerName,
      alertType: 'MANUAL',
      riskScore: 93,
      riskLevel: 'CRITICAL',
      sourceRuleCodes: 'STR_UI_SUSPICIOUS',
      alertSummary,
      ruleCode: 'STR_UI_SUSPICIOUS',
      ruleName: 'STR报告闭环可疑交易线索',
      matchScore: 99,
      matchDetail: `STR 报告 UI 闭环验证 ${runId}`
    })
  })
  const alert = alertResult.body?.data
  if (alertResult.status === 200 && alertResult.body?.code === 200 && alert?.id) {
    pass(`准备 STR 来源预警成功，alertId=${alert.id}`)
  } else {
    fail('准备 STR 来源预警失败', JSON.stringify(alertResult, null, 2))
  }

  const assignResult = await authedFetch(page, '/api/alerts/assign', {
    method: 'POST',
    body: JSON.stringify({
      alertId: alert?.id,
      assignTo: 1,
      assignReason: `STR UI E2E 指派 ${runId}`
    })
  })
  if (assignResult.status === 200 && assignResult.body?.code === 200) {
    pass('STR 来源预警指派成功')
  } else {
    fail('STR 来源预警指派失败', JSON.stringify(assignResult, null, 2))
  }

  const processResult = await authedFetch(page, '/api/alerts/process', {
    method: 'POST',
    body: JSON.stringify({
      alertId: alert?.id,
      processResult: 'CONFIRMED_SUSPICIOUS',
      processRemark: `STR UI E2E 确认可疑 ${runId}`
    })
  })
  if (processResult.status === 200 && processResult.body?.code === 200) {
    pass('确认预警并自动创建 STR 来源案件成功')
  } else {
    fail('确认预警创建 STR 来源案件失败', JSON.stringify(processResult, null, 2))
  }

  const caseResult = await authedFetch(page, `/api/cases/page?customerId=${customerId}&size=5`)
  const caseItem = caseResult.body?.data?.list?.[0]
  if (caseResult.status === 200 && caseResult.body?.code === 200 && caseItem?.id) {
    pass(`定位 STR 来源案件成功，caseNo=${caseItem.caseNo}`)
  } else {
    fail('定位 STR 来源案件失败', JSON.stringify(caseResult, null, 2))
  }

  const statusResult = await authedFetch(page, `/api/cases/${caseItem?.id}/status?toStatus=INVESTIGATING&remark=${encodeURIComponent(`STR报告创建前进入调查 ${runId}`)}`, {
    method: 'PUT'
  })
  if (statusResult.status === 200 && statusResult.body?.code === 200) {
    pass('STR 来源案件已流转为调查中')
  } else {
    fail('STR 来源案件流转失败', JSON.stringify(statusResult, null, 2))
  }

  return { customerId, caseId: caseItem?.id, caseNo: caseItem?.caseNo }
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML STR 报告 UI E2E 闭环')
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
    info('1. 登录并准备 STR 来源案件')
    await login(page)
    const issueStart = runtimeIssues.length
    const { caseId } = await prepareCaseForStr(page)

    info('2. 进入 STR 报告页面并新建报告')
    await page.goto(`${frontendUrl}/str-reports`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('STR可疑交易报告').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/str-reports')

    await page.getByRole('button', { name: '新建报告' }).click()
    const createDialog = page.locator('.el-dialog').filter({ hasText: '新建STR报告' }).last()
    await createDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await createDialog.locator('.el-form-item').filter({ hasText: '关联案件ID' }).locator('input').fill(String(caseId))
    await createDialog.locator('.el-select').click()
    await selectVisibleOption(page, '常规可疑交易报告')
    await createDialog.locator('textarea').fill(reportContent)
    const createResult = await waitForApiJson(
      page,
      response => response.url().includes('/api/str-reports')
        && response.request().method() === 'POST'
        && !response.url().includes('/submit'),
      () => createDialog.getByRole('button', { name: '确认创建' }).click()
    )
    const reportNo = createResult.body?.data?.reportNo
    if (createResult.body?.code === 200 && reportNo) {
      pass(`STR 报告创建成功，reportNo=${reportNo}`)
    } else {
      fail('STR 报告创建接口返回异常', JSON.stringify(createResult.body, null, 2))
    }
    await page.getByText(reportNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(reportContent, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `str-report-list-${runId}.png`), fullPage: false })

    info('3. 提交审核')
    await confirmMessageBox(
      page,
      response => response.url().includes('/api/str-reports/')
        && response.url().includes('/submit-review')
        && response.request().method() === 'POST',
      () => page.getByRole('button', { name: '提交审核' }).first().click()
    )
    await page.getByText('已提交审核').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('待审核', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('STR 报告可提交审核')

    info('4. 审核通过')
    await page.getByRole('button', { name: '审核' }).first().click()
    const reviewDialog = page.locator('.el-dialog').filter({ hasText: '审核STR报告' }).last()
    await reviewDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await reviewDialog.locator('textarea').fill(reviewOpinion)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/str-reports/')
        && response.url().includes('/review')
        && response.request().method() === 'POST',
      () => reviewDialog.getByRole('button', { name: '确认' }).click()
    )
    await page.getByText('审核通过').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已审核', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('STR 报告可审核通过')

    info('5. 报送监管')
    await confirmMessageBox(
      page,
      response => response.url().includes('/api/str-reports/')
        && response.url().includes('/submit-regulator')
        && response.request().method() === 'POST',
      () => page.getByRole('button', { name: '报送监管' }).first().click()
    )
    await page.getByText('已报送监管').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已报送', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('STR 报告可报送监管')
    await page.screenshot({ path: path.join(screenshotDir, `str-report-submitted-${runId}.png`), fullPage: false })

    info('6. 复查报告详情')
    await page.getByRole('button', { name: '详情' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: 'STR报告详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(reportNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(reportContent, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(reviewOpinion, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('STR 报告详情展示内容、审核意见和最终状态')

    await assertNoOverlay(page, 'STR 报告 UI 闭环')
    await assertNoRuntimeIssues('STR 报告 UI 闭环', issueStart)
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
