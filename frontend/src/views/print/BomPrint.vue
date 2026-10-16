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
          <div class="doc-title">组装BOM清单</div>
          <div class="company-info">
            <span>地址：{{ company?.address || '—' }}</span>
            <span>电话：{{ company?.contactPhone || '—' }}</span>
          </div>
        </div>
        <div class="doc-header-right">
          <div class="doc-no-label">成品</div>
          <div class="doc-no-value">{{ productName || '—' }}</div>
          <div class="doc-date">{{ todayStr }}</div>
        </div>
      </div>
      <!-- 状态水印 -->
      <div class="watermark">BOM清单</div>
      <div class="a4-content">
        <!-- 成品信息 -->
        <table class="info-table">
          <tr>
            <td class="label">成品名称</td><td>{{ productName || '—' }}</td>
            <td class="label">BOM行数</td><td>{{ bomList.length }} 行</td>
          </tr>
          <tr>
            <td class="label">打印日期</td><td>{{ todayStr }}</td>
            <td class="label">编制人</td><td>&nbsp;</td>
          </tr>
        </table>
        <!-- BOM 清单 -->
        <div class="section-title">一、组装BOM明细</div>
        <table class="data-table">
          <thead>
            <tr>
              <th style="width:5%">序号</th>
              <th style="width:18%">子件类型</th>
              <th>子件名称</th>
              <th style="width:12%">单台用量</th>
              <th style="width:8%">单位</th>
              <th style="width:10%">投料校验</th>
              <th style="width:10%">投料顺序</th>
              <th>备注</th>
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
              <td style="text-align:center">{{ b.feedSequence }}</td>
              <td>{{ b.remark || '' }}</td>
            </tr>
            <tr v-if="bomList.length === 0">
              <td colspan="8" style="text-align:center;color:#999;">暂无BOM数据</td>
            </tr>
          </tbody>
        </table>
        <!-- 签字栏 -->
        <div class="section-title">二、签字确认</div>
        <table class="sign-table">
          <tr>
            <td class="sign-label">制表</td>
            <td class="sign-blank"></td>
            <td class="sign-label">审核</td>
            <td class="sign-blank"></td>
            <td class="sign-label">批准</td>
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
          <span>本BOM清单为成品【{{ productName || '—' }}】的组装物料明细。</span>
          <span>第1页/共1页 · 打印日期：{{ todayStr }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { companyApi, pmFasApi } from '@/api/index'
import { printCSS } from './printCSS'
import type { CompanyProfile, FasBom } from '@/types/models'

const props = defineProps<{ productId: number | null; productName?: string; visible: boolean }>()
const company = ref<CompanyProfile | null>(null)
const bomList = ref<FasBom[]>([])
const printRef = ref<HTMLElement>()

const todayStr = new Date().toLocaleDateString('zh-CN')

const componentTypeText = (t?: string) => {
  const m: Record<string, string> = {
    PCBA: 'PCBA半成品', STRUCTURE: '结构件', DISPLAY: '显示模组',
    CABLE: '线材', FASTENER: '紧固件', PACKAGING: '包材', OTHER: '其他'
  }
  return m[t || ''] || t || '—'
}

const loadData = async () => {
  if (!props.productId) return
  try {
    company.value = await companyApi.profileGet()
    bomList.value = (await pmFasApi.bomByProduct(props.productId)) || []
  } catch (e) { console.error('print load', e) }
}

watch(() => props.visible, (v) => { if (v) loadData() })
onMounted(() => { if (props.productId) loadData() })

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
