<template>
  <div class="page-scm-ap-kanban">
    <el-form :inline="true" class="search-form" @submit.prevent>
      <el-form-item label="供应商">
        <el-select v-model="supplierId" placeholder="全部供应商" clearable filterable style="width:260px">
          <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refreshAll"><el-icon><RefreshLeft /></el-icon> 刷新</el-button>
      </el-form-item>
    </el-form>

    <!-- KPI 卡片 -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card">
          <div class="kpi-title">应付总额</div>
          <div class="kpi-value">{{ fmt(summary.totalPayableAmount) }}</div>
          <div class="kpi-sub">共 {{ summary.payableCount ?? 0 }} 单</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card unpaid">
          <div class="kpi-title">未付总额</div>
          <div class="kpi-value">{{ fmt(summary.unpaidAmount) }}</div>
          <div class="kpi-sub">未付单数 {{ summary.unpaidCount ?? 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card overdue">
          <div class="kpi-title">逾期未付</div>
          <div class="kpi-value">{{ fmt(summary.overdueAmount) }}</div>
          <div class="kpi-sub">逾期单数 {{ summary.overdueCount ?? 0 }} · 平均逾期 {{ summary.avgOverdueDays ?? 0 }} 天</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card month">
          <div class="kpi-title">本月到期</div>
          <div class="kpi-value">{{ fmt(summary.monthDueAmount) }}</div>
          <div class="kpi-sub">到期单数 {{ summary.monthDueCount ?? 0 }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="charts-row">
      <el-col :md="14">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">账龄分桶（按未付金额）</span></template>
          <div ref="agingRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :md="10">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">Top {{ topLimit }} 供应商未付金额</span></template>
          <div ref="topRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top:16px;">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span class="card-title">应付单明细（未付/逾期优先）</span>
          <el-tag type="info">共 {{ payableRows.length }} 行</el-tag>
        </div>
      </template>
      <el-table :data="payableRows" stripe border size="small" max-height="480">
        <el-table-column prop="payableNo" label="应付单号" width="180" />
        <el-table-column prop="supplierName" label="供应商" width="200" show-overflow-tooltip />
        <el-table-column prop="bizDate" label="业务日" width="110" />
        <el-table-column prop="dueDate" label="到期日" width="110" />
        <el-table-column prop="totalAmount" label="应付额" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已付" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unpaidAmount" label="未付" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ danger: Number(row.isOverdue) === 1 }">{{ fmt(row.unpaidAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="逾期" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="Number(row.isOverdue) === 1" type="danger">{{ row.overdueDays ?? 0 }}天</el-tag>
            <el-tag v-else type="success">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
              {{ statusMap[row.status]?.l ?? row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import { scmApPayableApi, scmSupplierApi } from '@/api'
import * as echarts from 'echarts'
import type { ScmSupplier } from '@/types/models'

defineOptions({ name: 'ScmApKanbanPage' })

function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }

const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '待对账', t: 'info' }, 1: { l: '对账中', t: 'warning' }, 2: { l: '对账完成', t: 'primary' },
  3: { l: '部分付', t: 'warning' }, 4: { l: '付款完成', t: 'success' }, 9: { l: '作废', t: 'info' }
}
const statusMap = STATUS_LABEL

const supplierId = ref<number | undefined>(undefined)
const suppliers = ref<ScmSupplier[]>([])
const topLimit = 10

const summary = ref<any>({
  totalPayableAmount: 0, payableCount: 0, unpaidAmount: 0, unpaidCount: 0,
  overdueAmount: 0, overdueCount: 0, avgOverdueDays: 0, monthDueAmount: 0, monthDueCount: 0
})
const agingData = ref<any[]>([])
const topData = ref<any[]>([])
const payableRows = ref<any[]>([])

const agingRef = ref<HTMLElement | null>(null)
const topRef = ref<HTMLElement | null>(null)
let agingChart: echarts.ECharts | null = null
let topChart: echarts.ECharts | null = null

async function loadSuppliers() {
  try {
    const resp = (await scmSupplierApi.options()) as any
    suppliers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载供应商失败') }
}

async function refreshAll() {
  try {
    const [s, t, a, list] = await Promise.all([
      scmApPayableApi.kanbanSummary(supplierId.value),
      scmApPayableApi.topSuppliers(supplierId.value, topLimit),
      scmApPayableApi.aging(supplierId.value),
      scmApPayableApi.list({
        pageNum: 1, pageSize: 100, status: undefined, payableNo: '', supplierName: '',
        bizDate: '', dueDate: '', isOverdue: undefined, supplierId: supplierId.value
      }) as any
    ])
    summary.value = (s as any)?.data || s || {}
    topData.value = (t as any)?.data || t || []
    agingData.value = (a as any)?.data || a || []
    const rows = list?.data?.rows || list?.rows || []
    payableRows.value = rows.slice().sort((x: any, y: any) => {
      const s1 = Number(x.isOverdue) === 1 ? 0 : 1
      const s2 = Number(y.isOverdue) === 1 ? 0 : 1
      if (s1 !== s2) return s1 - s2
      return Number(y.unpaidAmount || 0) - Number(x.unpaidAmount || 0)
    })
    await nextTick()
    renderCharts()
  } catch (e: any) { ElMessage.error(e?.message || '看板数据加载失败') }
}

function renderCharts() {
  if (!agingRef.value || !topRef.value) return
  if (!agingChart) agingChart = echarts.init(agingRef.value)
  if (!topChart) topChart = echarts.init(topRef.value)

  const agingLabels = agingData.value.map((d: any) => d.bucket || d.label || '未知')
  const agingValues = agingData.value.map((d: any) => Number(d.unpaidAmount ?? d.amount ?? 0))
  agingChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v: any) => '¥' + Number(v).toLocaleString() },
    grid: { left: 50, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: agingLabels, axisLabel: { interval: 0 } },
    yAxis: { type: 'value', axisLabel: { formatter: (v: any) => (v >= 10000 ? (v / 10000).toFixed(1) + '万' : v) } },
    series: [{
      type: 'bar', data: agingValues, barWidth: '42%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#409EFF' }, { offset: 1, color: '#79bbff' }
        ]), borderRadius: [6, 6, 0, 0]
      },
      label: { show: true, position: 'top', formatter: (p: any) => '¥' + Number(p.value).toLocaleString() }
    }]
  })

  const topLabels = topData.value.map((d: any) => d.supplierName || d.name || '未知').reverse()
  const topValues = topData.value.map((d: any) => Number(d.unpaidAmount ?? d.amount ?? 0)).reverse()
  topChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v: any) => '¥' + Number(v).toLocaleString() },
    grid: { left: 110, right: 30, top: 20, bottom: 30 },
    xAxis: { type: 'value', axisLabel: { formatter: (v: any) => (v >= 10000 ? (v / 10000).toFixed(1) + '万' : v) } },
    yAxis: { type: 'category', data: topLabels, axisLabel: { width: 100, overflow: 'truncate' } },
    series: [{
      type: 'bar', data: topValues, barWidth: '55%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#f59e0b' }, { offset: 1, color: '#ef4444' }
        ]), borderRadius: [0, 6, 6, 0]
      },
      label: { show: true, position: 'right', formatter: (p: any) => '¥' + Number(p.value).toLocaleString() }
    }]
  })
}

