<template>
  <div class="page-sal-kanban">
    <el-form :inline="true" class="search-form" @submit.prevent>
      <el-form-item label="客户">
        <el-select v-model="customerId" placeholder="全部客户" clearable filterable style="width:260px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
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
          <div class="kpi-title">应收总额</div>
          <div class="kpi-value">{{ fmt(summary.totalReceivableAmount) }}</div>
          <div class="kpi-sub">共 {{ summary.receivableCount ?? 0 }} 单</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card unrecv">
          <div class="kpi-title">未收总额</div>
          <div class="kpi-value">{{ fmt(summary.unreceivedAmount) }}</div>
          <div class="kpi-sub">未收单数 {{ summary.unreceivedCount ?? 0 }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card overdue">
          <div class="kpi-title">逾期未收</div>
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

    <!-- 销售业务 KPI -->
    <el-row :gutter="16" class="kpi-row" style="margin-top:16px;">
      <el-col :xs="12" :sm="12" :md="8">
        <div class="kpi-card delivery">
          <div class="kpi-title">订单交付率</div>
          <div class="kpi-value">{{ pct(sales.deliveryRate) }}</div>
          <div class="kpi-sub">已交付 {{ sales.deliveredOrders ?? 0 }} / {{ sales.totalOrders ?? 0 }} 单</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8">
        <div class="kpi-card return-rate">
          <div class="kpi-title">退货率</div>
          <div class="kpi-value">{{ pct(sales.returnRate) }}</div>
          <div class="kpi-sub">退货 {{ sales.returnOrders ?? 0 }} 单 · {{ fmt(sales.returnAmount) }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8">
        <div class="kpi-card sales-amt">
          <div class="kpi-title">销售金额</div>
          <div class="kpi-value">{{ fmt(sales.salesAmount) }}</div>
          <div class="kpi-sub">已完成 {{ sales.completedOrders ?? 0 }} 单</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表行 -->
    <el-row :gutter="16" class="charts-row">
      <el-col :md="14">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">账龄分桶（按未收金额）</span></template>
          <div ref="agingRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :md="10">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">Top {{ topLimit }} 客户未收金额</span></template>
          <div ref="topRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 销售趋势 + 客户TOP收款 -->
    <el-row :gutter="16" class="charts-row" style="margin-top:16px;">
      <el-col :md="14">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">近6月销售/退货/收款金额趋势</span></template>
          <div ref="trendRef" class="chart-box trend-box"></div>
        </el-card>
      </el-col>
      <el-col :md="10">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">Top {{ topLimit }} 客户收款金额</span></template>
          <div ref="topReceivedRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 应收单明细 -->
    <el-card shadow="never" style="margin-top:16px;">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span class="card-title">应收单明细（未收/逾期优先）</span>
          <el-tag type="info">共 {{ receivableRows.length }} 行</el-tag>
        </div>
      </template>
      <el-table :data="receivableRows" stripe border size="small" max-height="480">
        <el-table-column prop="receivableNo" label="应收单号" width="180" />
        <el-table-column prop="customerName" label="客户" width="200" show-overflow-tooltip />
        <el-table-column prop="bizDate" label="业务日" width="110" />
        <el-table-column prop="dueDate" label="到期日" width="110" />
        <el-table-column prop="totalAmount" label="应收额" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="receivedAmount" label="已收" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.receivedAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unreceivedAmount" label="未收" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ danger: Number(row.isOverdue) === 1 }">{{ fmt(row.unreceivedAmount) }}</span>
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
import { salArReceivableApi, salCustomerApi } from '@/api'
import request from '@/utils/request'
import * as echarts from 'echarts'
import type { SalCustomer } from '@/types/models'

defineOptions({ name: 'SalKanbanPage' })

function fmt(v: any) {
  if (v == null || v === '') return '-'
  return '¥' + Number(v).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '待对账', t: 'info' }, 1: { l: '对账中', t: 'warning' }, 2: { l: '对账完成', t: 'primary' },
  3: { l: '部分收', t: 'warning' }, 4: { l: '收款完成', t: 'success' }, 9: { l: '作废', t: 'info' }
}
const statusMap = STATUS_LABEL

const customerId = ref<number | undefined>(undefined)
const customers = ref<SalCustomer[]>([])
const topLimit = 10

const summary = ref<any>({
  totalReceivableAmount: 0, receivableCount: 0, unreceivedAmount: 0, unreceivedCount: 0,
  overdueAmount: 0, overdueCount: 0, avgOverdueDays: 0, monthDueAmount: 0, monthDueCount: 0
})
const agingData = ref<any[]>([])
const topData = ref<any[]>([])
const receivableRows = ref<any[]>([])
const sales = ref<any>({ totalOrders: 0, deliveredOrders: 0, completedOrders: 0, deliveryRate: 0, salesAmount: 0, returnOrders: 0, returnAmount: 0, returnRate: 0 })
const topReceivedData = ref<any[]>([])
const trendData = ref<any[]>([])

const agingRef = ref<HTMLElement | null>(null)
const topRef = ref<HTMLElement | null>(null)
const trendRef = ref<HTMLElement | null>(null)
const topReceivedRef = ref<HTMLElement | null>(null)
let agingChart: echarts.ECharts | null = null
let topChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let topReceivedChart: echarts.ECharts | null = null

async function loadCustomers() {
  try {
    const resp = (await salCustomerApi.options()) as any
    customers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载客户失败') }
}

async function refreshAll() {
  try {
    const [s, t, a, list, salesSum, topRecv] = await Promise.all([
      salArReceivableApi.kanbanSummary(customerId.value) as any,
      salArReceivableApi.topCustomers(customerId.value, topLimit) as any,
      salArReceivableApi.aging(customerId.value) as any,
      salArReceivableApi.list({
        pageNum: 1, pageSize: 100, status: undefined, receivableNo: '', customerId: customerId.value,
        customerName: '', bizDate: '', dueDate: '', isOverdue: undefined
      }) as any,
      request.get<Record<string, any>>('/sal/kanban/sales-summary', customerId.value != null ? { customerId: customerId.value } : {}) as any,
      request.get<any[]>('/sal/kanban/top-received', customerId.value != null ? { customerId: customerId.value, limit: topLimit } : { limit: topLimit }) as any
    ])
    // 后端字段: receivedTotal, unreceivedTotal, overdueTotal, dueThisMonth, newToday, new30
    const raw = (s as any)?.data || s || {}
    const listTotal = list?.data?.total || list?.total || 0
    const recvTotal = Number(raw.receivedTotal || 0)
    const unrecvTotal = Number(raw.unreceivedTotal || 0)
    summary.value = {
      totalReceivableAmount: recvTotal + unrecvTotal,
      receivableCount: listTotal,
      unreceivedAmount: unrecvTotal,
      unreceivedCount: 0,
      overdueAmount: Number(raw.overdueTotal || 0),
      overdueCount: 0,
      avgOverdueDays: 0,
      monthDueAmount: Number(raw.dueThisMonth || 0),
      monthDueCount: 0
    }
    topData.value = (t as any)?.data || t || []
    agingData.value = (a as any)?.data || a || []
    topReceivedData.value = (topRecv as any)?.data || topRecv || []
    // 销售业务KPI
    const ss = (salesSum as any)?.data || salesSum || {}
    sales.value = {
      totalOrders: Number(ss.totalOrders || 0),
      deliveredOrders: Number(ss.deliveredOrders || 0),
      completedOrders: Number(ss.completedOrders || 0),
      deliveryRate: Number(ss.deliveryRate || 0),
      salesAmount: Number(ss.salesAmount || 0),
      returnOrders: Number(ss.returnOrders || 0),
      returnAmount: Number(ss.returnAmount || 0),
      returnRate: Number(ss.returnRate || 0)
    }
    const rows = list?.data?.rows || list?.rows || []
    receivableRows.value = rows.slice().sort((x: any, y: any) => {
      const s1 = Number(x.isOverdue) === 1 ? 0 : 1
      const s2 = Number(y.isOverdue) === 1 ? 0 : 1
      if (s1 !== s2) return s1 - s2
      return Number(y.unreceivedAmount || 0) - Number(x.unreceivedAmount || 0)
    })
    await nextTick()
    renderCharts()
  } catch (e: any) { ElMessage.error(e?.message || '看板数据加载失败') }
}

function renderCharts() {
  if (!agingRef.value || !topRef.value || !trendRef.value) return
  if (!agingChart) agingChart = echarts.init(agingRef.value)
  if (!topChart) topChart = echarts.init(topRef.value)
  if (!trendChart) trendChart = echarts.init(trendRef.value)

  // 账龄柱状图
  const agingLabels = agingData.value.map((d: any) => d.bucket || d.label || '未知')
  const agingValues = agingData.value.map((d: any) => Number(d.unreceivedAmount ?? d.amount ?? 0))
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

  // Top 客户横向柱状图
  const topLabels = topData.value.map((d: any) => d.customerName || d.name || '未知').reverse()
  const topValues = topData.value.map((d: any) => Number(d.unreceivedAmount ?? d.amount ?? 0)).reverse()
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

  // 近6月回款率趋势（占位示意）
  const months = buildRecentMonths(6)
  const trendRates = [0.82, 0.78, 0.85, 0.91, 0.88, 0.93]
  trendChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v: any) => (Number(v) * 100).toFixed(1) + '%' },
    grid: { left: 50, right: 30, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: months },
    yAxis: { type: 'value', min: 0, max: 1, axisLabel: { formatter: (v: any) => (v * 100).toFixed(0) + '%' } },
    series: [{
      type: 'line', data: trendRates, smooth: true, symbol: 'circle', symbolSize: 8,
      lineStyle: { width: 3, color: '#10b981' },
      itemStyle: { color: '#10b981' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(16,185,129,0.25)' }, { offset: 1, color: 'rgba(16,185,129,0.02)' }
        ])
      },
      label: { show: true, position: 'top', formatter: (p: any) => (Number(p.value) * 100).toFixed(0) + '%' }
    }]
  })
}

