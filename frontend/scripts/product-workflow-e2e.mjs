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
const productCode = `${prefix}_PRODUCT_UI_${numericRun}`
const productName = `${prefix}尊享跨境投连寿险${numericRun.slice(-6)}`
const updatedProductName = `${prefix}尊享跨境投连寿险升级版${numericRun.slice(-6)}`
const productDescription = `面向高净值客户的跨境投资连结型寿险产品，覆盖大额趸交、受益人变更和跨境资金流风险识别。${runId}`
const updatedDescription = `已完成 UI E2E 编辑校验的投连寿险产品，纳入反洗钱产品风险评估闭环。${runId}`

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
      'Unable to launch a browser for product-workflow-e2e.',
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

async function fillFormInput(scope, label, value) {
  await scope.locator('.el-form-item').filter({ hasText: label }).locator('input').fill(value)
}

async function selectFormOption(page, scope, label, optionText) {
  await scope.locator('.el-form-item').filter({ hasText: label }).locator('input').click()
  await selectVisibleOption(page, optionText)
}

async function confirmMessageBox(page, apiPredicate, trigger) {
  await trigger()
  const box = page.locator('.el-message-box').last()
  await box.waitFor({ state: 'visible', timeout: assertionTimeout })
  return waitForApiJson(page, apiPredicate, () => box.getByRole('button', { name: /确认/ }).click())
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

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 产品管理 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  ProductCode: ${productCode}`)
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
    info('1. 登录并进入产品管理页面')
    await login(page)
    const issueStart = runtimeIssues.length
    await page.goto(`${frontendUrl}/products`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('新建产品').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/products')

    info('2. 新建反洗钱测试产品')
    await page.getByRole('button', { name: '新建产品' }).click()
    const createDialog = page.locator('.el-dialog').filter({ hasText: '新建产品' }).last()
    await createDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await fillFormInput(createDialog, '产品编码', productCode)
    await fillFormInput(createDialog, '产品名称', productName)
    await selectFormOption(page, createDialog, '产品类型', '投资连结保险')
    await selectFormOption(page, createDialog, '风险等级', '中风险')
    await createDialog.locator('.el-form-item').filter({ hasText: '产品描述' }).locator('textarea').fill(productDescription)
    const createResult = await waitForApiJson(
      page,
      response => response.url().endsWith('/api/products') && response.request().method() === 'POST',
      () => createDialog.getByRole('button', { name: '确认创建' }).click()
    )
    const productId = createResult.body?.data?.id
    if (createResult.body?.code === 200 && productId) {
      pass(`产品创建成功，id=${productId}`)
    } else {
      fail('产品创建接口返回异常', JSON.stringify(createResult.body, null, 2))
    }
    await page.getByText(productCode, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(productName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `product-created-${runId}.png`), fullPage: false })

    info('3. 按产品编码查询并查看详情')
    await page.locator('.filter-card .el-form-item').filter({ hasText: '产品编码' }).locator('input').fill(productCode)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/products/page') && response.request().method() === 'GET',
      () => page.getByRole('button', { name: '查询' }).click()
    )
    await page.getByText(productCode, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('产品编码筛选返回新建产品')

    await page.getByRole('button', { name: '详情' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '产品详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(productCode, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(productDescription, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('产品详情展示编码、名称和简介')
    await page.screenshot({ path: path.join(screenshotDir, `product-detail-before-assess-${runId}.png`), fullPage: false })
    await detailDialog.locator('.el-dialog__headerbtn').click()
    await detailDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('4. 编辑产品名称和描述')
    await page.getByRole('button', { name: '编辑' }).first().click()
    const editDialog = page.locator('.el-dialog').filter({ hasText: '编辑产品' }).last()
    await editDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await fillFormInput(editDialog, '产品名称', updatedProductName)
    await editDialog.locator('.el-form-item').filter({ hasText: '产品描述' }).locator('textarea').fill(updatedDescription)
    const updateResult = await waitForApiJson(
      page,
      response => response.url().includes(`/api/products/${productId}`) && response.request().method() === 'PUT',
      () => editDialog.getByRole('button', { name: '保存修改' }).click()
    )
    if (updateResult.body?.code === 200) {
      pass('产品编辑保存成功')
    } else {
      fail('产品编辑接口返回异常', JSON.stringify(updateResult.body, null, 2))
    }
    await page.getByText(updatedProductName, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })

    info('5. 发起产品风险评估')
    const assessResult = await confirmMessageBox(
      page,
      response => response.url().includes(`/api/products/${productId}/assess`) && response.request().method() === 'POST',
      () => page.getByRole('button', { name: '风险评估' }).first().click()
    )
    const totalScore = assessResult.body?.data?.totalScore
    const riskLevel = assessResult.body?.data?.riskLevel
    if (assessResult.body?.code === 200 && typeof totalScore === 'number' && riskLevel === 'HIGH') {
      pass(`产品风险评估完成，totalScore=${totalScore}，riskLevel=${riskLevel}`)
    } else {
      fail('产品风险评估接口返回异常', JSON.stringify(assessResult.body, null, 2))
    }
    await page.getByText('风险评估完成').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('高风险', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `product-assessed-${runId}.png`), fullPage: false })

    info('6. 复查评估历史')
    await page.getByRole('button', { name: '详情' }).first().click()
    const finalDetailDialog = page.locator('.el-dialog').filter({ hasText: '产品详情' }).last()
    await finalDetailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText(updatedDescription, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText('系统风险评估', { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await finalDetailDialog.getByText(String(totalScore), { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('产品详情展示风险评估历史')

    await assertNoOverlay(page, '产品管理 UI 闭环')
    await assertNoRuntimeIssues('产品管理 UI 闭环', issueStart)
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
