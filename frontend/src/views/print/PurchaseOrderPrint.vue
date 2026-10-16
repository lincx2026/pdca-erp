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
            <tr v-for="i in 5" :key="i">
              <td style="text-align:center">{{ i }}</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td style="text-align:center">&nbsp;</td>
              <td style="text-align:right">&nbsp;</td>
              <td style="text-align:right">&nbsp;</td>
              <td style="text-align:right">&nbsp;</td>
              <td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
        <!-- 交货条款 -->
        <div class="section-title">二、交货条款</div>
        <table class="info-table">
          <tr>
            <td class="label">交货地址</td><td>&nbsp;</td>
            <td class="label">运输方式</td><td>&nbsp;</td>
          </tr>
          <tr>
            <td class="label">付款方式</td><td>&nbsp;</td>
            <td class="label">特别要求</td><td>&nbsp;</td>
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
          <span>第1页/共1页 · 打印日期：{{ todayStr }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { companyApi } from '@/api/index'
import { printCSS } from './printCSS'
import type { CompanyProfile } from '@/types/models'

const props = defineProps<{ order: any; visible: boolean }>()
const company = ref<CompanyProfile | null>(null)
const printRef = ref<HTMLElement>()

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
  return props.order ? statusText(props.order.status) : '—'
})

const loadData = async () => {
  try {
    company.value = await companyApi.profileGet()
  } catch (e) { console.error('print load', e) }
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
