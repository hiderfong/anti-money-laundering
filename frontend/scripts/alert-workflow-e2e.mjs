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
const customerName = `${prefix}预警闭环UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900404${idTail}`
const customerPhone = `136${phoneTail}`
const customerEmail = `alert_${numericRun}@test.local`
const transactionNo = `${prefix}_ALERT_UI_${numericRun}_001`
const alertSummary = `${prefix}预警管理UI闭环验证 ${runId}`

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
      'Unable to launch a browser for alert-workflow-e2e.',
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

async function prepareAlertFixture(page) {
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
      occupation: '企业资金经理',
      employer: '上海嘉澜贸易有限公司',
      jobTitle: '资金经理'
    })
  })
  const customerId = customerResult.body?.data?.id
  if (customerResult.status === 200 && customerResult.body?.code === 200 && customerId) {
    pass(`准备预警测试客户成功，id=${customerId}`)
  } else {
    fail('准备预警测试客户失败', JSON.stringify(customerResult, null, 2))
  }

  const transactionResult = await authedFetch(page, '/api/monitoring/transactions/ingest', {
    method: 'POST',
    body: JSON.stringify({
      transactionNo,
      customerId,
      transactionType: 'PREMIUM',
      amount: 386000,
      currency: 'CNY',
      paymentMethod: 'TRANSFER',
      channel: 'ONLINE',
      counterpartyName: `${prefix}跨区域资金归集账户`,
      counterpartyAccount: `622600${numericRun.slice(-12).padStart(12, '0')}`,
      counterpartyBank: '招商银行深圳分行营业部',
      isCrossBorder: false,
      transactionTime: '2026-05-31T11:00:00'
    })
  })
  const transactionId = transactionResult.body?.data?.id
  if (transactionResult.status === 200 && transactionResult.body?.code === 200 && transactionId) {
    pass(`准备预警关联交易成功，id=${transactionId}`)
  } else {
    fail('准备预警关联交易失败', JSON.stringify(transactionResult, null, 2))
  }

  const alertResult = await authedFetch(page, '/api/alerts/manual', {
    method: 'POST',
    body: JSON.stringify({
      customerId,
      customerName,
      alertType: 'MANUAL',
      riskScore: 88,
      riskLevel: 'HIGH',
      sourceRuleCodes: 'MANUAL_REVIEW,COMPLEX_FLOW',
      alertSummary,
      relatedTransactionIds: String(transactionId),
      ruleCode: 'COMPLEX_FLOW',
      ruleName: '复杂资金流向人工复核',
      matchScore: 96,
      matchDetail: `交易 ${transactionNo} 触发预警管理 UI 闭环验证`
    })
  })
  const alert = alertResult.body?.data
  if (alertResult.status === 200 && alertResult.body?.code === 200 && alert?.id && alert?.alertNo) {
    pass(`人工创建预警成功，alertNo=${alert.alertNo}`)
  } else {
    fail('人工创建预警失败', JSON.stringify(alertResult, null, 2))
  }

  return { customerId, transactionId, alertId: alert?.id, alertNo: alert?.alertNo }
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 预警管理 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  Alert summary: ${alertSummary}`)
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
    info('1. 登录并准备客户、交易和人工预警')
    await login(page)
    const issueStart = runtimeIssues.length
    const { alertNo } = await prepareAlertFixture(page)

    info('2. 进入预警管理并验证列表展示')
    await page.goto(`${frontendUrl}/alerts`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('预警列表').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(alertNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(alertSummary, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/alerts')
    pass('预警列表展示人工创建预警')
    await page.screenshot({ path: path.join(screenshotDir, `alert-list-${runId}.png`), fullPage: false })

    info('3. 打开预警详情并验证处置链路')
    await page.getByRole('button', { name: '查看' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '预警详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(alertNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('预警处置链路图').waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('关联交易').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('预警详情展示真实处置链路')
    await page.screenshot({ path: path.join(screenshotDir, `alert-detail-${runId}.png`), fullPage: false })
    await detailDialog.locator('.el-dialog__headerbtn').click()
    await detailDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('4. 通过 UI 指派预警')
    await page.getByRole('button', { name: '指派' }).first().click()
    const assignDialog = page.locator('.el-dialog').filter({ hasText: '指派预警' }).last()
    await assignDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await assignDialog.locator('.el-select').click()
    await selectVisibleOption(page, '管理员')
    await waitForApiJson(
      page,
      response => response.url().includes('/api/alerts/assign') && response.request().method() === 'POST',
      () => assignDialog.getByRole('button', { name: '确认指派' }).click()
    )
    await page.getByText('指派成功').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('预警指派接口和页面反馈正常')

    info('5. 通过 UI 处理预警并确认可疑')
    await page.getByText(alertNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByRole('button', { name: '处理' }).first().click()
    const processDialog = page.locator('.el-dialog').filter({ hasText: '处理预警' }).last()
    await processDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await processDialog.locator('textarea').fill(`预警管理 UI E2E 确认可疑 ${runId}`)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/alerts/process') && response.request().method() === 'POST',
      () => processDialog.getByRole('button', { name: '确认处理' }).click()
    )
    await page.getByText('处理成功').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已确认').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('预警处理接口和页面状态更新正常')
    await page.screenshot({ path: path.join(screenshotDir, `alert-processed-${runId}.png`), fullPage: false })

    await assertNoOverlay(page, '预警管理 UI 闭环')
    await assertNoRuntimeIssues('预警管理 UI 闭环', issueStart)
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
