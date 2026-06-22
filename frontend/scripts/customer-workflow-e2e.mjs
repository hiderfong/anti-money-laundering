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
const customerName = `${prefix}客户UI${numericRun.slice(-10)}`
const customerIdNumber = `11010119900101${idTail}`
const customerPhone = `139${phoneTail}`
const customerEmail = `ui_${numericRun}@test.local`

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
      'Unable to launch a browser for customer-workflow-e2e.',
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
  await item.locator('input').first().fill(value, { timeout: assertionTimeout })
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

async function getCustomerRelationshipGraph(page, customerId) {
  return page.evaluate(async ({ id, e2eRunId }) => {
    const token = localStorage.getItem('aml_token')
    const response = await fetch(`/api/kyc/customers/${id}/relationship-graph`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'X-E2E-Run-Id': e2eRunId
      }
    })
    return {
      status: response.status,
      body: await response.json()
    }
  }, { id: customerId, e2eRunId: runId })
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 客户管理 UI E2E 闭环')
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
    info('1. 登录并进入客户管理')
    await login(page)
    const issueStart = runtimeIssues.length
    await page.goto(`${frontendUrl}/kyc`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('客户列表').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('/kyc 客户列表渲染')
    await assertNoOverlay(page, '/kyc')

    info('2. 通过 UI 新增客户')
    await page.getByRole('button', { name: '新增客户' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '新增客户' }).last()
    await dialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await fillFormItem(dialog, '姓名/名称', customerName)
    await fillFormItem(dialog, '证件号码', customerIdNumber)
    await fillFormItem(dialog, '手机号码', customerPhone)
    await fillFormItem(dialog, '邮箱', customerEmail)

    const createResult = await waitForApiJson(
      page,
      response => response.url().includes('/api/kyc/customers')
        && response.request().method() === 'POST',
      () => dialog.getByRole('button', { name: '确定' }).click()
    )
    const createdCustomerId = createResult.body?.data?.id
    if (createResult.response.status() === 200 && createResult.body?.code === 200 && createdCustomerId) {
      pass(`新增客户成功，id=${createdCustomerId}`)
    } else {
      fail('新增客户 API 响应异常', JSON.stringify(createResult.body, null, 2))
    }
    await dialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('3. 搜索新客户并进入详情')
    const searchForm = page.locator('.el-form').first()
    await fillFormItem(searchForm, '客户名称', customerName)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/kyc/customers/page')
        && response.request().method() === 'GET',
      () => page.getByRole('button', { name: '查询' }).click()
    )
    await page.getByText(customerName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('客户列表可按新客户名称搜索命中')
    await page.screenshot({ path: path.join(screenshotDir, `customer-list-${runId}.png`), fullPage: false })

    await Promise.all([
      page.waitForURL(/\/kyc\/\d+$/, { timeout: assertionTimeout }),
      page.getByRole('button', { name: '查看' }).first().click()
    ])
    await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(customerName, { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('进入客户详情页并展示新客户名称')

    const detailCustomerId = page.url().split('/').pop()
    if (String(createdCustomerId) === detailCustomerId) {
      pass('详情页客户 ID 与新增响应一致')
    } else {
      fail('详情页客户 ID 与新增响应不一致', `created=${createdCustomerId}, detail=${detailCustomerId}`)
    }

    info('4. 验证客户画像与关系图谱')
    await page.getByText('客户画像').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('画像雷达图').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    const radarCanvasCount = await page.locator('.profile-radar canvas').count()
    if (radarCanvasCount > 0) {
      pass('客户画像雷达图已渲染 canvas')
    } else {
      fail('客户画像雷达图未渲染 canvas')
    }
    await page.screenshot({ path: path.join(screenshotDir, `customer-detail-${runId}.png`), fullPage: false })

    const relationshipApi = await getCustomerRelationshipGraph(page, createdCustomerId)
    const graphNodes = Array.isArray(relationshipApi.body?.data?.nodes) ? relationshipApi.body.data.nodes : []
    const hasCustomerNode = graphNodes.some(node => node.type === 'CUSTOMER' && node.label === customerName)
    if (relationshipApi.status === 200 && relationshipApi.body?.code === 200 && hasCustomerNode) {
      pass('客户关系图谱 API 返回客户节点')
    } else {
      fail('客户关系图谱 API 缺少客户节点', JSON.stringify({ status: relationshipApi.status, body: relationshipApi.body }, null, 2))
    }

    await page.getByRole('tab', { name: '关系图谱' }).click()
    await page.getByText('客户关系图谱').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('节点类型').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByRole('button', { name: '刷新图谱' }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('详情页关系图谱标签页可见并可刷新')
    await page.screenshot({ path: path.join(screenshotDir, `customer-relationship-${runId}.png`), fullPage: false })

    await assertNoOverlay(page, '客户管理 UI 闭环')
    await assertNoRuntimeIssues('客户管理 UI 闭环', issueStart)
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
