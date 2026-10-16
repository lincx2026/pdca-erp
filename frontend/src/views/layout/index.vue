<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="aside-header">
        <img src="/logo.png" class="logo" />
        <transition name="fade">
          <span v-show="!collapsed" class="brand">智健ERP系统</span>
        </transition>
      </div>
      <el-scrollbar class="aside-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          :collapse-transition="false"
          :unique-opened="true"
          background-color="#1F2937"
          text-color="#D1D5DB"
          active-text-color="#F59E0B"
          @select="onMenuSelect"
        >
          <MenuTree :items="userStore.menus" />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- 主区 -->
    <el-container>
      <el-header class="header">
        <div class="left flex items-center gap-8">
          <el-button text circle @click="collapsed = !collapsed">
            <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title">{{ route.meta.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="right flex items-center gap-16">
          <el-tooltip content="接口文档">
            <el-button link target="_blank" @click="$router.resolve && window.open('/doc.html','_blank')">
              <el-icon><Reading /></el-icon> 文档
            </el-button>
          </el-tooltip>
          <NoticeDropdown />
          <el-dropdown @command="onCommand">
            <div class="user-info flex items-center gap-8">
              <el-avatar :size="32" :icon="UserFilled" />
              <span class="name">{{ userStore.userInfo?.nickName || '未登录' }}</span>
              <el-icon><CaretBottom /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile"><el-icon><User /></el-icon> 个人中心</el-dropdown-item>
                <el-dropdown-item command="refresh"><el-icon><Refresh /></el-icon> 刷新页面</el-dropdown-item>
                <el-dropdown-item divided command="logout"><el-icon><SwitchButton /></el-icon> 退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>

      <el-footer class="footer">
        <span>© {{ year }} {{ companyName }} · 智健ERP系统 v1.0.0 · 自研ERP系统 · 阶段一骨架</span>
      </el-footer>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  UserFilled, CaretBottom, User, Refresh, SwitchButton,
  Fold, Expand, Reading
} from '@element-plus/icons-vue'
import MenuTree from './MenuTree.vue'
import NoticeDropdown from '@/components/NoticeDropdown.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const companyName = computed(() => import.meta.env.VITE_COMPANY_NAME)
const year = new Date().getFullYear()
const collapsed = ref(false)

// 图标: 必须 markRaw 避免响应式代理告警
Object.assign(window, {}) // 占位防告警
void markRaw

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗?', '提示', { type: 'warning' })
      .then(() => {
        userStore.logout()
        ElMessage.success('已退出')
        router.replace('/login')
      }).catch(() => {})
  } else if (cmd === 'refresh') {
    window.location.reload()
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}

/** Element Plus el-menu @select: 手动用 index (已在 MenuTree 里算好完整路径) 做跳转 */
function onMenuSelect(index: string, indexPath: string[], item: any) {
  console.log('[layout] onMenuSelect', { index, indexPath, route: item?.route })
  window.__menuDebug = JSON.stringify({ index, indexPath })
  if (!index) return
  router.push(index)
}
</script>

<style lang="scss" scoped>
.layout { height: 100vh; width: 100%; }

.aside {
  background: #1F2937; color: #D1D5DB; transition: width .2s;
  display: flex; flex-direction: column;
  :deep(.el-menu) { border-right: none; }
}
.aside-header {
  height: 60px; padding: 0 16px; display: flex; align-items: center; gap: 10px;
  border-bottom: 1px solid #374151;
  .logo { width: 32px; height: 32px; flex-shrink: 0; }
  .brand { font-weight: 700; color: #fff; letter-spacing: 1px; }
}
.aside-scroll { flex: 1; }

.header {
  height: 60px; background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 20px;
  display: flex; align-items: center; justify-content: space-between;
  .name { font-size: 14px; color: #1f2937; cursor: default; }
}
.main {
  background: #f3f4f6; padding: 16px; overflow: auto;
}
.footer {
  height: 40px; background: #fff; border-top: 1px solid #e5e7eb;
  color: #6b7280; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
}

/* 过渡动画 */
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.fade-transform-enter-active, .fade-transform-leave-active { transition: all .25s; }
.fade-transform-enter-from { opacity: 0; transform: translateX(20px); }
.fade-transform-leave-to { opacity: 0; transform: translateX(-20px); }
</style>
