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
          <div class="doc-title">生产工单随工单</div>
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
        <!-- 工单基本信息 -->
        <table class="info-table">
          <tr>
            <td class="label">工单号</td><td>{{ order?.orderNo }}</td>
            <td class="label">工作中心</td><td>{{ order?.workCenter || '—' }}</td>
          </tr>
          <tr>
            <td class="label">物料编码</td><td>{{ order?.materialCode || '—' }}</td>
            <td class="label">物料名称</td><td>{{ order?.materialName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">规格</td><td colspan="3">{{ order?.specification || '—' }}</td>
          </tr>
          <tr>
            <td class="label">订单数量</td><td>{{ order?.orderQty || 0 }} {{ order?.unit || 'PCS' }}</td>
            <td class="label">来源类型</td><td>{{ sourceTypeText(order?.sourceType) }}</td>
          </tr>
          <tr>
            <td class="label">已完成</td><td>{{ order?.finishedQty || 0 }} {{ order?.unit || 'PCS' }}</td>
            <td class="label">报废数量</td><td>{{ order?.scrappedQty || 0 }} {{ order?.unit || 'PCS' }}</td>
          </tr>
          <tr>
            <td class="label">计划开始</td><td>{{ order?.planStartDate || '—' }}</td>
            <td class="label">计划完成</td><td>{{ order?.planEndDate || '—' }}</td>
          </tr>
          <tr>
            <td class="label">销售订单</td><td>{{ order?.saleOrderNo || '—' }}</td>
            <td class="label">客户</td><td>{{ order?.customerName || '—' }}</td>
          </tr>
        </table>
        <!-- BOM 清单(空白行供现场手写) -->
        <div class="section-title">一、BOM清单(投料记录)</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:5%">序号</th>
              <th>物料编码/名称</th>
              <th style="width:12%">单台用量</th>
              <th style="width:8%">单位</th>
              <th style="width:15%">投料批次</th>
              <th style="width:15%">实际用量</th>
              <th style="width:15%">操作人</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="i in 8" :key="'b' + i">
              <td style="text-align:center">{{ i }}</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
        <!-- 工序流转(空白行供现场手写) -->
        <div class="section-title">二、工序流转记录</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:8%">工序号</th>
              <th style="width:22%">工序名称</th>
              <th style="width:15%">工作中心</th>
              <th style="width:10%">合格数</th>
              <th style="width:10%">不良数</th>
              <th style="width:15%">操作人</th>
              <th style="width:10%">开始时间</th>
              <th style="width:10%">结束时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="i in 6" :key="'s' + i">
              <td style="text-align:center">{{ i }}</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>
          </tbody>
        </table>
        <!-- 签字栏 -->
        <div class="section-title">三、签字确认</div>
        <table class="sign-table">
          <tr>
            <td class="sign-label">制表</td>
            <td class="sign-blank"></td>
            <td class="sign-label">审核</td>
            <td class="sign-blank"></td>
            <td class="sign-label">确认</td>
            <td class="sign-blank"><div class="stamp-area">盖章</div></td>
          </tr>
          <tr>
            <td class="sign-label">日期</td>
            <td class="sign-blank"></td>
            <td class="sign-label">日期</td>
            <td class="sign-blank"></td>
            <td class="sign-label">日期</td>
            <td class="sign-blank"></td>
          </tr>
        </table>
        <div class="footer-note">
          <span>本随工单随产品流转，完工后归档。</span>
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
import type { CompanyProfile, PmmWorkOrder } from '@/types/models'

const props = defineProps<{ order: PmmWorkOrder | null; visible: boolean }>()
const company = ref<CompanyProfile | null>(null)
const printRef = ref<HTMLElement>()

const todayStr = new Date().toLocaleDateString('zh-CN')

const watermarkText = computed(() => {
  const m: Record<number, string> = { 0: '草稿', 1: '已下达', 2: '进行中', 3: '已完工', 4: '已入库' }
  return m[Number(props.order?.status)] || '—'
})

const sourceTypeText = (t?: string) => {
  const m: Record<string, string> = {
    MTO: '按单生产', MTS: '备货生产', REWORK: '返工', SAMPLE: '打样'
  }
  return m[t || ''] || t || '—'
}

const loadData = async () => {
  if (!props.order) return
  try {
    company.value = await companyApi.profileGet()
  } catch (e) { console.error('print load', e) }
}

watch(() => props.visible, (v) => { if (v) loadData() })
onMounted(() => { if (props.order) loadData() })

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
