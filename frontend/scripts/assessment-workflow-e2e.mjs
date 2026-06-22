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
const assessmentYear = 2070 + (Number(numericRun.slice(-2)) % 30)
const assessmentTitle = `${assessmentYear}年年度风险自评估`
const rectificationIssue = `${prefix}自评估发现高风险客户复核频率不足 ${runId}`
const rectificationMeasure = `${prefix}补充高风险客户复核清单并完善名单筛查复核记录 ${runId}`
const responsiblePerson = '赵清妍'
const deadline = '2099-12-31'

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
      'Unable to launch a browser for assessment-workflow-e2e.',
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

async function confirmMessageBox(page, apiPredicate, trigger) {
  await trigger()
  const box = page.locator('.el-message-box').last()
  await box.waitFor({ state: 'visible', timeout: assertionTimeout })
  const confirmButton = box.locator('.el-message-box__btns .el-button--primary').last()
  await confirmButton.waitFor({ state: 'visible', timeout: assertionTimeout })
  return waitForApiJson(page, apiPredicate, () => confirmButton.click())
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
  console.log('  AML 风险自评估 UI E2E 闭环')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  AssessmentYear: ${assessmentYear}`)
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
    info('1. 登录并进入风险自评估页面')
    await login(page)
    const issueStart = runtimeIssues.length
    await page.goto(`${frontendUrl}/assessment`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.getByText('风险自评估管理').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('自评估风险画像').waitFor({ state: 'visible', timeout: assertionTimeout })
    await assertNoOverlay(page, '/assessment')

    info('2. 创建自评估任务')
    await page.getByRole('button', { name: /创建评估/ }).click()
    const createDialog = page.locator('.el-dialog').filter({ hasText: '创建评估' }).last()
    await createDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await fillFormInput(createDialog, '评估年度', assessmentYear)
    await selectFormOption(page, createDialog, '评估周期', '年度评估')
    await fillFormInput(createDialog, '评估人ID', 1)
    const createResult = await waitForApiJson(
      page,
      response => response.url().endsWith('/api/assessments') && response.request().method() === 'POST',
      () => createDialog.getByRole('button', { name: '确认创建' }).click()
    )
    const assessmentId = createResult.body?.data?.id
    if (createResult.body?.code === 200 && assessmentId) {
      pass(`自评估创建成功，id=${assessmentId}`)
    } else {
      fail('自评估创建接口返回异常', JSON.stringify(createResult.body, null, 2))
    }
    await page.getByText(assessmentTitle, { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `assessment-created-${runId}.png`), fullPage: false })

    info('3. 按启用指标提交评分')
    await page.getByRole('button', { name: '评分' }).first().click()
    const scoreDialog = page.locator('.el-dialog').filter({ hasText: '评估评分' }).last()
    await scoreDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await scoreDialog.getByText('固有风险', { exact: false }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await scoreDialog.getByText('控制有效性', { exact: false }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    const scoreResult = await waitForApiJson(
      page,
      response => response.url().includes('/api/assessments/score') && response.request().method() === 'POST',
      () => scoreDialog.getByRole('button', { name: '确认评分' }).click()
    )
    if (scoreResult.body?.code === 200) {
      pass('自评估指标评分提交成功')
    } else {
      fail('自评估评分接口返回异常', JSON.stringify(scoreResult.body, null, 2))
    }
    await page.getByText('评分成功').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('进行中', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })

    info('4. 完成并审批自评估')
    const completeResult = await confirmMessageBox(
      page,
      response => response.url().includes(`/api/assessments/${assessmentId}/complete`) && response.request().method() === 'POST',
      () => page.getByRole('button', { name: '完成' }).first().click()
    )
    const overallScore = completeResult.body?.data?.overallScore
    const riskLevel = completeResult.body?.data?.overallRiskLevel
    if (completeResult.body?.code === 200 && typeof overallScore === 'number' && riskLevel) {
      pass(`自评估完成，overallScore=${overallScore}，riskLevel=${riskLevel}`)
    } else {
      fail('完成自评估接口返回异常', JSON.stringify(completeResult.body, null, 2))
    }
    await page.getByText('已完成', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })

    await confirmMessageBox(
      page,
      response => response.url().includes(`/api/assessments/${assessmentId}/approve`) && response.request().method() === 'POST',
      () => page.getByRole('button', { name: '审批' }).first().click()
    )
    await page.getByText('审批通过').waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('已审批', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('自评估可审批通过')
    await page.screenshot({ path: path.join(screenshotDir, `assessment-approved-${runId}.png`), fullPage: false })

    info('5. 复查评估详情')
    await page.getByRole('button', { name: '详情' }).first().click()
    const detailDialog = page.locator('.el-dialog').filter({ hasText: '评估详情' }).last()
    await detailDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(assessmentTitle, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText(String(overallScore), { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await detailDialog.getByText('高风险客户、大额现金交易和复杂资金链路占比较高', { exact: false }).waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('评估详情展示综合评分和评分明细')
    await detailDialog.locator('.el-dialog__headerbtn').click()
    await detailDialog.waitFor({ state: 'hidden', timeout: assertionTimeout })

    info('6. 创建关联整改任务')
    await page.getByRole('tab', { name: '整改任务' }).click()
    await page.getByRole('button', { name: /创建整改任务/ }).click()
    const rectDialog = page.locator('.el-dialog').filter({ hasText: '创建整改任务' }).last()
    await rectDialog.waitFor({ state: 'visible', timeout: assertionTimeout })
    await fillFormInput(rectDialog, '关联评估ID', assessmentId)
    await rectDialog.locator('.el-form-item').filter({ hasText: '问题描述' }).locator('textarea').fill(rectificationIssue)
    await selectFormOption(page, rectDialog, '严重程度', '中')
    await rectDialog.locator('.el-form-item').filter({ hasText: '整改措施' }).locator('textarea').fill(rectificationMeasure)
    await fillFormInput(rectDialog, '责任部门', '合规部')
    await fillFormInput(rectDialog, '责任人', responsiblePerson)
    await fillFormInput(rectDialog, '截止日期', deadline)
    const rectResult = await waitForApiJson(
      page,
      response => response.url().endsWith('/api/assessments/rectifications') && response.request().method() === 'POST',
      () => rectDialog.getByRole('button', { name: '确认创建' }).click()
    )
    if (rectResult.body?.code === 200 && rectResult.body?.data?.id) {
      pass(`关联整改任务创建成功，id=${rectResult.body.data.id}`)
    } else {
      fail('关联整改任务创建接口返回异常', JSON.stringify(rectResult.body, null, 2))
    }
    await page.getByText(rectificationIssue, { exact: true }).waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.getByText('待整改', { exact: true }).first().waitFor({ state: 'visible', timeout: assertionTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `assessment-rectification-${runId}.png`), fullPage: false })

    await assertNoOverlay(page, '风险自评估 UI 闭环')
    await assertNoRuntimeIssues('风险自评估 UI 闭环', issueStart)
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
