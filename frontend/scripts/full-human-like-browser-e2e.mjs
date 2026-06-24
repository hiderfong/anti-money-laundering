import { chromium } from 'playwright'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

// 全量人工仿真巡检脚本：
// 1. 只做登录、浏览、查询和截图，不创建、审批或关闭业务数据；
// 2. UI 巡检负责发现页面渲染、路由守卫和控制台错误；
// 3. API 核对负责确认测试数据、图谱和处置链路是否真正贯通。
const frontendUrl = (process.env.FRONTEND_URL || process.env.BASE_URL || 'http://127.0.0.1:5173').replace(/\/$/, '')
const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || ['admin', '123'].join('')
const runId = process.env.E2E_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
const artifactRoot = process.env.HUMAN_E2E_ARTIFACT_DIR || path.join('/tmp', 'aml-human-like-browser-e2e')
const artifactDir = path.join(artifactRoot, runId)
const screenshotDir = process.env.SCREENSHOT_DIR || path.join(artifactDir, 'screenshots')
const reportPath = process.env.HUMAN_E2E_REPORT || path.join(artifactDir, 'human-like-browser-e2e-report.md')
const headless = process.env.HEADLESS !== 'false'
const slowMo = Number(process.env.PLAYWRIGHT_SLOW_MO || 80)
const navigationTimeout = Number(process.env.PLAYWRIGHT_NAVIGATION_TIMEOUT || 90000)
const assertionTimeout = Number(process.env.PLAYWRIGHT_ASSERTION_TIMEOUT || 20000)
const browserIp = process.env.E2E_BROWSER_IP || ''
const graphCustomerId = process.env.HUMAN_E2E_GRAPH_CUSTOMER_ID || '18'
const alertId = process.env.HUMAN_E2E_ALERT_ID || '17'
const densityThreshold = Number(process.env.HUMAN_E2E_DENSITY_THRESHOLD || 4)
const syncGraph = process.env.HUMAN_E2E_SYNC_GRAPH === 'true'

// 路由信号词选用页面内稳定业务字段，不强依赖标题，降低 UI 文案微调造成的误报。
const routes = [
  { path: '/dashboard', title: '仪表盘', signals: ['反洗钱运营态势', '仪表盘'] },
  { path: '/kyc', title: '客户管理', signals: ['客户列表', '姓名/名称'] },
  { path: `/kyc/${graphCustomerId}`, title: '客户详情', signals: ['客户画像', '客户关系图谱', '客户详情'] },
  { path: '/screening', title: '名单筛查', signals: ['名单筛查', '筛查结果'] },
  { path: '/monitoring', title: '交易监测', signals: ['交易监测', '交易关系图谱', '交易编号'] },
  { path: '/alerts', title: '预警管理', signals: ['预警列表', '预警总数'] },
  { path: '/cases', title: '案件管理', signals: ['案件管理', '案件列表', '案件编号', '案件状态', '新建案件'] },
  { path: '/reporting', title: '监管报送', signals: ['监管报送', '大额交易'] },
  { path: '/str-reports', title: 'STR报告', signals: ['STR报告', '可疑交易报告'] },
  { path: '/products', title: '产品管理', signals: ['产品管理', '产品名称'] },
  { path: '/assessment', title: '风险自评估', signals: ['自评估', '整改'] },
  { path: '/special-prevention', title: '特别预防', signals: ['特别预防', '名单命中'] },
  { path: '/rectifications', title: '整改中心', signals: ['整改中心', '整改任务'] },
  { path: '/investigations', title: '调查协查', signals: ['调查协查', '协查请求'] },
  { path: '/models', title: '模型管理', signals: ['模型管理', '模型治理'] },
  { path: '/regulation-library', title: '法规及资料库', signals: ['法规及资料库', '知识库'] },
  { path: '/notifications', title: '通知中心', signals: ['通知中心', '案件通知', '未读通知', '全部已读', '通知类型'] },
  { path: '/system', title: '系统管理', signals: ['系统管理', '用户管理'] }
]

