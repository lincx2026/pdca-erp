<template>
  <div class="print-page" ref="printRef">
    <!-- 隐藏区域：打印按钮(仅屏幕显示，不打印) -->
    <div class="no-print" style="text-align:center; margin-bottom:12px;">
      <el-checkbox v-model="settings.showSpec">规格</el-checkbox>
      <el-checkbox v-model="settings.showRemark">备注</el-checkbox>
      <el-checkbox v-model="settings.showSignature">签字栏</el-checkbox>
      <span>每组明细行数：</span><el-input-number v-model="settings.rowsPerPage" :min="1" :max="30" :step="1" />
      <el-button @click="saveSettings">保存本机模板</el-button>
      <el-button type="primary" @click="doPrint">打印</el-button>
    </div>
    <!-- A4 打印内容 -->
    <div v-for="(page, pageIndex) in pages" :key="pageIndex" class="a4-page">
      <!-- 公司抬头 -->
      <div class="doc-header">
        <div class="doc-header-left">
          <div class="logo-box">LOGO</div>
        </div>
        <div class="doc-header-center">
          <div class="company-name">{{ company?.companyName || '—' }}</div>
          <div class="doc-title">出库单</div>
          <div class="company-info">
            <span>地址：{{ company?.address || '—' }}</span>
            <span>电话：{{ company?.contactPhone || '—' }}</span>
          </div>
        </div>
        <div class="doc-header-right">
          <div class="doc-no-label">单号</div>
          <div class="doc-no-value">{{ order?.orderNo || '—' }}</div>
          <div class="doc-date">{{ todayStr }}</div>
        </div>
      </div>
      <!-- 状态水印 -->
      <div class="watermark">{{ watermarkText }}</div>
      <div class="a4-content">
        <!-- 出库单基本信息 -->
        <table class="info-table">
          <tr>
            <td class="label">出库单号</td><td>{{ order?.orderNo || '—' }}</td>
            <td class="label">仓库</td><td>{{ order?.warehouseName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">出库类型</td><td>{{ outTypeText(order?.outType) }}</td>
            <td class="label">客户</td><td>{{ order?.customerName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">总数量</td><td>{{ fmtNum(order?.totalQty) }}</td>
            <td class="label">状态</td><td>{{ statusText(order?.status) }}</td>
          </tr>
          <tr>
            <td class="label">日期</td><td>{{ order?.createTime || todayStr }}</td>
            <td class="label">备注</td><td>{{ order?.remark || '—' }}</td>
          </tr>
        </table>
        <!-- 物料明细 -->
        <div class="section-title">物料明细</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:5%">序号</th>
              <th style="width:18%">物料编码</th>
              <th>物料名称</th>
              <th v-if="settings.showSpec" style="width:14%">规格</th>
              <th style="width:8%">单位</th>
              <th style="width:10%">数量</th>
              <th v-if="settings.showRemark" style="width:14%">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in page" :key="i">
              <td style="text-align:center">{{ pageIndex * settings.rowsPerPage + i + 1 }}</td>
              <td>{{ l.materialCode || '' }}</td>
              <td>{{ l.materialName || '' }}</td>
              <td v-if="settings.showSpec">{{ l.specification || '' }}</td>
              <td style="text-align:center">{{ l.unit || '' }}</td>
              <td style="text-align:right">{{ l.quantity ?? '' }}</td>
              <td v-if="settings.showRemark">{{ l.remark || '' }}</td>
            </tr>
          </tbody>
        </table>
        <!-- 签字栏 -->
        <div v-if="settings.showSignature" class="section-title">签字确认</div>
        <table v-if="settings.showSignature" class="sign-table">
          <tr>
            <td class="sign-label">制表</td>
            <td class="sign-blank"></td>
            <td class="sign-label">审核</td>
            <td class="sign-blank"></td>
            <td class="sign-label">仓管</td>
            <td class="sign-blank"></td>
            <td class="sign-label">领物人</td>
            <td class="sign-blank"><div class="stamp-area">盖章</div></td>
          </tr>
        </table>
        <div class="footer-note">
          <span>本出库单一式三联：仓库、财务、客户。</span>
          <span>明细组 {{ pageIndex + 1 }}/{{ pages.length }} · 打印日期：{{ todayStr }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { companyApi, stockOutApi } from '@/api/index'
import { printCSS } from './printCSS'
import type { CompanyProfile } from '@/types/models'

const props = defineProps<{ order: any; visible: boolean }>()
const emit = defineEmits<{ (e: 'confirmed'): void }>()
const settings = reactive({ showSpec: true, showRemark: true, showSignature: true, rowsPerPage: 10 })
try {
  const saved = JSON.parse(localStorage.getItem('stockOutPrintTemplate') || '{}')
  for (const key of ['showSpec', 'showRemark', 'showSignature'] as const) if (typeof saved[key] === 'boolean') settings[key] = saved[key]
  if (Number.isInteger(saved.rowsPerPage) && saved.rowsPerPage >= 1 && saved.rowsPerPage <= 30) settings.rowsPerPage = saved.rowsPerPage
} catch { /* use defaults */ }
function saveSettings() { localStorage.setItem('stockOutPrintTemplate', JSON.stringify(settings)); ElMessage.success('打印模板已保存在当前浏览器') }
const company = ref<CompanyProfile | null>(null)
const detailLines = ref<any[]>([])
const printRef = ref<HTMLElement>()

const todayStr = new Date().toLocaleDateString('zh-CN')

const outTypeMap: Record<string, string> = {
  PRODUCTION: '生产领料', SALES: '销售出库', TRANSFER: '调拨出库', OTHER: '其他'
}
const outTypeText = (t?: string) => outTypeMap[t || ''] || t || '—'
const statusText = (s?: number) => {
  const m: Record<number, string> = { 0: '草稿', 1: '待审核', 2: '已审核', 3: '已出库', 4: '已驳回' }
  return m[s ?? -1] || '草稿'
}

const watermarkText = computed(() => {
  const m: Record<number, string> = { 0: '草稿', 1: '待审核', 2: '已审核', 3: '已出库' }
  return m[Number(props.order?.status)] || '草稿'
})
function fmtNum(v: any) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  return Number.isNaN(n) ? '0' : n
}

// 明细行：有数据则展示，否则 5 行空白供手写
const displayLines = computed(() => {
  if (detailLines.value && detailLines.value.length) return detailLines.value
  return Array.from({ length: 5 }, () => ({}))
})
const pages = computed(() => {
  const size = Math.max(1, Math.min(30, Number(settings.rowsPerPage) || 10))
  const groups = []
  for (let i = 0; i < displayLines.value.length; i += size) groups.push(displayLines.value.slice(i, i + size))
  return groups
})

const loadData = async () => {
  if (!props.order) return
  try {
    company.value = await companyApi.profileGet()
  } catch (e) { console.error('company load', e) }
  const id = props.order.stockOutId ?? props.order.orderId ?? props.order.id
  if (id) {
    try {
      const detail: any = await stockOutApi.get(id)
      detailLines.value = (detail?.lines || []) as any[]
    } catch (e) { console.error('stockout detail load', e) }
  }
}

watch(() => props.visible, (v) => { if (v) loadData() })
onMounted(() => { if (props.order) loadData() })

const doPrint = () => {
  const el = printRef.value
  if (!el) return
  const win = window.open('', '_blank', 'width=900,height=700')
  if (!win) { ElMessage.warning('请允许弹窗以打印'); return }
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>打印</title><style>${printCSS()} .a4-page:not(:last-child){break-after:page}.data-table tr{break-inside:avoid}</style></head><body>${el.innerHTML}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(async () => {
    win.print(); win.close()
    try {
      await ElMessageBox.confirm('请确认纸质出库单已实际打印完成。取消打印时请选择取消。', '确认打印结果', { confirmButtonText: '已打印', cancelButtonText: '取消' })
      await stockOutApi.confirmPrint(props.order.stockOutId)
      emit('confirmed'); ElMessage.success('已记录打印确认')
    } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '记录打印状态失败') }
  }, 500)
}

</script>

<style scoped>
.print-page { background:#fff; }
.a4-page { padding: 16px; margin: 12px auto; border: 1px solid #ddd; color: #222; font-size: 12px; position: relative; }
.doc-header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 3px double #222; padding-bottom: 8px; }
.doc-header-left, .doc-header-right { width: 22%; }
.doc-header-right { text-align: right; }
.doc-header-center { text-align: center; flex: 1; }
.company-name { font-size: 20px; font-weight: bold; }
.doc-title { font-size: 16px; font-weight: bold; }
.company-info, .doc-date { font-size: 10px; color: #666; }
.logo-box { width: 60px; height: 60px; border: 1px solid #222; display: grid; place-items: center; }
.watermark { position: absolute; top: 45%; left: 35%; transform: rotate(-30deg); color: rgba(150,150,150,.2); font-size: 60px; pointer-events: none; }
.a4-content { position: relative; }
.info-table, .data-table, .sign-table { width: 100%; border-collapse: collapse; margin-top: 8px; }
.info-table td, .data-table td, .data-table th, .sign-table td { border: 1px solid #aaa; padding: 5px; overflow-wrap: anywhere; }
.data-table th { background: #2c3e50; color: white; }
.info-table .label, .sign-label { background: #eef1f5; font-weight: bold; }
.section-title { margin-top: 10px; font-weight: bold; border-left: 4px solid #222; padding-left: 6px; }
.sign-blank { height: 40px; }
.footer-note { display: flex; justify-content: space-between; margin-top: 10px; color: #666; }
</style>
