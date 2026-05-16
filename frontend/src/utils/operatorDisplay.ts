const OPERATOR_DISPLAY_NAME_MAP: Record<string, string> = {
  admin: '系统管理员',
  system: '系统自动处理',
  e2e_admin: '系统管理员',
  e2e_seed_operator: '合规负责人',
  e2e_compliance: '合规审批员',
  e2e_investigator: '案件调查员',
  e2e_viewer: '只读观察员'
}

export interface OperatorUserInfo {
  username?: string
  realName?: string
}

export function formatOperatorName(value: unknown, emptyText = '-'): string {
  const raw = value == null ? '' : String(value).trim()
  if (!raw) return emptyText
  return OPERATOR_DISPLAY_NAME_MAP[raw] || raw
}

export function currentOperatorName(userInfo: OperatorUserInfo | null | undefined, fallback = '系统管理员'): string {
  return formatOperatorName(userInfo?.realName || userInfo?.username, fallback)
}