// API 检查覆盖“页面有数据”和“业务链路可闭合”两层含义；
// 阈值按当前局域网测试数据设计，必要时可通过前置种子脚本补齐数据。
const apiChecks = [
  {
    name: '仪表盘概览有客户、交易、预警数据',
    url: '/api/dashboard/overview',
    validate: data => Number(data.totalCustomers || 0) > 0
      && Number(data.totalTransactions || 0) > 0
      && Number(data.totalAlerts || 0) > 0
  },
  {
    name: '客户列表具备批量测试数据',
    url: '/api/kyc/customers/page?page=1&size=5',
    validate: data => pageTotal(data) >= 20
  },
  {
    name: '产品列表具备测试产品',
    url: '/api/products/page?page=1&size=5',
    validate: data => pageTotal(data) >= 7
  },
  {
    name: '交易列表具备多场景交易',
    url: '/api/monitoring/transactions/page?page=1&size=5',
    validate: data => pageTotal(data) >= 60
  },
  {
    name: '筛查结果具备命中记录',
    url: '/api/screening/results?page=1&size=5',
    validate: data => pageTotal(data) >= 8
  },
  {
    name: '预警列表具备多等级预警',
    url: '/api/alerts/page?page=1&size=5',
    validate: data => pageTotal(data) >= 20
  },
  {
    name: '案件列表具备处置链路',
    url: '/api/cases/page?page=1&size=5',
    validate: data => pageTotal(data) >= 9
  },
  {
    name: 'STR报告列表具备报告内容',
    url: '/api/str-reports/page?page=1&size=5',
    validate: data => pageTotal(data) >= 7
  },
  {
    name: '大额交易报告列表具备报告数据',
    url: '/api/reporting/large-txn/page?page=1&size=5',
    validate: data => pageTotal(data) >= 10
  },
  {
    name: '风险自评估列表具备评估记录',
    url: '/api/assessments/list',
    validate: data => asArray(data).length >= 4
  },
  {
    name: '整改中心列表具备整改任务',
    url: '/api/rectifications?page=1&size=5',
    validate: data => pageTotal(data) >= 7
  },
  {
    name: '特别预防概览具备管控数据',
    url: '/api/special-prevention/overview',
    validate: data => Number(data.activeMeasures || data.measureCount || 0) > 0
      || Number(data.activeFreezeRecords || data.freezeRecordCount || 0) > 0
  },
  {
    name: '调查协查概览具备协查数据',
    url: '/api/investigations/overview',
    validate: data => Object.values(data || {}).some(value => Number(value || 0) > 0)
  },
  {
    name: '模型治理概览具备模型数据',
    url: '/api/models/overview',
    validate: data => Number(data.totalModels || data.modelCount || 0) >= 5
  },
  {
    name: 'AI风险复核池具备评分记录',
    url: '/api/ai/risk/review-pool/overview',
    validate: data => Number(data.totalScores || data.totalRecords || data.total || 0) >= 20
  },
  {
    name: '法规资料库具备资料和动态',
    url: '/api/regulation-library/overview',
    validate: data => Number(data.totalDocuments || data.documentCount || 0) > 0
      || Number(data.totalUpdates || data.updateCount || 0) > 0
  },
  {
    name: '通知中心具备通知记录',
    url: '/api/system/notifications/my?page=1&size=5',
    validate: data => pageTotal(data) > 0
  }
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
  /Running in development mode/i,
  /ResizeObserver loop/i,
  /\[ECharts\].*width.*height/i,
  /Added non-passive event listener/i
]

const results = []
const screenshots = []
const runtimeIssues = []

function pass(message, detail = '') {
  results.push({ ok: true, message, detail })
  console.log(`  \x1b[32m✓\x1b[0m ${message}`)
  if (detail) console.log(`    ${detail.slice(0, 800)}`)
}

function fail(message, detail = '') {
  results.push({ ok: false, message, detail })
  console.log(`  \x1b[31m✗\x1b[0m ${message}`)
  if (detail) console.log(`    Detail: ${detail.slice(0, 1200)}`)
}

function info(message) {
  console.log(`\x1b[33m▶ ${message}\x1b[0m`)
}

function sanitizeName(input) {
  return String(input || 'screenshot')
    .replace(/^\//, '')
    .replace(/[^\p{L}\p{N}_.-]+/gu, '-')
    .replace(/-+/g, '-')
    .slice(0, 80)
}

function isIgnoredConsole(message) {
  return ignoredConsolePatterns.some(pattern => pattern.test(message))
}

function unwrap(body) {
  if (body && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'data')) {
    return body.data
  }
  return body
}

