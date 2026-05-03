<template>
  <el-tooltip :content="isDark ? '切换浅色模式' : '切换暗色模式'" placement="bottom">
    <div class="theme-toggle" @click="toggle">
      <el-icon :size="18">
        <Moon v-if="!isDark" />
        <Sunny v-else />
      </el-icon>
    </div>
  </el-tooltip>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { Moon, Sunny } from '@element-plus/icons-vue'

const THEME_KEY = 'aml_theme'
const isDark = ref(false)

function applyTheme(dark: boolean) {
  if (dark) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

function toggle() {
  isDark.value = !isDark.value
  localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
  applyTheme(isDark.value)
}

watch(isDark, (val) => applyTheme(val))

onMounted(() => {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'dark') {
    isDark.value = true
  } else if (saved === 'light') {
    isDark.value = false
  } else {
    // Default: respect system preference
    isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  applyTheme(isDark.value)
})
</script>

<style scoped>
.theme-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-tertiary);
  transition: all 0.15s ease;
}

.theme-toggle:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}
</style>
