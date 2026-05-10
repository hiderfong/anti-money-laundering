import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const frontendUrl = process.env.FRONTEND_URL || process.env.BASE_URL || 'http://127.0.0.1:5173'
const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || 'admin123'
const runId = process.env.E2E_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
const screenshotDir = process.env.SCREENSHOT_DIR || path.join('/tmp', 'aml-frontend-browser-e2e')
const headless = process.env.HEADLESS !== 'false'
const slowMo = Number(process.env.PLAYWRIGHT_SLOW_MO || 0)
const navigationTimeout = Number(process.env.PLAYWRIGHT_NAVIGATION_TIMEOUT || 90000)
const assertionTimeout = Number(process.env.PLAYWRIGHT_ASSERTION_TIMEOUT || 20000)

const routes = [
  { path: '/dashboard', signal: '反洗钱运营态势' },
  { path: '/system', signal: '用户管理' },
  { path: '/kyc', signal: '客户列表' },
  { path: '/monitoring', signal: '交易编号' },
  { path: '/alerts', signal: '预警列表' },
  { path: '/products', signal: '产品名称' }
]

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
      'Unable to launch a browser for frontend-browser-e2e.',
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

async function waitForRouteReady(page, route) {
  await page.goto(`${frontendUrl}${route.path}`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
  await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
  await page.waitForTimeout(500)
}

async function checkRoute(page, route) {
  const issueStart = runtimeIssues.length
  await waitForRouteReady(page, route)

  const mainText = await pageText(page, 'main')
  if (mainText.includes(route.signal)) {
    pass(`${route.path} 渲染核心内容`)
  } else {
    fail(`${route.path} 缺少核心内容`, `expected text: ${route.signal}`)
  }

  await assertNoOverlay(page, route.path)

  const newIssues = runtimeIssues.slice(issueStart)
  if (newIssues.length === 0) {
    pass(`${route.path} 无新增 console/pageerror`)
  } else {
    fail(`${route.path} 出现新增 console/pageerror`, JSON.stringify(newIssues, null, 2))
  }
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 前端浏览器 E2E 回归')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  HEADLESS: ${headless}`)
  console.log(`  NAVIGATION_TIMEOUT: ${navigationTimeout}ms`)
  console.log('')

  await mkdir(screenshotDir, { recursive: true })
  const browser = await launchBrowser()
  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
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
    info('1. 登录页面')
    await page.goto(`${frontendUrl}/login`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.locator('input[placeholder="用户名"]').fill(username)
    await page.locator('input[placeholder="密码"]').fill(password)
    await Promise.all([
      page.waitForURL('**/dashboard', { timeout: assertionTimeout }),
      page.getByRole('button', { name: /登\s*录/ }).click()
    ])
    await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
    pass(`${username} 登录后进入 /dashboard`)

    info('2. 刷新登录态')
    const issueStart = runtimeIssues.length
    await page.reload({ waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
    const currentUrl = page.url()
    const mainText = await pageText(page, 'main')
    if (currentUrl.includes('/dashboard') && mainText.includes('反洗钱运营态势')) {
      pass('刷新后仍保持登录态')
    } else {
      fail('刷新后登录态异常', `url=${currentUrl}`)
    }
    await assertNoOverlay(page, '/dashboard refresh')
    const refreshIssues = runtimeIssues.slice(issueStart)
    if (refreshIssues.length === 0) {
      pass('/dashboard refresh 无新增 console/pageerror')
    } else {
      fail('/dashboard refresh 出现新增 console/pageerror', JSON.stringify(refreshIssues, null, 2))
    }

    info('3. /api/auth/me 权限契约')
    const me = await page.evaluate(async (id) => {
      const token = localStorage.getItem('aml_token')
      const response = await fetch('/api/auth/me', {
        headers: {
          Authorization: `Bearer ${token}`,
          'X-E2E-Run-Id': id
        }
      })
      return { status: response.status, body: await response.json() }
    }, runId)

    const roles = Array.isArray(me.body?.data?.roles) ? me.body.data.roles : []
    const permissions = Array.isArray(me.body?.data?.permissions) ? me.body.data.permissions : []
    if (me.status === 200 && roles.includes('ROLE_ADMIN') && permissions.includes('system:user')) {
      pass('/api/auth/me 返回 roles/permissions')
    } else {
      fail('/api/auth/me 权限契约异常', JSON.stringify({ status: me.status, roles, permissions }, null, 2))
    }

    info('4. 核心页面路由')
    for (const route of routes) {
      await checkRoute(page, route)
    }

    await page.goto(`${frontendUrl}/dashboard`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `dashboard-${runId}.png`), fullPage: false })
    await page.goto(`${frontendUrl}/products`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
    await page.screenshot({ path: path.join(screenshotDir, `products-${runId}.png`), fullPage: false })
    pass(`截图已输出到 ${screenshotDir}`)
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
