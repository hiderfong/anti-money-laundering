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
const customerName = `${prefix}筛查UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900202${idTail}`
const customerPhone = `138${phoneTail}`
const customerEmail = `screening_${numericRun}@test.local`
const whitelistReason = `${prefix}名单筛查UI白名单验证 ${runId}`

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
      'Unable to launch a browser for screening-workflow-e2e.',
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

async function createCustomerByApi(page) {
  return page.evaluate(async ({ e2eRunId, payload }) => {
    const token = localStorage.getItem('aml_token')
    const response = await fetch('/api/kyc/customers', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'X-E2E-Run-Id': e2eRunId
      },
      body: JSON.stringify(payload)
    })
    return {
      status: response.status,
      body: await response.json()
    }
  }, {
    e2eRunId: runId,
    payload: {
      customerType: 'INDIVIDUAL',
      name: customerName,
      nationality: 'CN',
      idType: 'IDCARD',
      idNumber: customerIdNumber,
      phone: customerPhone,
      email: customerEmail,
      address: '上海市浦东新区世纪大道100号',
      occupation: '企业财务负责人',
      employer: '上海云澜贸易有限公司',
      jobTitle: '财务经理'
    }
  })
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 名单筛查 UI E2E 闭环')
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
    info('1. 登录并准备测试客户')
    await login(page)
    const issueStart = runtimeIssues.length

    const customerResult = await createCustomerByApi(page)
    const createdCustomerId = customerResult.body?.data?.id
    if (customerResult.status === 200 && customerResult.body?.code === 200 && createdCustomerId) {
      pass(`准备名单筛查测试客户成功，id=${createdCustomerId}`)
    } else {
      fail('准备名单筛查测试客户失败', JSON.stringify(customerResult, null, 2))
    }

    info('2. 进入名单筛查并触发单笔筛查')
    await page.goto(`${frontendUrl}/screening`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByRole('tab', { name: '名单筛查' }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('筛查结果').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/screening')

    const screeningPane = page.locator('.el-tab-pane').filter({ hasText: '触发筛查' }).first()
    await fillFormItem(screeningPane, '客户ID', createdCustomerId)
    const screenPostPromise = page.waitForResponse(
      response => response.url().includes('/api/screening/screen') && response.request().method() === 'POST',
      { timeout: assertionTimeout }
    )
    const screenReloadPromise = page.waitForResponse(
      response => response.url().includes('/api/screening/results') && response.request().method() === 'GET',
      { timeout: assertionTimeout }
    )
    await screeningPane.getByRole('button', { name: '触发筛查' }).click()
    const screenResponse = await screenPostPromise
    const screenBody = await screenResponse.json()
    if (screenResponse.status() === 200 && screenBody?.code === 200 && Number.isFinite(Number(screenBody?.data))) {
      pass(`单笔名单筛查提交成功，命中数=${screenBody.data}`)
    } else {
      fail('单笔名单筛查响应异常', JSON.stringify(screenBody, null, 2))
    }
    await screenReloadPromise.catch(() => {})
    await page.screenshot({ path: path.join(screenshotDir, `screening-single-${runId}.png`), fullPage: false })

    info('3. 通过 UI 执行批量筛查')
    await screeningPane.getByRole('button', { name: '批量筛查' }).click()
    const batchDialog = page.locator('.el-dialog').filter({ hasText: '批量筛查' }).last()
    await batchDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await batchDialog.locator('input[placeholder="客户ID"]').first().fill(String(createdCustomerId))
    const batchPostPromise = page.waitForResponse(
      response => response.url().includes('/api/screening/batch-screen') && response.request().method() === 'POST',
      { timeout: assertionTimeout }
    )
    const batchReloadPromise = page.waitForResponse(
      response => response.url().includes('/api/screening/results') && response.request().method() === 'GET',
      { timeout: assertionTimeout }
    )
    await batchDialog.getByRole('button', { name: '开始批量筛查' }).click()
    const batchResponse = await batchPostPromise
    const batchBody = await batchResponse.json()
    if (batchResponse.status() === 200 && batchBody?.code === 200 && Array.isArray(batchBody?.data)) {
      pass(`批量名单筛查提交成功，返回 ${batchBody.data.length} 条结果`)
    } else {
      fail('批量名单筛查响应异常', JSON.stringify(batchBody, null, 2))
    }
    await batchReloadPromise.catch(() => {})
    await batchDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('4. 验证白名单管理闭环')
    await page.getByRole('tab', { name: '白名单管理' }).click()
    await page.getByText('添加白名单').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    const whitelistPane = page.locator('.el-tab-pane').filter({ hasText: '白名单列表' }).first()
    await fillFormItem(whitelistPane, '客户ID', createdCustomerId)
    await fillFormItem(whitelistPane, '客户姓名', customerName)
    await fillFormItem(whitelistPane, '名单项ID', createdCustomerId)
    await fillFormItem(whitelistPane, '排除原因', whitelistReason)

    const whitelistPostPromise = page.waitForResponse(
      response => response.url().includes('/api/screening/whitelist') && response.request().method() === 'POST',
      { timeout: assertionTimeout }
    )
    const whitelistReloadPromise = page.waitForResponse(
      response => response.url().includes('/api/screening/whitelist') && response.request().method() === 'GET',
      { timeout: assertionTimeout }
    )
    await whitelistPane.getByRole('button', { name: '添加白名单' }).click()
    const whitelistResponse = await whitelistPostPromise
    const whitelistBody = await whitelistResponse.json()
    if (whitelistResponse.status() === 200 && whitelistBody?.code === 200) {
      pass('白名单新增提交成功')
    } else {
      fail('白名单新增响应异常', JSON.stringify(whitelistBody, null, 2))
    }
    await whitelistReloadPromise.catch(() => {})
    await page.getByText(whitelistReason, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('白名单列表展示新增记录')
    await page.screenshot({ path: path.join(screenshotDir, `screening-whitelist-${runId}.png`), fullPage: false })

    await assertNoOverlay(page, '名单筛查 UI 闭环')
    await assertNoRuntimeIssues('名单筛查 UI 闭环', issueStart)
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
