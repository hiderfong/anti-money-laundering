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
const issueDescription = `${prefix}监管检查发现高风险客户复核材料缺失 ${numericRun}`
const issueCategory = '客户尽调'
const responsibleDept = '合规部'
const responsiblePerson = '赵清妍'
const deadline = '2099-12-31'
const progressEvidence = `${prefix}已补充高风险客户复核材料、审批记录和名单筛查复核证据 ${runId}`
const verifyOpinion = `${prefix}整改证据完整，验证通过 ${runId}`

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
      'Unable to launch a browser for rectification-workflow-e2e.',
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
  await scope.locator('.el-form-item').filter({ hasText: label }).locator('input').fill(String(value))
}

async function selectFormOption(page, scope, label, optionText) {
  const formItem = scope.locator('.el-form-item').filter({ hasText: label })
  await formItem.locator('.el-select').first().click({ timeout: assertionTimeout })
  await selectVisibleOption(page, optionText)
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
  console.log('  AML 整改中心 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  Issue: ${issueDescription}`)
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
    info('1. 登录并进入整改中心')
    await login(page)
    const issueStart = runtimeIssues.length
    await page.goto(`${frontendUrl}/rectifications`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('整改中心').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('整改闭环状态').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/rectifications')

    info('2. 新增整改任务')
    await page.getByRole('button', { name: /新增整改/ }).click()
    const createDialog = page.locator('.el-dialog').filter({ hasText: '新增整改任务' }).last()
    await createDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await selectFormOption(page, createDialog, '问题来源', '监管检查')
    await fillFormInput(createDialog, '问题分类', issueCategory)
    await createDialog.locator('.el-form-item').filter({ hasText: '问题描述' }).locator('textarea').fill(issueDescription)
    await selectFormOption(page, createDialog, '严重程度', '高')
    await fillFormInput(createDialog, '责任部门', responsibleDept)
    await fillFormInput(createDialog, '责任人', responsiblePerson)
    await fillFormInput(createDialog, '整改期限', deadline)
    const createResult = await waitForApiJson(
      page,
      response => response.url().endsWith('/api/rectifications') && response.request().method() === 'POST',
      () => createDialog.getByRole('button', { name: '创建任务' }).click()
    )
    const taskId = createResult.body?.data?.id
    if (createResult.body?.code === 200 && taskId) {
      pass(`整改任务创建成功，id=${taskId}`)
    } else {
      fail('整改任务创建接口返回异常', JSON.stringify(createResult.body, null, 2))
    }
    await page.getByText(issueDescription, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('待整改', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `rectification-created-${runId}.png`), fullPage: false })

    info('3. 按责任人查询整改任务')
    await page.locator('.toolbar input[placeholder="责任人"]').fill(responsiblePerson)
    await waitForApiJson(
      page,
      response => response.url().includes('/api/rectifications') && response.request().method() === 'GET',
      () => page.getByRole('button', { name: /查询/ }).click()
    )
    await page.getByText(issueDescription, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText(responsiblePerson, { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('整改中心责任人筛选返回新建任务')

    info('4. 更新整改进度为已完成')
    await page.getByRole('button', { name: '更新进度' }).first().click()
    const progressDialog = page.locator('.el-dialog').filter({ hasText: '更新整改进度' }).last()
    await progressDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    const progressInput = progressDialog.locator('.el-input-number input').first()
    await progressInput.fill('100')
    await progressInput.press('Enter')
    await selectFormOption(page, progressDialog, '状态', '已完成')
    await progressDialog.locator('.el-form-item').filter({ hasText: '证据/说明' }).locator('textarea').fill(progressEvidence)
    const progressResult = await waitForApiJson(
      page,
      response => response.url().includes(`/api/rectifications/${taskId}/progress`) && response.request().method() === 'PUT',
      () => progressDialog.getByRole('button', { name: '保存进度' }).click()
    )
    if (progressResult.body?.code === 200) {
      pass('整改进度更新成功')
    } else {
      fail('整改进度更新接口返回异常', JSON.stringify(progressResult.body, null, 2))
    }
    await page.getByText('整改进度已更新').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已完成', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })

    info('5. 提交整改验证')
    await page.getByRole('button', { name: '验证' }).first().click()
    const verifyDialog = page.locator('.el-dialog').filter({ hasText: '整改验证' }).last()
    await verifyDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await selectFormOption(page, verifyDialog, '验证结果', '验证通过')
    await verifyDialog.locator('.el-form-item').filter({ hasText: '验证意见' }).locator('textarea').fill(verifyOpinion)
    const verifyResult = await waitForApiJson(
      page,
      response => response.url().includes(`/api/rectifications/${taskId}/verify`) && response.request().method() === 'POST',
      () => verifyDialog.getByRole('button', { name: '提交验证' }).click()
    )
    if (verifyResult.body?.code === 200) {
      pass('整改验证提交成功')
    } else {
      fail('整改验证接口返回异常', JSON.stringify(verifyResult.body, null, 2))
    }
    await page.getByText('整改验证已提交').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已验证', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('通过', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `rectification-verified-${runId}.png`), fullPage: false })

    info('6. 按已验证状态复查')
    await page.locator('.toolbar input[placeholder="责任人"]').fill(responsiblePerson)
    await page.locator('.toolbar .el-select').nth(1).locator('input').click()
    await selectVisibleOption(page, '已验证')
    await waitForApiJson(
      page,
      response => response.url().includes('/api/rectifications') && response.url().includes('status=VERIFIED') && response.request().method() === 'GET',
      () => page.getByRole('button', { name: /查询/ }).click()
    )
    await page.getByText(issueDescription, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('整改中心已验证状态筛选可复查闭环任务')

    await assertNoOverlay(page, '整改中心 UI 闭环')
    await assertNoRuntimeIssues('整改中心 UI 闭环', issueStart)
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
