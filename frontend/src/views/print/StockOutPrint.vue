<template>
  <div class="print-page" ref="printRef">
    <!-- 隐藏区域：打印按钮(仅屏幕显示，不打印) -->
    <div class="no-print" style="text-align:center; margin-bottom:12px;">
      <el-checkbox v-model="settings.showSpec">规格</el-checkbox>
      <el-checkbox v-model="settings.showRemark">备注</el-checkbox>
      <el-checkbox v-model="settings.showSignature">签字栏</el-checkbox>
      <span>每组明细行数：</span><el-input-number v-model="settings.rowsPerPage" :min="1" :max="30" :step="1" />
      <el-button @click="saveSettings">保存本机模板</el-button>
      <el-button type="primary" :disabled="!canPrint" :loading="loading" @click="doPrint">打印</el-button>
      <el-collapse class="width-settings">
        <el-collapse-item title="调整明细列宽" name="widths">
          <div class="width-help">设置各列相对宽度；隐藏的列不占宽度。保存本机模板后下次继续使用。</div>
          <div class="width-grid">
            <label v-for="column in printColumns" :key="column.key" class="width-control">
              <span>{{ column.label }}</span>
              <el-input-number v-model="settings.columnWidths[column.key]" :min="1" :max="100" :step="1" :aria-label="`${column.label}列宽`" />
            </label>
          </div>
          <el-button @click="resetWidths">恢复默认列宽</el-button>
        </el-collapse-item>
      </el-collapse>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon>
        <el-button size="small" @click="loadData">重新加载</el-button>
      </el-alert>
      <el-alert v-else-if="loaded && !detailLines.length" title="该出库单没有明细，无法打印。" type="warning" :closable="false" show-icon />
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
          <colgroup><col v-for="column in columns" :key="column.key" :style="{ width: `${column.percentage}%` }"></colgroup>
          <thead>
            <tr>
              <th>序号</th>
              <th>物料编码</th>
              <th>物料名称</th>
              <th v-if="settings.showSpec">规格</th>
              <th>单位</th>
              <th>数量</th>
              <th v-if="settings.showRemark">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in page" :key="i">
              <td style="text-align:center">{{ pageIndex * pageSize + i + 1 }}</td>
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
import { ref, watch, computed, reactive, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { companyApi, stockOutApi } from '@/api/index'
import { printCSS } from './printCSS'
import { printColumns, defaultWidths, readWidths, visibleWidths } from './stockOutPrintSettings'
import type { CompanyProfile } from '@/types/models'

const props = defineProps<{ order: any; visible: boolean }>()
const emit = defineEmits<{ (e: 'confirmed'): void }>()
const settings = reactive({ showSpec: true, showRemark: true, showSignature: true, rowsPerPage: 10, columnWidths: defaultWidths() })
try {
  const saved = JSON.parse(localStorage.getItem('stockOutPrintTemplate') || '{}')
  for (const key of ['showSpec', 'showRemark', 'showSignature'] as const) if (typeof saved[key] === 'boolean') settings[key] = saved[key]
  if (Number.isInteger(saved.rowsPerPage) && saved.rowsPerPage >= 1 && saved.rowsPerPage <= 30) settings.rowsPerPage = saved.rowsPerPage
  settings.columnWidths = readWidths(saved.columnWidths)
} catch { /* use defaults */ }
function saveSettings() {
  settings.columnWidths = readWidths(settings.columnWidths)
  try { localStorage.setItem('stockOutPrintTemplate', JSON.stringify(settings)); ElMessage.success('打印模板已保存在当前浏览器') }
  catch { ElMessage.error('保存失败，请检查浏览器存储设置') }
}
function resetWidths() { settings.columnWidths = defaultWidths() }
const columns = computed(() => visibleWidths(settings.columnWidths, settings.showSpec, settings.showRemark))
const company = ref<CompanyProfile | null>(null)
const detailLines = ref<any[]>([])
const detailOrder = ref<any>(null)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref('')
const order = computed(() => detailOrder.value)
const canPrint = computed(() => props.visible && !loading.value && loaded.value && !!detailOrder.value && detailLines.value.length > 0)
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
  return m[Number(order.value?.status)] || '草稿'
})
function fmtNum(v: any) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  return Number.isNaN(n) ? '0' : n
}