function asArray(value) {
  if (Array.isArray(value)) return value
  if (Array.isArray(value?.list)) return value.list
  if (Array.isArray(value?.records)) return value.records
  if (Array.isArray(value?.rows)) return value.rows
  if (Array.isArray(value?.content)) return value.content
  return []
}

function pageTotal(value) {
  const total = value?.total ?? value?.totalElements ?? value?.totalCount ?? value?.count
  if (Number.isFinite(Number(total))) return Number(total)
  return asArray(value).length
}

function summarize(value) {
  return JSON.stringify(value, (_, item) => {
    if (typeof item === 'string' && item.length > 160) return `${item.slice(0, 160)}...`
    return item
  }, 2)
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
    return chromium.launch({ ...baseOptions, executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH })
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
      'Unable to launch a browser for full-human-like-browser-e2e.',
      'Install Chrome/Edge, set PLAYWRIGHT_CHANNEL=chrome, or run `npx playwright install chromium` in frontend.',
      ...errors.map(item => `- ${item}`)
    ].join('\n'))
  }
}

async function humanPause(page, min = 120, max = 420) {
  const delay = min + Math.floor(Math.random() * Math.max(1, max - min))
  await page.waitForTimeout(delay)
}

async function humanMove(page) {
  const viewport = page.viewportSize() || { width: 1280, height: 720 }
  await page.mouse.move(
    Math.floor(viewport.width * (0.25 + Math.random() * 0.5)),
    Math.floor(viewport.height * (0.2 + Math.random() * 0.5)),
    { steps: 6 }
  )
}

async function pageText(page, selector = 'body') {
  try {
    return await page.locator(selector).innerText({ timeout: assertionTimeout })
  } catch (error) {
    return ''
  }
}

async function screenshot(page, label, fullPage = false) {
  const file = path.join(screenshotDir, `${String(screenshots.length + 1).padStart(2, '0')}-${sanitizeName(label)}.png`)
  await page.screenshot({ path: file, fullPage })
  screenshots.push({ label, file })
  return file
}

