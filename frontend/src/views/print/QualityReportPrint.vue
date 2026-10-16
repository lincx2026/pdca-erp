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
          <div class="doc-title">质量检验报告</div>
          <div class="company-info">
            <span>地址：{{ company?.address || '—' }}</span>
            <span>电话：{{ company?.contactPhone || '—' }}</span>
          </div>
        </div>
        <div class="doc-header-right">
          <div class="doc-no-label">报告编号</div>
          <div class="doc-no-value">QR-{{ record?.defectId ?? '—' }}</div>
          <div class="doc-date">{{ todayStr }}</div>
        </div>
      </div>
      <!-- 状态水印 -->
      <div class="watermark">质检报告</div>
      <div class="a4-content">
        <!-- 基本信息 -->
        <div class="section-title">一、基本信息</div>
        <table class="info-table">
          <tr>
            <td class="label">工单ID</td><td>{{ record?.workOrderId ?? '—' }}</td>
            <td class="label">任务ID</td><td>{{ record?.taskId ?? '—' }}</td>
          </tr>
          <tr>
            <td class="label">物料编码</td><td>{{ record?.materialCode || '—' }}</td>
            <td class="label">工序名称</td><td>{{ record?.processName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">检验类型</td><td>{{ checkTypeText(record?.checkType) }}</td>
            <td class="label">检验员</td><td>{{ record?.inspectorName || '—' }}</td>
          </tr>
          <tr>
            <td class="label">检验日期</td><td>{{ record?.checkTime || record?.createTime || '—' }}</td>
            <td class="label">处置方式</td><td>{{ dispositionText(record?.disposition) }}</td>
          </tr>
        </table>
        <!-- 不良信息 -->
        <div class="section-title">二、不良信息</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:14%">不良代码</th>
              <th>不良名称</th>
              <th style="width:10%">不良等级</th>
              <th style="width:10%">不良数量</th>
              <th style="width:10%">抽样数量</th>
              <th style="width:10%">不良率</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style="text-align:center">{{ record?.defectCode || '—' }}</td>
              <td>{{ record?.defectName || '—' }}</td>
              <td style="text-align:center">{{ levelText(record?.defectLevel) }}</td>
              <td style="text-align:right">{{ record?.defectQty ?? 0 }}</td>
              <td style="text-align:right">{{ record?.sampleQty ?? 0 }}</td>
              <td style="text-align:right">{{ defectRate }}</td>
            </tr>
          </tbody>
        </table>
        <!-- 处置信息 -->
        <div class="section-title">三、处置信息</div>
        <table class="info-table">
          <tr>
            <td class="label">处置方式</td><td>{{ dispositionText(record?.disposition) }}</td>
          </tr>
          <tr>
            <td class="label">处置描述</td><td>&nbsp;</td>
          </tr>
        </table>
        <!-- 不良描述 -->
        <div class="section-title">四、不良描述</div>
        <table class="data-table">
          <tr>
            <td>&nbsp;</td>
          </tr>
          <tr>
            <td>&nbsp;</td>
          </tr>
          <tr>
            <td>&nbsp;</td>
          </tr>
        </table>
        <!-- 签字栏 -->
        <div class="section-title">五、签字确认</div>
        <table class="sign-table">
          <tr>
            <td class="sign-label">检验员</td>
            <td class="sign-blank"></td>
            <td class="sign-label">审核</td>
            <td class="sign-blank"></td>
            <td class="sign-label">质量主管</td>
            <td class="sign-blank"></td>
            <td class="sign-label">日期</td>
            <td class="sign-blank"><div class="stamp-area">盖章</div></td>
          </tr>
        </table>
        <div class="footer-note">
          <span>本报告经相关责任人签字后归档。</span>
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

const props = defineProps<{ record: any; visible: boolean }>()
const company = ref<CompanyProfile | null>(null)
const printRef = ref<HTMLElement>()

const todayStr = new Date().toLocaleDateString('zh-CN')

const defectRate = computed(() => {
  const d = Number(props.record?.defectQty ?? 0)
  const s = Number(props.record?.sampleQty ?? 0)
  if (!s || s <= 0) return '—'
  return (d / s * 100).toFixed(2) + '%'
})

const checkTypeText = (v?: string) => {
  const m: Record<string, string> = {
    IQC: '进料检', IPQC: '过程检', FQC: '成品检', OQC: '出货检', SPC: '统计过程'
  }
  return m[v || ''] || v || '—'
}
const levelText = (v?: string) => {
  const m: Record<string, string> = {
    CRITICAL: '严重', MAJOR: '主要', MINOR: '次要', SUGGESTION: '建议'
  }
  return m[v || ''] || v || '—'
}
const dispositionText = (v?: string) => {
  const m: Record<string, string> = {
    PENDING: '待处理', REWORK: '返工', SCRAP: '报废', CONCESSION: '让步', RETURN: '退回'
  }
  return m[v || ''] || v || '—'
}

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
