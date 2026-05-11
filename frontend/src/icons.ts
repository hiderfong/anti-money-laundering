/**
 * Element Plus Icons 按需注册
 *
 * 只注册项目实际使用的图标，避免全量引入整套图标库。
 * 如需新增图标，先在 .vue 文件中使用，再在下方引入注册。
 */
import {
  ArrowDown,
  Bell,
  Check,
  CircleCheckFilled,
  DataAnalysis,
  Document,
  Download,
  Expand,
  Fold,
  FolderOpened,
  Goods,
  Loading,
  Monitor,
  Moon,
  Notification,
  Odometer,
  Operation,
  Plus,
  Refresh,
  Search,
  Setting,
  Sunny,
  User,
  UserFilled,
  Warning,
  WarningFilled
} from '@element-plus/icons-vue'
import type { App, Component } from 'vue'

const icons: Record<string, Component> = {
  ArrowDown,
  Bell,
  Check,
  CircleCheckFilled,
  DataAnalysis,
  Document,
  Download,
  Expand,
  Fold,
  FolderOpened,
  Goods,
  Loading,
  Monitor,
  Moon,
  Notification,
  Odometer,
  Operation,
  Plus,
  Refresh,
  Search,
  Setting,
  Sunny,
  User,
  UserFilled,
  Warning,
  WarningFilled
}

export function registerIcons(app: App): void {
  for (const [name, component] of Object.entries(icons)) {
    app.component(name, component)
  }
}

export { icons }