async function authedFetch(page, apiPath, options = {}) {
  return page.evaluate(async ({ apiPath: targetPath, options: fetchOptions, id }) => {
    const token = localStorage.getItem('aml_token')
    const response = await fetch(targetPath, {
      ...fetchOptions,
      headers: {
        ...(fetchOptions.headers || {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        'X-E2E-Run-Id': id
      }
    })
    const text = await response.text()
    let body = null
    try {
      body = text ? JSON.parse(text) : null
    } catch (error) {
      body = { raw: text }
    }
    return { ok: response.ok, status: response.status, body }
  }, { apiPath, options, id: runId })
}

async function apiCheck(page, check) {
  const response = await authedFetch(page, check.url)
  const data = unwrap(response.body)
  const ok = response.ok && check.validate(data, response.body)
  if (ok) {
    pass(check.name, `GET ${check.url}`)
  } else {
    fail(check.name, summarize({ url: check.url, status: response.status, body: response.body }))
  }
  return data
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

async function login(page) {
  await page.goto(`${frontendUrl}/login`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
  await humanPause(page)

  const usernameInput = page.locator('input[placeholder="用户名"], input[placeholder="Username"], input[name="username"], input[type="text"]').first()
  const passwordInput = page.locator('input[placeholder="密码"], input[placeholder="Password"], input[name="password"], input[type="password"]').first()

  await usernameInput.fill(username)
  await humanPause(page, 80, 180)
  await passwordInput.fill(password)
  await humanMove(page)

  const loginButton = page.getByRole('button', { name: /登录|登\s*录|Login/i }).first()
  await Promise.all([
    page.waitForURL('**/dashboard', { timeout: assertionTimeout }),
    loginButton.click()
  ])
  await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
  pass(`${username} 登录后进入 /dashboard`)
}

async function checkRoute(page, route) {
  const issueStart = runtimeIssues.length
  await page.goto(`${frontendUrl}${route.path}`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
  await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
  await humanMove(page)
  await humanPause(page)
  await page.mouse.wheel(0, 260)
  await humanPause(page, 120, 260)

  const mainText = await pageText(page, 'main')
  const matched = route.signals.some(signal => mainText.includes(signal))
  if (matched) {
    pass(`${route.title} 页面渲染核心内容`)
  } else {
    fail(`${route.title} 页面缺少核心内容`, `expected one of: ${route.signals.join(', ')}`)
  }

  await assertNoOverlay(page, route.title)
  await screenshot(page, route.title)

  const newIssues = runtimeIssues.slice(issueStart)
  if (newIssues.length === 0) {
    pass(`${route.title} 页面无新增 console/pageerror`)
  } else {
    fail(`${route.title} 页面出现新增 console/pageerror`, summarize(newIssues))
  }
}

async function checkProfileContract(page) {
  const response = await authedFetch(page, '/api/auth/me')
  const data = unwrap(response.body)
  const roles = Array.isArray(data?.roles) ? data.roles : []
  const permissions = Array.isArray(data?.permissions) ? data.permissions : []

  if (response.ok && roles.includes('ROLE_ADMIN') && permissions.includes('system:user')) {
    pass('/api/auth/me 返回管理员 roles/permissions')
  } else {
    fail('/api/auth/me 权限契约异常', summarize({ status: response.status, roles, permissions, body: response.body }))
  }
}

async function checkGraphAndLinkedChains(page) {
  if (syncGraph) {
    const syncResponse = await authedFetch(
      page,
      '/api/monitoring/graph/sync-transactions?limit=100&sourceSystem=LAN_GRAPH',
      { method: 'POST' }
    )
    if (syncResponse.ok) {
      pass('图数据库同步接口可用', summarize(unwrap(syncResponse.body)))
    } else {
      fail('图数据库同步接口异常', summarize({ status: syncResponse.status, body: syncResponse.body }))
    }
  } else {
    pass('图数据库同步步骤已跳过', '如需测试同步，可设置 HUMAN_E2E_SYNC_GRAPH=true')
  }

  // 图谱能力需要验证“关系数据真的存在”，仅靠页面截图无法判断多层、环形、共同账户是否命中。
  const graphChecks = [
    {
      name: '客户关系图谱返回客户、产品、交易、预警等关系',
      url: `/api/kyc/customers/${graphCustomerId}/relationship-graph`,
      validate: data => asArray(data?.nodes).length > 0 && asArray(data?.links).length > 0
    },
    {
      name: '交易多层转账追踪识别长链路',
      url: `/api/monitoring/graph/multi-layer-transfer?customerId=${graphCustomerId}&maxDepth=4`,
      validate: data => asArray(data?.chains).length > 0 && data?.suspicious === true
    },
    {
      name: '交易环形关系检测可命中',
      url: `/api/monitoring/graph/ring-detection?customerId=${graphCustomerId}`,
      validate: data => data?.detected === true || asArray(data?.rings).length > 0
    },
    {
      name: '共同账户检测可命中',
      url: `/api/monitoring/graph/shared-accounts?customerId=${graphCustomerId}`,
      validate: data => data?.detected === true || asArray(data?.sharedAccounts).length > 0
    },
    {
      name: '异常网络密度检测可命中',
      url: `/api/monitoring/graph/network-density?customerId=${graphCustomerId}&densityThreshold=${densityThreshold}`,
      validate: data => data?.densityAlert === true
        || data?.detected === true
        || Number(data?.relatedPartyCount ?? data?.relatedCustomerCount ?? 0) >= densityThreshold
    },
    {
      name: '预警处置链路关联交易、案件、STR',
      url: `/api/alerts/${alertId}/disposition-chain`,
      validate: data => asArray(data?.transactions).length > 0
        && asArray(data?.cases).length > 0
        && asArray(data?.strReports).length > 0
        && asArray(data?.steps).length > 0
    }
  ]

  for (const check of graphChecks) {
    await apiCheck(page, check)
  }
}

async function checkHumanInteractions(page) {
  await page.goto(`${frontendUrl}/monitoring`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
  await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
  const customerInput = page.locator('main').getByPlaceholder('选择或输入客户ID').first()
  if (await customerInput.count()) {
    await customerInput.fill(graphCustomerId)
    await humanPause(page)
    await page.getByRole('button', { name: /生成图谱/ }).first().click()
    await page.waitForTimeout(1500)
    const text = await pageText(page, 'main')
    if (/节点|关系|风险判断/.test(text)) {
      pass('人工仿真交互：交易监测可输入客户ID并生成图谱')
    } else {
      fail('人工仿真交互：交易监测图谱生成后缺少统计信息')
    }
    await screenshot(page, '交易监测-生成图谱')
  } else {
    fail('人工仿真交互：未找到交易图谱客户ID输入框')
  }

  await page.goto(`${frontendUrl}/alerts`, { waitUntil: 'domcontentloaded', timeout: navigationTimeout })
  await page.locator('main').waitFor({ state: 'visible', timeout: assertionTimeout })
  await page.locator('main .el-table, main .table-card').first().waitFor({ state: 'visible', timeout: assertionTimeout })
  await page.waitForTimeout(800)
  const firstDetailButton = page.locator('main button:has-text("查看"), main .el-button:has-text("查看")').first()
  if (await firstDetailButton.count()) {
    await firstDetailButton.click()
    await page.getByText('预警详情').first().waitFor({ state: 'visible', timeout: assertionTimeout })
    pass('人工仿真交互：预警列表可打开详情弹窗')
    await screenshot(page, '预警详情弹窗')
  } else {
    fail('人工仿真交互：预警列表未找到查看按钮')
  }
}

async function writeReport() {
  const passed = results.filter(item => item.ok).length
  const failed = results.length - passed
  const lines = [
    '# 全量人工仿真浏览器 E2E 报告',
    '',
    `- 执行时间：${new Date().toISOString()}`,
    `- FRONTEND_URL：${frontendUrl}`,
    `- E2E_RUN_ID：${runId}`,
    `- 登录用户：${username}`,
    `- Headless：${headless}`,
    `- 图谱客户ID：${graphCustomerId}`,
    `- 预警ID：${alertId}`,
    `- 结果：${failed === 0 ? 'PASS' : 'FAIL'}（通过 ${passed}，失败 ${failed}）`,
    '',
    '## 检查明细',
    '',
    '| 序号 | 结果 | 检查项 | 说明 |',
    '|------|------|--------|------|',
    ...results.map((item, index) => `| ${index + 1} | ${item.ok ? 'PASS' : 'FAIL'} | ${item.message.replaceAll('|', '\\|')} | ${(item.detail || '').replace(/\s+/g, ' ').replaceAll('|', '\\|').slice(0, 280)} |`),
    '',
    '## 截图',
    '',
    ...screenshots.map(item => `- ${item.label}: ${item.file}`),
    ''
  ]

  await mkdir(path.dirname(reportPath), { recursive: true })
  await writeFile(reportPath, lines.join('\n'), 'utf8')
  console.log('')
  console.log(`报告已生成：${reportPath}`)
}

async function main() {
  console.log('')
  console.log('==========================================')
  console.log('  AML 全量人工仿真浏览器 E2E 巡检')
  console.log('==========================================')
  console.log(`  FRONTEND_URL: ${frontendUrl}`)
  console.log(`  E2E_RUN_ID: ${runId}`)
  console.log(`  HEADLESS: ${headless}`)
  console.log(`  ARTIFACT_DIR: ${artifactDir}`)
  console.log(`  E2E_BROWSER_IP: ${browserIp || '(default)'}`)
  console.log('')

  await mkdir(screenshotDir, { recursive: true })
  const browser = await launchBrowser()
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
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
    info('1. 登录与权限契约')
    await login(page)
    await checkProfileContract(page)
    await screenshot(page, '登录后仪表盘')

    info('2. 核心页面人工仿真巡检')
    for (const route of routes) {
      await checkRoute(page, route)
    }

    info('3. 关键业务数据覆盖核对')
    for (const check of apiChecks) {
      await apiCheck(page, check)
    }

    info('4. 图谱与处置链路核对')
    await checkGraphAndLinkedChains(page)

    info('5. 关键页面人工交互核对')
    await checkHumanInteractions(page)
  } finally {
    await writeReport()
    await browser.close()
  }

  const failed = results.filter(item => !item.ok)
  console.log('')
  console.log('==========================================')
  console.log(`  通过: ${results.length - failed.length}`)
  console.log(`  失败: ${failed.length}`)
  console.log('==========================================')

  if (failed.length > 0) {
    process.exit(1)
  }
}

main().catch(async error => {
  fail('脚本执行异常', error.stack || error.message)
  await writeReport().catch(() => {})
  process.exit(1)
})
