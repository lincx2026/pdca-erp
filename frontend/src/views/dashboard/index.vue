<template>
  <div class="dashboard">
    <!-- 顶部 KPI 卡 -->
    <el-row :gutter="16" class="mb-16">
      <el-col v-for="c in kpiCards" :key="c.label" :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="kpi-card">
          <div class="flex justify-between items-center">
            <div>
              <p class="label">{{ c.label }}</p>
              <p class="value" :style="{ color: c.color }">{{ c.value }}</p>
              <p class="trend" :class="c.up ? 'up' : 'down'">
                <el-icon><component :is="c.up ? ArrowUp : ArrowDown" /></el-icon>
                {{ c.diff }} 较上周
              </p>
            </div>
            <div class="icon-box" :style="{ background: c.bg }">
              <el-icon :size="28" :color="c.color"><component :is="c.icon" /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 后端冒烟测试结果 -->
    <el-row :gutter="16" class="mb-16">
      <el-col :xs="24" :md="14">
        <el-card shadow="hover">
          <template #header>
            <div class="flex items-center gap-8 justify-between">
              <div class="flex items-center gap-8"><el-icon color="#1e3a8a"><Cpu /></el-icon> <b>后端服务联调测试</b></div>
              <el-button size="small" type="primary" :loading="loading" @click="pingBackend">重新测试</el-button>
            </div>
          </template>
          <el-alert v-if="error" :title="'❌ 后端未连通: ' + error" type="error" :closable="false" class="mb-16" />
          <el-alert v-if="result" :title="'✅ 后端联调成功: ' + result.msg" type="success" :closable="false" class="mb-16" />
          <el-descriptions v-if="result && result.data" :column="1" border size="small" title="Hello World 返回详情">
            <el-descriptions-item label="应用">{{ result.data.app }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ result.data.version }}</el-descriptions-item>
            <el-descriptions-item label="已加载模块">{{ (result.data.modules || []).join('、') }}</el-descriptions-item>
            <el-descriptions-item label="默认账号">
              <span v-for="(a, i) in (result.data.default_accounts || [])" :key="i" style="display:block">🔑 {{ a }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="服务器时间">{{ result.data.timestamp }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-if="!result && !error" description='点击右上角"重新测试"按钮触发请求' />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="hover">
          <template #header>
            <div class="flex items-center gap-8"><el-icon color="#10b981"><List /></el-icon> <b>开发进度 · 阶段一</b></div>
          </template>
          <div v-for="p in progress" :key="p.label" class="prog mb-16">
            <div class="flex justify-between"><span>{{ p.label }}</span><b>{{ p.percent }}%</b></div>
            <el-progress :percentage="p.percent" :color="p.color" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header><b>📋 下一步快速开始 (开发指引)</b></template>
          <el-timeline>
            <el-timeline-item type="primary" :timestamp="'第 1 步'">
              启动中间件 <code>docker compose up -d</code> (MySQL/Redis/MinIO)
            </el-timeline-item>
            <el-timeline-item type="success" :timestamp="'第 2 步'">
              后端 <code>cd backend &amp;&amp; mvn -pl pcba-erp-admin -am spring-boot:run</code>
            </el-timeline-item>
            <el-timeline-item type="warning" :timestamp="'第 3 步'">
              前端 <code>cd frontend &amp;&amp; pnpm i &amp;&amp; pnpm dev</code>
            </el-timeline-item>
            <el-timeline-item type="info" :timestamp="'第 4 步'">
              登录后点击"后端服务联调测试"卡片右上角按钮，验证接口 200
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover">
          <template #header><b>🧩 默认测试账号 (密码统一 admin123)</b></template>
          <el-table :data="accounts" size="small" stripe>
            <el-table-column prop="username" label="账号" />
            <el-table-column prop="role" label="角色" />
            <el-table-column prop="scope" label="权限范围" />
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="quickLogin(row.username)">快速登录</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Goods, User, Cpu, Document, ArrowUp, ArrowDown
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const kpiCards = [
  { label: '物料总数',   value: '16 大类',    diff: '+0',   up: true,  color: '#1E3A8A', bg: '#DBEAFE', icon: Goods },
  { label: '用户总数',   value: '3 账号',    diff: '+0',   up: true,  color: '#10B981', bg: '#D1FAE5', icon: User },
  { label: '工单占位',   value: '0',        diff: '+0',   up: true,  color: '#F59E0B', bg: '#FEF3C7', icon: Cpu },
  { label: '接口文档',   value: '已接入Knife4j', diff: '+1', up: true,  color: '#8B5CF6', bg: '#EDE9FE', icon: Document },
]

const progress = [
  { label: '中间件配置 (Docker Compose)',      percent: 100, color: '#10B981' },
  { label: '数据库 DDL (阶段一18张表+初始化)',  percent: 100, color: '#10B981' },
  { label: '后端 Spring Boot 多模块骨架',       percent: 100, color: '#10B981' },
  { label: '前端 Vue3 登录 + 布局 + 路由',      percent: 100, color: '#10B981' },
  { label: '阶段一权限/用户/角色/菜单页面',     percent: 5,   color: '#F59E0B' },
  { label: '阶段一物料/BOM/工艺完整功能',       percent: 5,   color: '#F59E0B' },
  { label: '阶段一冒烟测试 Hello 接口 200',      percent: 30,  color: '#3B82F6' },
]

const accounts = [
  { username: 'admin',    role: '超级管理员', scope: '所有菜单+所有权限' },
  { username: 'pdmadmin', role: '产品主管',   scope: '产品管理模块全部权限' },
  { username: 'buyer',    role: '普通用户',   scope: '产品只读 + 操作日志查看' },
]

// 后端接口联调
const loading = ref(false)
const result = ref<any>(null)
const error = ref<string | null>(null)
async function pingBackend() {
  loading.value = true
  error.value = null
  try {
    const res = await request.get('/system/hello')
    result.value = res as any
  } catch (e: any) {
    error.value = e.message || '请求失败 (请先启动后端)'
    result.value = null
  } finally {
    loading.value = false
  }
}

function quickLogin(username: string) {
  userStore.reset()
  localStorage.setItem('pcba_remember_user', username)
  ElMessage.success('已切换到用户 ' + username + ', 跳转到登录页')
  setTimeout(() => router.replace('/login'), 300)
}

onMounted(() => {
  // 页面一加载就自动尝试 ping 后端
  pingBackend()
})
</script>

<style lang="scss" scoped>
.kpi-card {
  .label { color: #6b7280; font-size: 13px; margin: 0; }
  .value { font-size: 26px; font-weight: 700; margin: 6px 0; }
  .trend  { font-size: 12px; margin: 0; &.up { color: #10B981; } &.down { color: #EF4444; } }
  .icon-box {
    width: 56px; height: 56px; border-radius: 12px;
    display: flex; align-items: center; justify-content: center;
  }
}
.prog .el-progress { margin-top: 4px; }
</style>
