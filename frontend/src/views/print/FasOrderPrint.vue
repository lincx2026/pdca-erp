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
          <div class="doc-title">后道组装工单随工单</div>
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
            <td class="label">产线</td><td>{{ order?.lineName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">成品名称</td><td>{{ order?.productName }}</td>
            <td class="label">计划数量</td><td>{{ order?.qtyPlan }} PCS</td>
          </tr>
          <tr>
            <td class="label">计划开始</td><td>{{ order?.planStartDate || '—' }}</td>
            <td class="label">计划完成</td><td>{{ order?.planEndDate || '—' }}</td>
          </tr>
          <tr>
            <td class="label">完工数量</td><td>{{ order?.qtyComplete || 0 }} PCS</td>
            <td class="label">不良数量</td><td>{{ order?.qtyNg || 0 }} PCS</td>
          </tr>
        </table>
        <!-- BOM 清单 -->
        <div class="section-title">一、组装BOM清单</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:5%">序号</th>
              <th style="width:15%">子件类型</th>
              <th>子件名称</th>
              <th style="width:12%">单台用量</th>
              <th style="width:8%">单位</th>
              <th style="width:10%">投料校验</th>
              <th style="width:15%">投料批次</th>
              <th style="width:15%">实际用量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(b, i) in bomList" :key="b.bomId">
              <td style="text-align:center">{{ i + 1 }}</td>
              <td>{{ componentTypeText(b.componentType) }}</td>
              <td>{{ b.componentName }}</td>
              <td style="text-align:right">{{ b.qtyPer }}</td>
              <td style="text-align:center">{{ b.unit }}</td>
              <td style="text-align:center">{{ b.needFeedCheck ? '是' : '否' }}</td>
              <td>&nbsp;</td>
              <td>&nbsp;</td>
            </tr>
            <tr v-if="bomList.length === 0">
              <td colspan="8" style="text-align:center;color:#999;">暂无BOM数据</td>
            </tr>
          </tbody>
        </table>
        <!-- 工序流转 -->
        <div class="section-title">二、工序流转记录</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:8%">工序号</th>
              <th style="width:20%">工位名称</th>
              <th style="width:15%">工位/产线</th>
              <th style="width:8%">合格数</th>
              <th style="width:8%">不良数</th>
              <th style="width:12%">操作人</th>
              <th style="width:15%">开始时间</th>
              <th style="width:14%">结束时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in stepList" :key="s.stepId">
              <td style="text-align:center">{{ s.stepNo }}</td>
              <td>{{ s.stepName }}</td>
              <td>{{ s.workcenter || '—' }}</td>
              <td style="text-align:right">{{ s.qtyPass }}</td>
              <td style="text-align:right">{{ s.qtyNg }}</td>
              <td>{{ s.operator || '—' }}</td>
              <td>{{ s.startTime?.substring(5) || '—' }}</td>
              <td>{{ s.endTime?.substring(5) || '—' }}</td>
            </tr>
            <tr v-if="stepList.length === 0">
              <td colspan="8" style="text-align:center;color:#999;">暂无工序流转数据</td>
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
import { companyApi, pmFasApi } from '@/api/index'
import { printCSS } from './printCSS'
import type { CompanyProfile, FasOrder, FasBom, FasStep } from '@/types/models'

const props = defineProps<{ order: FasOrder | null; visible: boolean }>()
const company = ref<CompanyProfile | null>(null)
const bomList = ref<FasBom[]>([])
const stepList = ref<FasStep[]>([])
const printRef = ref<HTMLElement>()

const todayStr = new Date().toLocaleDateString('zh-CN')

const watermarkText = computed(() => {
  const m: Record<number, string> = { 0: '草稿', 1: '已下达', 2: '进行中', 3: '已完工', 4: '已入库' }
  return m[Number(props.order?.status)] || '—'
})

const componentTypeText = (t?: string) => {
  const m: Record<string, string> = {
    PCBA: 'PCBA半成品', STRUCTURE: '结构件', DISPLAY: '显示模组',
    CABLE: '线材', FASTENER: '紧固件', PACKAGING: '包材', OTHER: '其他'
  }
  return m[t || ''] || t || '—'
}

const loadData = async () => {
  if (!props.order) return
  try {
    company.value = await companyApi.profileGet()
    if (props.order.productId) {
      bomList.value = (await pmFasApi.bomByProduct(props.order.productId)) || []
    }
    if (props.order.orderId) {
      stepList.value = (await pmFasApi.stepByOrder(props.order.orderId)) || []
    }
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
