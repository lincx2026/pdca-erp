<template>
  <div class="sn-label">
    <!-- 顶部：标签视觉预览 (60mm x 40mm 比例) -->
    <div class="preview-wrap">
      <div class="label-preview" ref="labelRef">
        <div class="lbl-product">{{ form.productName || '成品名称' }}</div>
        <div class="lbl-barcode">‖‖‖‖ {{ firstSn || 'SN-CODE' }}</div>
        <div class="lbl-sn">SN:{{ firstSn || '—' }}</div>
        <div class="lbl-row">批次:{{ form.batchNo || '—' }}</div>
        <div class="lbl-row">{{ form.inDate || todayStr }}</div>
      </div>
      <div class="preview-tip">标签预览（60mm × 40mm）</div>
    </div>

    <!-- 中间：SN 生成区 -->
    <div class="sn-gen">
      <el-form :model="form" label-width="80px" size="small" class="label-form">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="成品名称"><el-input v-model="form.productName" placeholder="成品名称" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次号"><el-input v-model="form.batchNo" placeholder="批次号" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日期"><el-input v-model="form.inDate" placeholder="YYYY-MM-DD" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-form :model="gen" label-width="80px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="SN前缀">
              <el-input v-model="gen.prefix" placeholder="如 PROD-B20240901-" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="起始序号">
              <el-input-number v-model="gen.startNo" :min="1" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="打印数量">
              <el-input-number v-model="gen.qty" :min="1" :max="999" :controls="false" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="sn-list">
        <span class="sn-list-title">生成 SN（共 {{ snList.length }} 条）：</span>
        <div class="sn-items">
          <el-tag v-for="s in snList.slice(0, 20)" :key="s" size="small" type="info" effect="plain" class="sn-tag">{{ s }}</el-tag>
          <span v-if="snList.length > 20" class="more">…等 {{ snList.length }} 条</span>
          <span v-if="!snList.length" class="empty">请填写前缀与数量</span>
        </div>
      </div>
    </div>

    <!-- TSPL 指令区 -->
    <div class="tspl-wrap">
      <div class="tspl-title">TSPL 打印指令</div>
      <el-input v-model="tspl" type="textarea" :rows="10" readonly />
    </div>

    <!-- 底部按钮 -->
    <div class="action-bar">
      <el-button type="primary" @click="copyTspl">复制指令</el-button>
      <el-button @click="printLabel">浏览器打印</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{ row: any; visible: boolean }>()
const labelRef = ref<HTMLElement>()
const todayStr = new Date().toLocaleDateString('zh-CN')

const form = reactive({
  productName: '',
  batchNo: '',
  inDate: ''
})

const gen = reactive({
  prefix: '',
  startNo: 1,
  qty: 1
})

const syncRow = () => {
  if (!props.row) return
  const r = props.row
  form.productName = r.productName || ''
  form.batchNo = r.batchNo || ''
  form.inDate = r.inTime || r.createTime || ''
  // SN 规则：${productName前4位}-${batchNo}-序号
  const pName = r.productName || ''
  const head = pName ? pName.substring(0, 4) : 'PROD'
  const batch = r.batchNo || 'BATCH'
  gen.prefix = `${head}-${batch}-`
  gen.startNo = 1
  gen.qty = 1
}

watch(() => props.visible, (v) => { if (v) syncRow() })
onMounted(() => syncRow())

// 序号补零（4 位）
const padNo = (n: number) => String(n).padStart(4, '0')

const snList = computed(() => {
  if (!gen.prefix) return []
  const arr: string[] = []
  for (let i = 0; i < gen.qty; i++) {
    arr.push(`${gen.prefix}${padNo(gen.startNo + i)}`)
  }
  return arr
})

const firstSn = computed(() => snList.value[0] || '')

const tspl = computed(() => {
  if (!snList.value.length) return '请填写 SN 前缀与打印数量'
  const header = ['SIZE 60mm,40mm', 'GAP 2mm,0mm', 'DIRECTION 1,0'].join('\n')
  const blocks = snList.value.map(sn => {
    return [
      'CLS',
      `TEXT 10,10,"TSS24.BF2",0,1,1,"${form.productName || ''}"`,
      `BARCODE 10,50,"CODE128",80,1,0,2,4,"${sn}"`,
      `TEXT 10,140,"TSS24.BF2",0,1,1,"SN:${sn}"`,
      `TEXT 10,170,"TSS24.BF2",0,1,1,"批次:${form.batchNo || '—'}"`,
      `TEXT 10,200,"TSS24.BF2",0,1,1,"${form.inDate || todayStr}"`,
      'PRINT 1'
    ].join('\n')
  })
  return `${header}\n${blocks.join('\n')}`
})

const copyTspl = async () => {
  try {
    await navigator.clipboard.writeText(tspl.value)
    ElMessage.success('TSPL 指令已复制到剪贴板')
  } catch {
    const ta = document.createElement('textarea')
    ta.value = tspl.value
    document.body.appendChild(ta)
    ta.select()
    try { document.execCommand('copy'); ElMessage.success('TSPL 指令已复制') }
    catch { ElMessage.error('复制失败，请手动选取') }
    document.body.removeChild(ta)
  }
}

const printLabel = () => {
  const el = labelRef.value
  if (!el) return
  const win = window.open('', '_blank', 'width=500,height=400')
  if (!win) { ElMessage.warning('请允许弹窗以打印'); return }
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>成品SN标签</title><style>${printCSS()}</style></head><body>${el.outerHTML}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 400)
}

const printCSS = () => `
  @page { size: 60mm 40mm; margin: 1mm; }
  * { margin:0; padding:0; box-sizing:border-box; font-family: 'Microsoft YaHei', 'SimSun', sans-serif; }
  body { color:#000; }
  .label-preview { width: 58mm; height: 38mm; border:1px dashed #999; padding:1mm; font-size:9px; line-height:1.35; display:flex; flex-direction:column; justify-content:space-between; }
  .lbl-product { font-weight:bold; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .lbl-barcode { font-family: monospace; font-size:11px; }
  .lbl-sn { font-size:9px; }
  .lbl-row { font-size:9px; }
`
</script>

<style scoped>
.sn-label { display:flex; flex-direction:column; gap:12px; }
.preview-wrap { display:flex; flex-direction:column; align-items:center; gap:6px; }
.label-preview {
  width: 300px; height: 200px; /* 60:40 比例放大 */
  border:1px dashed #999; padding:10px; background:#fff;
  font-size:12px; line-height:1.5; color:#000;
  display:flex; flex-direction:column; justify-content:space-between;
}
.lbl-product { font-weight:bold; font-size:14px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.lbl-barcode { font-family: monospace; font-size:16px; }
.preview-tip { font-size:12px; color:#909399; }
.label-form { border-bottom:1px solid #eee; padding-bottom:6px; }
.sn-gen { border-top:1px solid #eee; padding-top:8px; }
.sn-list { margin-top:6px; }
.sn-list-title { font-weight:600; font-size:13px; }
.sn-items { display:flex; flex-wrap:wrap; gap:6px; margin-top:6px; }
.sn-tag { font-family: monospace; }
.more, .empty { font-size:12px; color:#909399; }
.tspl-wrap .tspl-title { font-weight:600; font-size:13px; margin-bottom:6px; }
.action-bar { display:flex; justify-content:center; gap:12px; padding-top:4px; }
</style>
