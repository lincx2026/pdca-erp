<template>
  <div class="sales-order-print">
    <div class="toolbar"><el-button type="primary" @click="doPrint">打印</el-button></div>
    <div ref="printRef" class="document">
      <h2>销售订单</h2>
      <table class="info-table">
        <tbody>
          <tr><th>订单号</th><td>{{ order.soNo || '—' }}</td><th>客户订单号</th><td>{{ order.customerOrderNo || '—' }}</td></tr>
          <tr><th>客户</th><td>{{ order.customerName || '—' }}</td><th>销售员</th><td>{{ order.salesRepName || '—' }}</td></tr>
          <tr><th>下单日期</th><td>{{ order.soDate || '—' }}</td><th>交付日期</th><td>{{ order.deliveryDate || '—' }}</td></tr>
          <tr><th>订单金额</th><td>{{ money(order.totalAmount, order.currency) }}</td><th>备注</th><td>{{ order.remark || '—' }}</td></tr>
        </tbody>
      </table>
      <p v-if="order.amountMismatch || order.lineAmountsComplete === false" class="amount-warning">{{ order.lineAmountsComplete === false ? '部分明细金额缺失，请核对订单。' : `订单金额与明细合计 ${money(order.lineAmountTotal, order.currency)} 不一致，请核对订单。` }}</p>
      <h3>订单明细</h3>
      <table class="data-table">
        <thead><tr><th>行号</th><th>物料编码</th><th>物料名称</th><th>规格</th><th>单位</th><th>数量</th><th>未税单价</th><th>含税金额</th><th>备注</th></tr></thead>
        <tbody>
          <tr v-for="line in lines" :key="line.lineId"><td>{{ line.lineNo }}</td><td>{{ line.materialCode || '—' }}</td><td>{{ line.materialName || '—' }}</td><td>{{ line.specification || '—' }}</td><td>{{ line.unit || '—' }}</td><td class="number">{{ line.orderQty }}</td><td class="number">{{ line.unitPrice ?? '—' }}</td><td class="number">{{ money(line.lineAmount, order.currency) }}</td><td>{{ line.remark || '—' }}</td></tr>
          <tr v-if="!lines.length"><td colspan="9">暂无订单明细</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { printCSS } from './printCSS'
import type { SalSalesOrder, SalSalesOrderLine } from '@/types/models'

defineProps<{ order: SalSalesOrder; lines: SalSalesOrderLine[] }>()
const printRef = ref<HTMLElement>()
function money(value?: number, currency?: string) {
  return value == null ? '—' : `${currency || '未注明币种'} ${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function doPrint() {
  if (!printRef.value) return
  const win = window.open('', '_blank', 'width=1000,height=800')
  if (!win) { ElMessage.warning('请允许浏览器打开打印窗口'); return }
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>销售订单</title><style>${printCSS()} h2{text-align:center;margin-bottom:12px}.data-table tr{break-inside:avoid}.number{text-align:right}td{overflow-wrap:anywhere}</style></head><body>${printRef.value.innerHTML}</body></html>`)
  win.document.close(); win.focus()
  setTimeout(() => { win.print(); win.close() }, 300)
}
</script>

<style scoped>
.toolbar { text-align:right; margin-bottom:12px; }
.document { padding:16px; background:#fff; color:#222; }
h2 { text-align:center; }
table { width:100%; border-collapse:collapse; table-layout:fixed; }
th, td { border:1px solid #bbb; padding:6px; overflow-wrap:anywhere; text-align:left; }
th { background:#f3f5f8; }
.number { text-align:right; }
.amount-warning { color:#b42318; font-weight:bold; }
</style>