function buildRecentMonths(n: number): string[] {
  const arr: string[] = []
  const now = new Date()
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    arr.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  return arr
}

let _winResizeHandler: (() => void) | null = null
function bindResize() {
  _winResizeHandler = () => { agingChart?.resize(); topChart?.resize(); trendChart?.resize() }
  window.addEventListener('resize', _winResizeHandler as any)
}

watch(customerId, () => refreshAll())

onMounted(async () => {
  await loadCustomers()
  await refreshAll()
  bindResize()
})
onBeforeUnmount(() => {
  if (_winResizeHandler) window.removeEventListener('resize', _winResizeHandler as any)
  agingChart?.dispose(); topChart?.dispose(); trendChart?.dispose()
  agingChart = null; topChart = null; trendChart = null
})
</script>

<style scoped>
.page-sal-kanban { padding: 16px; }
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
.kpi-card.unrecv  .kpi-value { color: #2563eb; }
.kpi-card.overdue .kpi-value { color: #dc2626; }
.kpi-card.month   .kpi-value { color: #059669; }
.charts-row { margin-bottom: 4px; }
.chart-card { border-radius: 12px; }
.card-title { font-weight: 600; }
.chart-box { width: 100%; height: 340px; }
.trend-box { height: 260px; }
.danger { color: var(--el-color-danger); font-weight: 600; }
</style>