let _winResizeHandler: (() => void) | null = null
function bindResize() {
  _winResizeHandler = () => { agingChart?.resize(); topChart?.resize() }
  window.addEventListener('resize', _winResizeHandler)
}

watch(supplierId, () => refreshAll())

onMounted(async () => {
  await loadSuppliers()
  await refreshAll()
  bindResize()
})
onBeforeUnmount(() => {
  if (_winResizeHandler) window.removeEventListener('resize', _winResizeHandler)
  agingChart?.dispose(); topChart?.dispose()
  agingChart = null; topChart = null
})
</script>

<style scoped>
.page-scm-ap-kanban { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.kpi-row { margin-bottom: 16px; }
.kpi-card {
  background: #fff; border-radius: 12px; padding: 18px 20px;
  box-shadow: 0 1px 3px rgba(0,0,0,.04); border: 1px solid var(--el-border-color-lighter);
  display: flex; flex-direction: column; gap: 4px;
}
.kpi-title { color: var(--el-text-color-secondary); font-size: 13px; }
.kpi-value { font-size: 26px; font-weight: 700; color: #1f2937; letter-spacing: .3px; }
.kpi-sub { color: var(--el-text-color-secondary); font-size: 12px; }
.kpi-card.unpaid  .kpi-value { color: #2563eb; }
.kpi-card.overdue .kpi-value { color: #dc2626; }
.kpi-card.month   .kpi-value { color: #059669; }
.charts-row { margin-bottom: 4px; }
.chart-card { border-radius: 12px; }
.card-title { font-weight: 600; }
.chart-box { width: 100%; height: 340px; }
.danger { color: var(--el-color-danger); font-weight: 600; }
</style>
