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
const customerName = `${prefix}交易监测UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900303${idTail}`
const customerPhone = `137${phoneTail}`
const customerEmail = `monitoring_${numericRun}@test.local`
const transactionNo = `${prefix}_MON_UI_${numericRun}_001`
const transactionAmount = 268000

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
      'Unable to launch a browser for monitoring-workflow-e2e.',
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

async function fillFormItem(scope, label, value) {
  const item = scope.locator('.el-form-item').filter({ hasText: label }).first()
  await item.locator('input').first().fill(String(value), { timeout: assertionTimeout })
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

async function prepareCustomerAndTransaction(page) {
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
      address: '北京市朝阳区建国路88号',
      occupation: '企业财务负责人',
      employer: '北京云澜科技有限公司',
      jobTitle: '财务经理'
    })
  })
  const customerId = customerResult.body?.data?.id
  if (customerResult.status === 200 && customerResult.body?.code === 200 && customerId) {
    pass(`准备交易监测测试客户成功，id=${customerId}`)
  } else {
    fail('准备交易监测测试客户失败', JSON.stringify(customerResult, null, 2))
  }

  const transactionResult = await authedFetch(page, '/api/monitoring/transactions/ingest', {
    method: 'POST',
    body: JSON.stringify({
      transactionNo,
      customerId,
      transactionType: 'PREMIUM',
      amount: transactionAmount,
      currency: 'CNY',
      paymentMethod: 'CASH',
      channel: 'COUNTER',
      counterpartyName: `${prefix}华东资金结算中心`,
      counterpartyAccount: `622200${numericRun.slice(-12).padStart(12, '0')}`,
      counterpartyBank: '中国工商银行上海浦东支行',
      isCrossBorder: false,
      transactionTime: '2026-05-31T10:00:00'
    })
  })
  const transactionId = transactionResult.body?.data?.id
  if (transactionResult.status === 200 && transactionResult.body?.code === 200 && transactionId) {
    pass(`准备交易监测测试交易成功，id=${transactionId}`)
  } else {
    fail('准备交易监测测试交易失败', JSON.stringify(transactionResult, null, 2))
  }

  return { customerId, transactionId }
}

async function waitForCanvas(locator, label) {
  await locator.locator('canvas').first().waitFor({ state: 'visible', timeout: assertionTimeout })
  const canvasCount = await locator.locator('canvas').count()
  if (canvasCount > 0) {
    pass(`${label} 已渲染 canvas`)
  } else {
    fail(`${label} 未渲染 canvas`)
  }
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 交易监测 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  Transaction: ${transactionNo}`)
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
    info('1. 登录并准备测试客户与交易')
    await login(page)
    const issueStart = runtimeIssues.length
    const { customerId } = await prepareCustomerAndTransaction(page)

    info('2. 进入交易监测并按交易编号搜索')
    await page.goto(`${frontendUrl}/monitoring`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByRole('tab', { name: '交易监测' }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('交易关系图谱').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/monitoring')

    const transactionPane = page.locator('.el-tab-pane').filter({ hasText: '交易编号' }).first()
    await fillFormItem(transactionPane, '交易编号', transactionNo)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/monitoring/transactions/page')
        && response.request().method() === 'GET',
      () => transactionPane.getByRole('button', { name: '查询' }).click()
    )
    await page.getByText(transactionNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('交易列表可按新交易编号搜索命中')
    await page.screenshot({ path: path.join(screenshotDir, `monitoring-list-${runId}.png`), fullPage: false })

    info('3. 打开交易详情并验证资金流向图')
    await transactionPane.getByRole('button', { name: '详情' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '交易详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(transactionNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('资金流向图').waitFor({ state: 'visible', timeout: assertionTimeout })
    await waitForCanvas(detailDialog.locator('.tx-flow-chart'), '交易详情资金流向图')
    await page.screenshot({ path: path.join(screenshotDir, `monitoring-detail-${runId}.png`), fullPage: false })
    await detailDialog.locator('.el-dialog__headerbtn').click()
    await detailDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('4. 从交易行生成交易关系图谱')
    const graphResponsePromise = page.waitForResponse(
      response => response.url().includes('/api/monitoring/graph/')
        && response.request().method() === 'GET',
      { timeout: assertionTimeout }
    ).catch(() => null)
    await transactionPane.getByRole('button', { name: '图谱' }).first().click()
    await graphResponsePromise
    await waitForCanvas(page.locator('.graph-chart'), '交易关系图谱')
    await page.getByText(String(customerId), { exact: false }).first().waitFor({ state: 'visible', timeout: assertionTimeout }).catch(() => {})
    const graphSummaryText = await page.locator('.graph-summary').innerText({ timeout: assertionTimeout })
    if (/节点\s+\d+/.test(graphSummaryText) || graphSummaryText.includes('交易笔数')) {
      pass('交易关系图谱统计区已更新')
    } else {
      fail('交易关系图谱统计区未更新', graphSummaryText)
    }
    await page.screenshot({ path: path.join(screenshotDir, `monitoring-graph-${runId}.png`), fullPage: false })

    await assertNoOverlay(page, '交易监测 UI 闭环')
    await assertNoRuntimeIssues('交易监测 UI 闭环', issueStart)
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
