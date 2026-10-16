<template>
  <div class="print-page" ref="printRef">
    <!-- 隐藏区域：打印按钮(仅屏幕显示，不打印) -->
    <div class="no-print" style="text-align:center; margin-bottom:12px;">
      <el-button type="primary" @click="doPrint">打印</el-button>
    </div>
    <!-- A4 打印内容 -->
    <div class="a4-page">
      <!-- 公司抬头 -->
      <div class="doc-header">
        <div class="doc-header-left">
          <div class="logo-box">LOGO</div>
        </div>
        <div class="doc-header-center">
          <div class="company-name">{{ company?.companyName || '—' }}</div>
          <div class="doc-title">工装治具校准记录表</div>
          <div class="company-info">
            <span>地址：{{ company?.address || '—' }}</span>
            <span>电话：{{ company?.contactPhone || '—' }}</span>
          </div>
        </div>
        <div class="doc-header-right">
          <div class="doc-no-label">工装编号</div>
          <div class="doc-no-value">{{ jig?.jigNo || '—' }}</div>
          <div class="doc-date">{{ todayStr }}</div>
        </div>
      </div>
      <!-- 状态水印 -->
      <div class="watermark">校准记录</div>
      <div class="a4-content">
        <!-- 治具信息 -->
        <div class="section-title">一、治具信息</div>
        <table class="info-table">
          <tr>
            <td class="label">工装编号</td><td>{{ jig?.jigNo || '—' }}</td>
            <td class="label">工装名称</td><td>{{ jig?.jigName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">工装类型</td><td>{{ jigTypeText(jig?.jigType) }}</td>
            <td class="label">产品机种</td><td>{{ jig?.productCode || '—' }}</td>
          </tr>
          <tr>
            <td class="label">保管人</td><td>{{ jig?.keeperName || '—' }}</td>
            <td class="label">存放位置</td><td>{{ locText }}</td>
          </tr>
          <tr>
            <td class="label">状态</td><td>{{ statusText(jig?.status) }}</td>
            <td class="label">校准周期</td><td>{{ jig?.calCycleDays != null ? jig.calCycleDays + ' 天' : '—' }}</td>
          </tr>
        </table>
        <!-- 寿命信息 -->
        <div class="section-title">二、寿命信息</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:20%">累计使用次数</th>
              <th style="width:20%">寿命阈值</th>
              <th style="width:20%">使用率</th>
              <th style="width:20%">上次校准日期</th>
              <th style="width:20%">下次校准日期</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style="text-align:center">{{ jig?.usedCount ?? 0 }}</td>
              <td style="text-align:center">{{ jig?.lifeCount ?? 0 }}</td>
              <td style="text-align:center">{{ usageRate }}</td>
              <td style="text-align:center">{{ jig?.lastCalDate || '—' }}</td>
              <td style="text-align:center">{{ jig?.nextCalDate || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <!-- 校准历史记录 -->
        <div class="section-title">三、校准历史记录</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:6%">序号</th>
              <th style="width:16%">校准日期</th>
              <th style="width:14%">校准人</th>
              <th style="width:12%">校准结果</th>
              <th style="width:22%">测量值</th>
              <th>备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(lg, i) in displayLogs" :key="lg?.logId ?? i">
              <td style="text-align:center">{{ i + 1 }}</td>
              <td>{{ lg?.eventTime || '—' }}</td>
              <td>{{ lg?.operatorName || '—' }}</td>
              <td style="text-align:center">{{ resultText(lg?.eventResult) }}</td>
              <td>{{ measureText(lg) }}</td>
              <td>{{ lg?.remark || '&nbsp;' }}</td>
            </tr>
          </tbody>
        </table>
        <!-- 签字栏 -->
        <div class="section-title">四、签字确认</div>
        <table class="sign-table">
          <tr>
            <td class="sign-label">校准人</td>
            <td class="sign-blank"></td>
            <td class="sign-label">审核</td>
            <td class="sign-blank"></td>
            <td class="sign-label">设备主管</td>
            <td class="sign-blank"></td>
            <td class="sign-label">日期</td>
            <td class="sign-blank"><div class="stamp-area">盖章</div></td>
          </tr>
        </table>
        <div class="footer-note">
          <span>本记录经相关责任人签字后归档。</span>
          <span>第1页/共1页 · 打印日期：{{ todayStr }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { companyApi, pmJigApi } from '@/api/index'
import { printCSS } from './printCSS'
import type { CompanyProfile, PmmJigLog } from '@/types/models'

const props = defineProps<{ jig: any; visible: boolean }>()
const company = ref<CompanyProfile | null>(null)
const logList = ref<PmmJigLog[]>([])
const printRef = ref<HTMLElement>()

const todayStr = new Date().toLocaleDateString('zh-CN')

const locText = computed(() => {
  const w = props.jig?.warehouse || ''
  const l = props.jig?.location || ''
  if (!w && !l) return '—'
  return [w, l].filter(Boolean).join(' / ')
})

const usageRate = computed(() => {
  const used = Number(props.jig?.usedCount ?? 0)
  const life = Number(props.jig?.lifeCount ?? 0)
  if (!life || life <= 0) return '不限寿命'
  return Math.min(100, Math.round(used / life * 100)) + '%'
})

// 校准历史展示行：有数据则展示，无数据补 5 行空白占位
const displayLogs = computed<(PmmJigLog | null)[]>(() => {
  const rows = logList.value && logList.value.length > 0 ? logList.value : []
  if (rows.length > 0) return rows
  return Array.from({ length: 5 }, () => null)
})

const jigTypeText = (v?: string) => {
  const m: Record<string, string> = {
    STENCIL: '钢网', CARRIER: '载具(贴片/波峰焊)', FCT_FIXTURE: 'FCT功能测试治具',
    ICT_FIXTURE: 'ICT在线测试治具', REFLOW_FIXTURE: '回流焊治具',
    ROUTER_FIXTURE: '分板/铣刀治具', TOOL: '工具类(扭力扳手等)', OTHER: '其他工装'
  }
  return m[v || ''] || v || '—'
}
const statusText = (v: any) => {
  const m: Record<number, string> = {
    0: '闲置', 1: '在用', 2: '借出', 3: '校准中', 4: '修磨中', 5: '维修中', 6: '报废'
  }
  if (v == null) return '—'
  return m[Number(v)] || String(v)
}
const resultText = (v?: string) => {
  const m: Record<string, string> = { PASS: '合格', FAIL: '不合格', PENDING: '待定' }
  return m[v || ''] || v || '—'
}
const measureText = (lg: PmmJigLog | null) => {
  if (!lg) return '—'
  if (lg.measureValue == null && !lg.measureItem) return '—'
  const val = lg.measureValue != null ? lg.measureValue : ''
  const unit = lg.measureUnit || ''
  const item = lg.measureItem ? lg.measureItem + ': ' : ''
  return item + val + unit
}

const loadData = async () => {
  try {
    company.value = await companyApi.profileGet()
  } catch (e) { console.error('print load company', e) }
  // 加载校准日志
  if (props.jig?.jigId) {
    try {
      const resp: any = await pmJigApi.logList({ jigId: props.jig.jigId, eventType: 'CALIBRATE' })
      logList.value = (resp?.rows) || []
    } catch (e) { console.error('print load logs', e); logList.value = [] }
  }
}

watch(() => props.visible, (v) => { if (v) loadData() })
onMounted(() => { loadData() })

const doPrint = () => {
  const el = printRef.value
  if (!el) return
  const win = window.open('', '_blank', 'width=900,height=700')
  if (!win) { ElMessage.warning('请允许弹窗以打印'); return }
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>打印</title><style>${printCSS()}</style></head><body>${el.innerHTML}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 500)
}

</script>

<style scoped>
.print-page { background:#fff; }
.a4-page { padding: 8px; }
</style>