const pageSize = computed(() => Math.max(1, Math.min(30, Math.floor(Number(settings.rowsPerPage) || 10))))
const pages = computed(() => {
  const groups = []
  for (let i = 0; i < detailLines.value.length; i += pageSize.value) groups.push(detailLines.value.slice(i, i + pageSize.value))
  return groups
})

let requestSequence = 0
const loadData = async () => {
  const sequence = ++requestSequence
  const selected = props.order
  const id = selected?.stockOutId
  loading.value = true
  loaded.value = false
  loadError.value = ''
  detailOrder.value = null
  detailLines.value = []
  company.value = null
  try {
    if (!id) throw new Error('缺少出库单编号')
    const [profile, detail]: any[] = await Promise.all([companyApi.stockOutPrintProfile(), stockOutApi.get(id)])
    if (sequence !== requestSequence) return
    if (!profile || !detail || String(detail.stockOutId) !== String(id) || !Array.isArray(detail.lines)) throw new Error('返回数据不完整')
    company.value = profile
    detailOrder.value = { ...detail, warehouseName: detail.warehouseName || (detail.warehouseId === selected.warehouseId ? selected.warehouseName : '') }
    detailLines.value = detail.lines
    loaded.value = true
  } catch {
    if (sequence === requestSequence) loadError.value = '出库单或公司信息加载失败，请重新加载后打印。'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

watch(() => [props.visible, props.order?.stockOutId], ([visible]) => {
  if (visible) loadData()
  else { requestSequence++; loading.value = false; loaded.value = false; detailOrder.value = null; detailLines.value = []; loadError.value = '' }
}, { immediate: true })
onBeforeUnmount(() => { requestSequence++ })

const doPrint = () => {
  const el = printRef.value
  if (!el || !canPrint.value) return
  const printedId = detailOrder.value.stockOutId
  const win = window.open('', '_blank', 'width=900,height=700')
  if (!win) { ElMessage.warning('请允许弹窗以打印'); return }
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>打印</title><style>${printCSS()} .a4-page:not(:last-child){break-after:page}.data-table{table-layout:fixed}.data-table tr{break-inside:avoid}.data-table th,.data-table td{overflow-wrap:anywhere;white-space:normal}</style></head><body>${el.innerHTML}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(async () => {
    win.print(); win.close()
    try {
      await ElMessageBox.confirm('请确认纸质出库单已实际打印完成。取消打印时请选择取消。', '确认打印结果', { confirmButtonText: '已打印', cancelButtonText: '取消' })
      await stockOutApi.confirmPrint(printedId)
      emit('confirmed'); ElMessage.success('已记录打印确认')
    } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error('打印确认结果未能获取，请刷新单据查看状态后再操作') }
  }, 500)
}

</script>

<style scoped>
.print-page { background:#fff; }
.width-settings { text-align:left; margin-top:12px; }
.width-help { color:#606266; margin-bottom:8px; }
.width-grid { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:10px; }
.width-control { display:flex; align-items:center; gap:6px; }
.width-control :deep(.el-input-number) { width:120px; }
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
.data-table { table-layout:fixed; }
.info-table td, .data-table td, .data-table th, .sign-table td { border: 1px solid #aaa; padding: 5px; overflow-wrap: anywhere; }
.data-table th { background: #2c3e50; color: white; }
.info-table .label, .sign-label { background: #eef1f5; font-weight: bold; }
.section-title { margin-top: 10px; font-weight: bold; border-left: 4px solid #222; padding-left: 6px; }
.sign-blank { height: 40px; }
.footer-note { display: flex; justify-content: space-between; margin-top: 10px; color: #666; }
</style>
