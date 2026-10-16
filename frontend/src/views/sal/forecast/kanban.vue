<template>
  <div class="page-sal-forecast-kanban">
    <el-form :inline="true" class="search-form" @submit.prevent>
      <el-form-item label="客户">
        <el-select v-model="customerId" placeholder="全部客户" clearable filterable style="width:260px" @change="refreshAll">
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
          <div class="kpi-title">当前生效版本</div>
          <div class="kpi-value">{{ summary.versionNo || '-' }}</div>
          <div class="kpi-sub">{{ summary.rowsCount ?? 0 }} 行预测</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card forecast">
          <div class="kpi-title">预测总量</div>
          <div class="kpi-value">{{ fmt(summary.forecastQty) }}</div>
          <div class="kpi-sub">金额 ¥{{ fmt(summary.forecastAmt) }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card actual">
          <div class="kpi-title">实际总量</div>
          <div class="kpi-value">{{ fmt(summary.actualQty) }}</div>
          <div class="kpi-sub">金额 ¥{{ fmt(summary.actualAmt) }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6">
        <div class="kpi-card" :class="hitClass">
          <div class="kpi-title">命中率</div>
          <div class="kpi-value">{{ pct(summary.hitRate) }}</div>
          <div class="kpi-sub">实际/预测</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表行 -->
    <el-row :gutter="16" class="charts-row">
      <el-col :md="24">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">预测 vs 实际趋势(按周期)</span></template>
          <div ref="trendRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="charts-row">
      <el-col :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">Top客户(按预测金额)</span></template>
          <div ref="topCustomerRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never" class="chart-card">
          <template #header><span class="card-title">Top物料(按预测数量)</span></template>
          <div ref="topMaterialRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { salForecastApi, salCustomerApi } from '@/api'

const customers = ref<any[]>([])
const customerId = ref<number | undefined>(undefined)
const summary = ref<any>({})

const trendRef = ref<HTMLElement | null>(null)
const topCustomerRef = ref<HTMLElement | null>(null)
const topMaterialRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null
let topCustomerChart: echarts.ECharts | null = null
let topMaterialChart: echarts.ECharts | null = null

const fmt = (n: any) => Number(n || 0).toFixed(2)
const pct = (n: any) => {
  const v = Number(n || 0)
  return (v * 100).toFixed(1) + '%'
}
const hitClass = computed(() => {
  const v = Number(summary.value?.hitRate || 0)
  if (v >= 0.9 && v <= 1.1) return 'hit-good'
  if (v < 0.5) return 'hit-bad'
  if (v > 1.2) return 'hit-warn'
  return ''
})

async function loadCustomers() {
  try {
    const res: any = await salCustomerApi.options()
    customers.value = res || []
  } catch (e) { console.error('load customers failed', e) }
}

async function refreshAll() {
  try {
    const [sum, tr, tc, tm] = await Promise.all([
      salForecastApi.kanbanSummary(customerId.value),
      salForecastApi.trend(customerId.value, 12),
      salForecastApi.topCustomers(customerId.value, 10),
      salForecastApi.topMaterials(customerId.value, 10)
    ])
    summary.value = sum || {}
    await nextTick()
    renderTrend(tr || [])
    renderTopCustomer(tc || [])
    renderTopMaterial(tm || [])
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') }
}

function ensureChart(ref: HTMLElement | null, prev: echarts.ECharts | null): echarts.ECharts | null {
  if (!ref) return null
  if (prev) { prev.dispose() }
  return echarts.init(ref)
}

function renderTrend(rows: any[]) {
  if (!trendRef.value) return
  trendChart = ensureChart(trendRef.value, trendChart)
  const labels = rows.map(r => String(r.periodStart || ''))
  const fcQty = rows.map(r => Number(r.forecastQty || 0))
  const acQty = rows.map(r => Number(r.actualQty || 0))
  const fcAmt = rows.map(r => Number(r.forecastAmt || 0))
  const acAmt = rows.map(r => Number(r.actualAmt || 0))
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['预测量', '实际量', '预测金额', '实际金额'], top: 0 },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: 30 } },
    yAxis: [
      { type: 'value', name: '数量' },
      { type: 'value', name: '金额(¥)' }
    ],
    series: [
      { name: '预测量', type: 'line', smooth: true, data: fcQty, itemStyle: { color: '#409EFF' } },
      { name: '实际量', type: 'line', smooth: true, data: acQty, itemStyle: { color: '#67C23A' } },
      { name: '预测金额', type: 'bar', yAxisIndex: 1, data: fcAmt, itemStyle: { color: 'rgba(64,158,255,0.4)' } },
      { name: '实际金额', type: 'bar', yAxisIndex: 1, data: acAmt, itemStyle: { color: 'rgba(103,194,58,0.4)' } }
    ]
  })
}

function renderTopCustomer(rows: any[]) {
  if (!topCustomerRef.value) return
  topCustomerChart = ensureChart(topCustomerRef.value, topCustomerChart)
  topCustomerChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 100, right: 30, top: 30, bottom: 30 },
    xAxis: { type: 'value', name: '预测金额(¥)' },
    yAxis: { type: 'category', data: rows.map(r => r.customerName || '-'), inverse: true },
    series: [{
      name: '预测金额', type: 'bar', data: rows.map(r => Number(r.forecastAmt || 0)),
      itemStyle: { color: '#409EFF' },
      label: { show: true, position: 'right', formatter: (p: any) => '¥' + Number(p.value).toFixed(0) }
    }]
  })
}

function renderTopMaterial(rows: any[]) {
  if (!topMaterialRef.value) return
  topMaterialChart = ensureChart(topMaterialRef.value, topMaterialChart)
  topMaterialChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 100, right: 30, top: 30, bottom: 30 },
    xAxis: { type: 'value', name: '预测数量' },
    yAxis: { type: 'category', data: rows.map(r => (r.materialCode || '') + ' ' + (r.materialName || '')), inverse: true, axisLabel: { width: 140, overflow: 'truncate' } },
    series: [{
      name: '预测数量', type: 'bar', data: rows.map(r => Number(r.forecastQty || 0)),
      itemStyle: { color: '#67C23A' },
      label: { show: true, position: 'right', formatter: (p: any) => Number(p.value).toFixed(2) }
    }]
  })
}

function onResize() {
  trendChart?.resize()
  topCustomerChart?.resize()
  topMaterialChart?.resize()
}

watch(customerId, () => refreshAll())

onMounted(() => {
  loadCustomers()
  refreshAll()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  trendChart?.dispose(); trendChart = null
  topCustomerChart?.dispose(); topCustomerChart = null
  topMaterialChart?.dispose(); topMaterialChart = null
})
</script>

<style scoped>
.page-sal-forecast-kanban { padding: 12px; }
.search-form { margin-bottom: 8px; }
.kpi-row { margin-bottom: 12px; }
.charts-row { margin-bottom: 12px; }
.chart-card { background: var(--el-bg-color); }
.chart-box { width: 100%; height: 340px; }
.card-title { font-weight: 600; }

.kpi-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 16px 18px;
}
.kpi-card.forecast { border-left: 4px solid #409EFF; }
.kpi-card.actual { border-left: 4px solid #67C23A; }
.kpi-card.hit-good { border-left: 4px solid #67C23A; }
.kpi-card.hit-bad { border-left: 4px solid #F56C6C; }
.kpi-card.hit-warn { border-left: 4px solid #E6A23C; }
.kpi-title { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 6px; }
.kpi-value { font-size: 22px; font-weight: 600; color: var(--el-text-color-primary); }
.kpi-sub { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; }
</style>
