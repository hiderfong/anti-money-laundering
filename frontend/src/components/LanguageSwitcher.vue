<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <el-button :icon="LanguageIcon" circle size="small" />
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="zh-CN" :class="{ active: locale === 'zh-CN' }">
          中文
        </el-dropdown-item>
        <el-dropdown-item command="en" :class="{ active: locale === 'en' }">
          English
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { h } from 'vue'
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

const { locale } = useI18n()

function handleCommand(lang: string) {
  if (lang === 'zh-CN' || lang === 'en') {
    setLocale(lang satisfies SupportedLocale)
  }
}
</script>

<style scoped>
.active {
  color: var(--el-color-primary);
  font-weight: bold;
}
</style>
