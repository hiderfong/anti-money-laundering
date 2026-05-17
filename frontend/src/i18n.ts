import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import en from './locales/en'

type SupportedLocale = 'zh-CN' | 'en'

function isSupportedLocale(value: string | null): value is SupportedLocale {
  return value === 'zh-CN' || value === 'en'
}

// 从 localStorage 获取语言设置，默认中文
function getDefaultLocale(): SupportedLocale {
  const saved = localStorage.getItem('aml_locale')
  if (isSupportedLocale(saved)) {
    return saved
  }
  // 检测浏览器语言
  const browserLang = navigator.language
  if (browserLang.startsWith('en')) {
    return 'en'
  }
  return 'zh-CN'
}

const i18n = createI18n({
  legacy: false, // 使用 Composition API
  locale: getDefaultLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    en,
  },
})

document.documentElement.lang = i18n.global.locale.value

export default i18n

// 切换语言
export function setLocale(locale: SupportedLocale) {
  i18n.global.locale.value = locale
  localStorage.setItem('aml_locale', locale)
  document.documentElement.lang = locale
}

// 获取当前语言
export function getLocale(): SupportedLocale {
  return i18n.global.locale.value
}
