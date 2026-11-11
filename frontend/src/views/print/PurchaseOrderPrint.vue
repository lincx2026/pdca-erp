<template>
  <div class="print-page" ref="printRef">
    <!-- 隐藏区域：打印按钮(仅屏幕显示，不打印) -->
    <div class="no-print" style="text-align:center; margin-bottom:12px;">
      <el-button type="primary" :disabled="!canPrint || exporting" :loading="loading" @click="doPrint">打印</el-button>
      <el-button :disabled="!canPrint" :loading="exporting" @click="exportPdf">导出 PDF</el-button>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon><el-button size="small" @click="loadData">重新加载</el-button></el-alert>
      <el-alert v-else-if="loaded && !detailLines.length" title="该采购订单没有明细，无法打印。" type="warning" :closable="false" show-icon />
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
          <div class="doc-title">采购订单</div>
          <div class="company-info">
            <span>地址：{{ company?.address || '—' }}</span>
            <span>电话：{{ company?.contactPhone || '—' }}</span>
          </div>
        </div>
        <div class="doc-header-right">
          <div class="doc-no-label">单号</div>
          <div class="doc-no-value">{{ order?.poNo || '—' }}</div>
          <div class="doc-date">{{ todayStr }}</div>
        </div>
      </div>
      <!-- 状态水印 -->
      <div class="watermark">{{ watermarkText }}</div>
      <div class="a4-content">
        <!-- 订单基本信息 -->
        <table class="info-table">
          <tr>
            <td class="label">订单号</td><td>{{ order?.poNo || '—' }}</td>
            <td class="label">供应商</td><td>{{ order?.supplierName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">采购员</td><td>{{ order?.buyerName || '—' }}</td>
            <td class="label">下单日期</td><td>{{ order?.poDate || '—' }}</td>
          </tr>
          <tr>
            <td class="label">交付日期</td><td>{{ order?.deliveryDate || '—' }}</td>
            <td class="label">币种</td><td>{{ order?.currency || '—' }}</td>
          </tr>
          <tr>
            <td class="label">订单金额</td><td>{{ order?.totalAmount == null ? '—' : (order?.currency || '¥') + Number(order.totalAmount).toFixed(2) }}</td>
            <td class="label">状态</td><td>{{ statusText(order?.status) }}</td>
          </tr>
        </table>
        <!-- 物料明细表 -->
        <div class="section-title">一、物料明细</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:5%">序号</th>
              <th style="width:13%">物料编码</th>
              <th>物料名称</th>
              <th style="width:13%">规格</th>
              <th style="width:6%">单位</th>
              <th style="width:8%">数量</th>
              <th style="width:9%">单价</th>
              <th style="width:11%">金额</th>
              <th style="width:14%">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, i) in page" :key="line.lineId">
              <td style="text-align:center">{{ pageIndex * 10 + i + 1 }}</td>
              <td>{{ line.materialCode || '' }}</td>
              <td>{{ line.materialName || '' }}</td>
              <td>{{ line.specification || '' }}</td>
              <td style="text-align:center">{{ line.unit || '' }}</td>
              <td style="text-align:right">{{ line.orderQty ?? '' }}</td>
              <td style="text-align:right">{{ line.unitPrice ?? '' }}</td>
              <td style="text-align:right">{{ line.lineAmount ?? '' }}</td>
              <td>{{ line.remark || '' }}</td>
            </tr>
          </tbody>
        </table>
        <!-- 交货条款 -->
        <div class="section-title">二、交货条款</div>
        <table class="info-table">
          <tr>
            <td class="label">交货条款</td><td>{{ order?.deliveryTerm || '—' }}</td>
            <td class="label">运输方式</td><td>{{ order?.transportType || '—' }}</td>
          </tr>
          <tr>
            <td class="label">付款方式</td><td>{{ order?.paymentTerm || '—' }}</td>
            <td class="label">特别要求</td><td>{{ order?.remark || '—' }}</td>
          </tr>
        </table>
        <!-- 签字栏 -->
        <div class="section-title">三、签字确认</div>
        <table class="sign-table">
          <tr>
            <td class="sign-label">制表</td>
            <td class="sign-blank"></td>
            <td class="sign-label">审核</td>
            <td class="sign-blank"></td>
            <td class="sign-label">批准</td>
            <td class="sign-blank"></td>
            <td class="sign-label">供应商确认</td>
            <td class="sign-blank"><div class="stamp-area">盖章</div></td>
          </tr>
        </table>
        <div class="footer-note">
          <span>本订单经双方签字盖章后生效。</span>
          <span>明细组 {{ pageIndex + 1 }}/{{ pages.length }} · 打印日期：{{ todayStr }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { companyApi, scmPoApi } from '@/api/index'
import { printCSS } from './printCSS'
import type { CompanyProfile } from '@/types/models'

const props = defineProps<{ order: any; visible: boolean }>()
const emit = defineEmits<{ (e: 'confirmed'): void }>()
const company = ref<CompanyProfile | null>(null)
const printRef = ref<HTMLElement>()
const detailOrder = ref<any>(null)
const detailLines = ref<any[]>([])
const loading = ref(false), loaded = ref(false), loadError = ref(''), exporting = ref(false)
const order = computed(() => detailOrder.value)
const canPrint = computed(() => props.visible && !loading.value && loaded.value && !!order.value && detailLines.value.length > 0)
const pages = computed(() => {
  const groups = []
  for (let i = 0; i < detailLines.value.length; i += 10) groups.push(detailLines.value.slice(i, i + 10))
  return groups
})

const todayStr = new Date().toLocaleDateString('zh-CN')

const statusText = (s: any) => {
  const m: Record<number, string> = {
    0: '草稿', 1: '已提交', 2: '已审核', 3: '已下单',
    4: '部分到货', 5: '全部到货', 6: '已完成', 7: '已驳回', 9: '取消'
  }
  if (s == null) return '—'
  return m[Number(s)] || String(s)
}

const watermarkText = computed(() => {
  return order.value ? statusText(order.value.status) : '—'
})

let requestSequence = 0
const loadData = async () => {
  const sequence = ++requestSequence
  const id = props.order?.poId
  loading.value = true; loaded.value = false; loadError.value = ''; detailOrder.value = null; detailLines.value = []; company.value = null
  try {
    if (!id) throw new Error('缺少采购订单编号')
    const [profile, data]: any[] = await Promise.all([companyApi.purchaseOrderPrintProfile(), scmPoApi.printData(id)])
    if (sequence !== requestSequence) return
    if (!profile || !data?.order || String(data.order.poId) !== String(id) || !Array.isArray(data.lines)) throw new Error('返回数据不完整')
    company.value = profile; detailOrder.value = data.order; detailLines.value = data.lines; loaded.value = true
  } catch {
    if (sequence === requestSequence) loadError.value = '采购订单或公司信息加载失败，请重新加载后打印。'
  } finally { if (sequence === requestSequence) loading.value = false }
}

watch(() => [props.visible, props.order?.poId], ([visible]) => {
  if (visible) loadData()
  else { requestSequence++; loading.value = false; loaded.value = false; detailOrder.value = null; detailLines.value = []; loadError.value = '' }
}, { immediate: true })
onBeforeUnmount(() => { requestSequence++ })

const exportPdf = async () => {
  if (!canPrint.value || !printRef.value || exporting.value) return
  exporting.value = true
  try {
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([import('html2canvas'), import('jspdf')])
    const doc = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' })
    const sections = Array.from(printRef.value.querySelectorAll<HTMLElement>('.a4-page'))
    for (let i = 0; i < sections.length; i++) {
      const canvas = await html2canvas(sections[i], {
        scale: 2, backgroundColor: '#fff',
        onclone(cloned) {
          const style = cloned.createElement('style')
          style.textContent = printCSS() + ' .a4-page { width: 190mm !important; } .data-table td { overflow-wrap: anywhere; }'
          cloned.head.appendChild(style)
        }
      })
      if (i) doc.addPage()
      const scale = Math.min(190 / canvas.width, 277 / canvas.height)
      doc.addImage(canvas.toDataURL('image/png'), 'PNG', 10, 10, canvas.width * scale, canvas.height * scale)
    }
    const name = String(detailOrder.value?.poNo || '采购订单').replace(/[\\/:*?"<>|]/g, '_')
    doc.save(`${name}.pdf`)
  } catch { ElMessage.error('PDF 生成失败，请重试') }
  finally { exporting.value = false }
}

const doPrint = () => {
  const el = printRef.value
  if (!el || !canPrint.value) return
  const printedId = detailOrder.value.poId
  const win = window.open('', '_blank', 'width=900,height=700')
  if (!win) { ElMessage.warning('请允许弹窗以打印'); return }
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>打印</title><style>${printCSS()} .a4-page:not(:last-child){break-after:page}.data-table tr{break-inside:avoid}.data-table td{overflow-wrap:anywhere}</style></head><body>${el.innerHTML}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(async () => {
    win.print(); win.close()
    try {
      await ElMessageBox.confirm('请确认纸质采购订单已实际打印完成。取消打印时请选择取消。', '确认打印结果', { confirmButtonText: '已打印', cancelButtonText: '取消' })
      await scmPoApi.confirmPrint(printedId)
      emit('confirmed'); ElMessage.success('已记录打印确认')
    } catch (e: any) { if (e !== 'cancel' && e !== 'close') ElMessage.error('打印确认结果未能获取，请刷新单据查看状态后再操作') }
  }, 500)
}

</script>

<style scoped>
.print-page { background:#fff; }
.a4-page { padding: 8px; }
</style>
