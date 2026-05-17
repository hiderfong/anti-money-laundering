<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <el-button class="language-button" size="small" :aria-label="t('common.language')">
      <component :is="LanguageIcon" class="language-icon" />
      <span>{{ currentLabel }}</span>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="zh-CN" :class="{ active: locale === 'zh-CN' }">
          {{ t('common.chinese') }}
        </el-dropdown-item>
        <el-dropdown-item command="en" :class="{ active: locale === 'en' }">
          {{ t('common.english') }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'

type SupportedLocale = 'zh-CN' | 'en'

const LanguageIcon = {
  name: 'Language',
  render() {
    return h('svg', {
      xmlns: 'http://www.w3.org/2000/svg',
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': '2',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
    }, [
      h('path', { d: 'M5 8l6 6' }),
      h('path', { d: 'M4 14l6-6 2-3' }),
      h('path', { d: 'M2 5h12' }),
      h('path', { d: 'M7 2h1' }),
      h('path', { d: 'm22 22-5-10-5 10' }),
      h('path', { d: 'M14 18h6' }),
    ])
  },
}

const { locale, t } = useI18n()

const currentLabel = computed(() => locale.value === 'en' ? 'EN' : '中文')

function handleCommand(lang: string) {
  if (lang === 'zh-CN' || lang === 'en') {
    setLocale(lang as SupportedLocale)
  }
}
</script>

<style scoped>
.language-button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 62px;
  height: 30px;
  padding: 0 9px;
  border-radius: 8px;
  font-weight: 600;
}

.language-icon {
  width: 15px;
  height: 15px;
}

.active {
  color: var(--el-color-primary);
  font-weight: bold;
}
</style>
