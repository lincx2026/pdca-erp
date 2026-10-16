import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/styles/index.scss'
import 'element-plus/dist/index.css'
import 'nprogress/nprogress.css'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import hasPermi from './directives/hasPermi'
import hasRole from './directives/hasRole'

const app = createApp(App)
const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia).use(router).use(ElementPlus)

// 全局注册 Element 图标 (方便 MenuTree + 模板直接用名称)
for (const [key, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, comp as any)
}

// 按钮级权限指令
app.directive('hasPermi', hasPermi)
app.directive('hasRole', hasRole)

app.mount('#app')
