import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElAlert,
  ElAside,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDivider,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPageHeader,
  ElPagination,
  ElPopconfirm,
  ElProgress,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElTooltip,
  provideGlobalConfig
} from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './assets/theme.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import { permDirective } from './directives/perm'
import { registerIcons } from './icons'

const app = createApp(App)

const elementComponents = [
  ElAlert,
  ElAside,
  ElBreadcrumb,
  ElBreadcrumbItem,
  ElButton,
  ElCard,
  ElCheckbox,
  ElCheckboxGroup,
  ElCol,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDivider,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPageHeader,
  ElPagination,
  ElPopconfirm,
  ElProgress,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElTooltip
]

for (const component of elementComponents) {
  app.use(component)
}

// 只注册项目实际使用的图标，避免把整套图标库打进主包。
registerIcons(app)

provideGlobalConfig({ locale: zhCn }, app, true)

// 注册权限指令: v-perm="'code'" 或 v-perm:role="'ROLE_ADMIN'"
app.directive('perm', permDirective)

app.use(createPinia())
app.use(router)
app.use(i18n)

app.mount('#app')
