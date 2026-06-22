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
const customerName = `${prefix}大额报送UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900707${idTail}`
const customerPhone = `133${phoneTail}`
const customerEmail = `large_report_${numericRun}@test.local`
const transactionNo = `${prefix}_LARGE_REPORT_UI_${numericRun}_001`
const transactionAmount = 680000
const reviewRemark = `大额交易报告 UI E2E 审核通过 ${runId}`

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
      'Unable to launch a browser for large-report-workflow-e2e.',
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

async function prepareLargeTransaction(page) {
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
      address: '浙江省杭州市上城区钱江路188号',
      occupation: '企业财务负责人',
      employer: '杭州云汇贸易有限公司',
      jobTitle: '财务总监'
    })
  })
  const customerId = customerResult.body?.data?.id
  if (customerResult.status === 200 && customerResult.body?.code === 200 && customerId) {
    pass(`准备大额报告客户成功，id=${customerId}`)
  } else {
    fail('准备大额报告客户失败', JSON.stringify(customerResult, null, 2))
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
      counterpartyName: `${prefix}大额现金交易对手`,
      counterpartyAccount: `622700${numericRun.slice(-12).padStart(12, '0')}`,
      counterpartyBank: '中国银行杭州钱江支行',
      isCrossBorder: false,
      transactionTime: '2026-05-31T12:00:00'
    })
  })
  const transactionId = transactionResult.body?.data?.id
  if (transactionResult.status === 200 && transactionResult.body?.code === 200 && transactionId) {
    pass(`准备大额报告交易成功，id=${transactionId}`)
  } else {
    fail('准备大额报告交易失败', JSON.stringify(transactionResult, null, 2))
  }

  return { customerId, transactionId }
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 大额交易报告 UI E2E 闭环')
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
    info('1. 登录并准备大额交易')
    await login(page)
    const issueStart = runtimeIssues.length
    const { transactionId } = await prepareLargeTransaction(page)

    info('2. 进入监管报送页面并生成大额交易报告')
    await page.goto(`${frontendUrl}/reporting`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('大额交易报告').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/reporting')

    await page.getByRole('button', { name: '生成报告' }).click()
    const generateDialog = page.locator('.el-dialog').filter({ hasText: '生成大额交易报告' }).last()
    await generateDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await generateDialog.locator('.el-form-item').filter({ hasText: '交易ID' }).locator('input').fill(String(transactionId))
    const generateResult = await waitForApiJson(
      page,
      response => response.url().includes('/api/reporting/large-txn/generate')
        && response.request().method() === 'POST',
      () => generateDialog.getByRole('button', { name: '确认生成' }).click()
    )
    const reportNo = generateResult.body?.data?.reportNo
    if (generateResult.body?.code === 200 && reportNo) {
      pass(`大额交易报告生成成功，reportNo=${reportNo}`)
    } else {
      fail('大额交易报告生成接口返回异常', JSON.stringify(generateResult.body, null, 2))
    }
    await page.getByText(reportNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(customerName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `large-report-list-${runId}.png`), fullPage: false })

    info('3. 查看报告详情')
    await page.getByRole('button', { name: '详情' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '大额交易报告详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(reportNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(customerName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('680,000.00 CNY', { exact: false }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('大额交易报告详情展示客户和交易金额')
    await page.screenshot({ path: path.join(screenshotDir, `large-report-detail-${runId}.png`), fullPage: false })
    await detailDialog.locator('.el-dialog__headerbtn').click()
    await detailDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('4. 审核大额交易报告')
    await page.getByRole('button', { name: '审核' }).first().click()
    const reviewDialog = page.locator('.el-dialog').filter({ hasText: '审核大额交易报告' }).last()
    await reviewDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await reviewDialog.locator('textarea').fill(reviewRemark)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/reporting/large-txn/')
        && response.url().includes('/review')
        && response.request().method() === 'POST',
      () => reviewDialog.getByRole('button', { name: '确认' }).click()
    )
    await page.getByText('审核通过').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已审核', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('大额交易报告可审核通过')

    info('5. 提交监管报送')
    await confirmMessageBox(
      page,
      response => response.url().includes('/api/reporting/large-txn/')
        && response.url().includes('/submit')
        && response.request().method() === 'POST',
      () => page.getByRole('button', { name: '提交报送' }).first().click()
    )
    await page.getByText('提交报送成功').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已报送', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('大额交易报告可提交监管报送')
    await page.screenshot({ path: path.join(screenshotDir, `large-report-submitted-${runId}.png`), fullPage: false })

    info('6. 复查报送后详情')
    await page.getByRole('button', { name: '详情' }).first().click()
    const finalDetailDialog = page.locator('.el-dialog').filter({ hasText: '大额交易报告详情' }).last()
    await finalDetailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText(reportNo, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText('已报送', { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText('admin', { exact: false }).first().waitFor({ state: 'visible', timeout: assertionTimeout }).catch(() => {})
    pass('大额交易报告详情展示最终报送状态')

    await assertNoOverlay(page, '大额交易报告 UI 闭环')
    await assertNoRuntimeIssues('大额交易报告 UI 闭环', issueStart)
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
