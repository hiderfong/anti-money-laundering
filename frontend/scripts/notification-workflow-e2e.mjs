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

const numericRun = runId.replace(/\D/g, '') || new Date().toISOString().replace(/\D/g, '').slice(0, 14)
const idTail = numericRun.slice(-4).padStart(4, '0')
const phoneTail = numericRun.slice(-8).padStart(8, '0')
const namePool = ['顾明轩', '林若宁', '沈知远', '赵景行', '陈启明', '周嘉言']
const customerName = `${namePool[Number(idTail) % namePool.length]}${numericRun.slice(-6)}`
const customerIdNumber = `11010119900808${idTail}`
const customerPhone = `132${phoneTail}`
const customerEmail = `notification_case_${numericRun}@test.local`
const alertSummary = `${prefix}通知中心案件通知闭环验证 ${runId}`

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

function requireStep(condition, successMessage, detail = '', failureMessage = successMessage) {
  if (condition) {
    pass(successMessage)
    return
  }
  fail(failureMessage, detail)
  throw new Error(failureMessage)
}

function info(message) {
  console.log(`\x1b[33m▶ ${message}\x1b[0m`)
}

function isIgnoredConsole(message) {
  return ignoredConsolePatterns.some(pattern => pattern.test(message))
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
      'Unable to launch a browser for notification-workflow-e2e.',
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

async function selectVisibleOption(page, optionText) {
  const option = page
    .locator('.el-select-dropdown:visible .el-select-dropdown__item')
    .filter({ hasText: optionText })
    .first()
  await option.click({ timeout: assertionTimeout })
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

async function prepareCaseNotification(page) {
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
      address: '上海市浦东新区世纪大道100号',
      occupation: '跨境贸易企业负责人',
      employer: '上海景诚国际贸易有限公司',
      jobTitle: '执行董事'
    })
  })
  const customerId = customerResult.body?.data?.id
  requireStep(
    customerResult.status === 200 && customerResult.body?.code === 200 && customerId,
    `准备通知测试客户成功，id=${customerId || 'N/A'}`,
    JSON.stringify(customerResult, null, 2),
    '准备通知测试客户失败'
  )

  const alertResult = await authedFetch(page, '/api/alerts/manual', {
    method: 'POST',
    body: JSON.stringify({
      customerId,
      customerName,
      alertType: 'SUSPICIOUS_TX',
      riskScore: 91,
      riskLevel: 'CRITICAL',
      sourceRuleCodes: 'NOTIFICATION_CASE_LINK',
      alertSummary,
      ruleCode: 'NOTIFICATION_CASE_LINK',
      ruleName: '通知中心案件关联验证',
      matchScore: 96,
      matchDetail: `通知中心案件通知闭环验证 ${runId}`
    })
  })
  const alert = alertResult.body?.data
  requireStep(
    alertResult.status === 200 && alertResult.body?.code === 200 && alert?.id,
    `准备通知来源预警成功，alertId=${alert?.id || 'N/A'}`,
    JSON.stringify(alertResult, null, 2),
    '准备通知来源预警失败'
  )

  const assignResult = await authedFetch(page, '/api/alerts/assign', {
    method: 'POST',
    body: JSON.stringify({
      alertId: alert?.id,
      assignTo: 1,
      assignReason: `通知中心 UI E2E 指派 ${runId}`
    })
  })
  requireStep(
    assignResult.status === 200 && assignResult.body?.code === 200,
    '通知来源预警指派成功',
    JSON.stringify(assignResult, null, 2),
    '通知来源预警指派失败'
  )

  const processResult = await authedFetch(page, '/api/alerts/process', {
    method: 'POST',
    body: JSON.stringify({
      alertId: alert?.id,
      processResult: 'CONFIRMED_SUSPICIOUS',
      processRemark: `通知中心 UI E2E 确认可疑 ${runId}`
    })
  })
  requireStep(
    processResult.status === 200 && processResult.body?.code === 200,
    '确认预警并创建案件成功',
    JSON.stringify(processResult, null, 2),
    '确认预警创建案件失败'
  )

  const caseResult = await authedFetch(page, `/api/cases/page?customerId=${customerId}&size=5`)
  const caseItem = caseResult.body?.data?.list?.[0]
  requireStep(
    caseResult.status === 200 && caseResult.body?.code === 200 && caseItem?.id && caseItem?.caseNo,
    `定位案件成功，caseNo=${caseItem?.caseNo || 'N/A'}`,
    JSON.stringify(caseResult, null, 2),
    '定位案件失败'
  )

  const notificationResult = await authedFetch(page, '/api/system/notifications/my?type=CASE&isRead=false&size=10')
  const notifications = notificationResult.body?.data?.list || []
  const notification = notifications.find(item => String(item.relatedId) === String(caseItem?.id))
  requireStep(
    notificationResult.status === 200 && notificationResult.body?.code === 200 && notification?.id,
    `案件通知已生成，notificationId=${notification?.id || 'N/A'}`,
    JSON.stringify(notificationResult.body, null, 2),
    '案件通知未生成或未关联案件'
  )

  return {
    caseId: caseItem?.id,
    caseNo: caseItem?.caseNo,
    notificationTitle: notification?.title || `案件已创建：${caseItem?.caseNo}`
  }
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 通知中心案件通知 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  Customer: ${customerName}`)
  console.log(`  HEADLESS: ${headless}`)
  console.log('')

  await mkdir(screenshotDir, { recursive: true })
  const browser = await launchBrowser()
  const context = await browser.newContext({
    viewport: { width: 1366, height: 768 },
    ignoreHTTPSErrors: true
  })
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
    info('1. 登录并准备案件通知')
    await login(page)
    const issueStart = runtimeIssues.length
    const { caseId, caseNo, notificationTitle } = await prepareCaseNotification(page)

    info('2. 进入通知中心并筛选案件通知')
    await page.goto(`${frontendUrl}/notifications`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('未读通知').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.locator('.filter-card .el-select').click()
    await selectVisibleOption(page, '案件通知')
    await page.getByText(notificationTitle, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('案件通知', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/notifications')
    pass('通知中心可筛选并展示案件通知')
    await page.screenshot({ path: path.join(screenshotDir, `notification-case-list-${runId}.png`), fullPage: false })

    info('3. 打开案件通知详情')
    await page.getByRole('button', { name: /查看详情/ }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '通知详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(notificationTitle, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(String(caseId), { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(caseNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(customerName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('已读', { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('案件通知详情展示案件ID、案件编号、客户名称和已读状态')
    await page.screenshot({ path: path.join(screenshotDir, `notification-case-detail-${runId}.png`), fullPage: false })

    info('4. 从通知详情跳转到案件详情')
    await Promise.all([
      page.waitForURL(url => String(url).includes('/cases') && String(url).includes(`caseId=${caseId}`), { timeout: assertionTimeout }),
      detailDialog.getByRole('button', { name: '查看案件' }).click()
    ])
    const caseDetailDialog = page.locator('.el-dialog').filter({ hasText: '案件详情' }).last()
    await caseDetailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await caseDetailDialog.getByText(caseNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await caseDetailDialog.getByText(customerName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await caseDetailDialog.getByText(alertSummary, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('案件通知可跳转并打开对应案件详情')
    await page.screenshot({ path: path.join(screenshotDir, `notification-related-case-${runId}.png`), fullPage: false })

    await assertNoOverlay(page, '通知中心案件通知 UI 闭环')
    await assertNoRuntimeIssues('通知中心案件通知 UI 闭环', issueStart)
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
