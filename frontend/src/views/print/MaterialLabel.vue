<template>
  <div class="material-label">
    <!-- 顶部：标签视觉预览 (40mm x 30mm 比例) -->
    <div class="preview-wrap">
      <div class="label-preview" ref="labelRef">
        <div class="lbl-row">{{ form.materialCode || '物料编码' }}</div>
        <div class="lbl-row">{{ form.materialName || '物料名称' }}</div>
        <div class="lbl-row">批次:{{ form.batchNo || '—' }}</div>
        <div class="lbl-row">数量:{{ form.quantity || 0 }}{{ form.unit || 'PCS' }}</div>
        <div class="lbl-row">{{ form.inDate || todayStr }}</div>
        <div class="lbl-barcode">‖‖‖‖ {{ form.barcode || form.materialCode || '0000000000000' }}</div>
      </div>
      <div class="preview-tip">标签预览（40mm × 30mm）</div>
    </div>

    <!-- 中间：手动填写表单（无 row 数据时使用） -->
    <el-form :model="form" label-width="80px" size="small" class="label-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="物料编码"><el-input v-model="form.materialCode" placeholder="物料编码" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="物料名称"><el-input v-model="form.materialName" placeholder="物料名称" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="批次号"><el-input v-model="form.batchNo" placeholder="批次号" /></el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="数量"><el-input v-model="form.quantity" placeholder="数量" /></el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="单位"><el-input v-model="form.unit" placeholder="PCS" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="日期"><el-input v-model="form.inDate" placeholder="YYYY-MM-DD" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="条码值"><el-input v-model="form.barcode" placeholder="默认取物料编码" /></el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- TSPL 指令区 -->
    <div class="tspl-wrap">
      <div class="tspl-title">TSPL 打印指令</div>
      <el-input v-model="tspl" type="textarea" :rows="9" readonly />
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
  materialCode: '',
  materialName: '',
  batchNo: '',
  quantity: '',
  unit: 'PCS',
  inDate: '',
  barcode: ''
})

const syncRow = () => {
  if (!props.row) return
  const r = props.row
  form.materialCode = r.materialCode || ''
  form.materialName = r.materialName || ''
  form.batchNo = r.batchNo || ''
  form.quantity = r.quantity != null ? String(r.quantity) : ''
  form.unit = r.unit || 'PCS'
  form.inDate = r.inDate || r.createTime || ''
  form.barcode = r.barcode || r.materialCode || ''
}

watch(() => props.visible, (v) => { if (v) syncRow() })
onMounted(() => syncRow())

const barcodeValue = computed(() => form.barcode || form.materialCode || '0000000000000')

const tspl = computed(() => {
  return [
    'SIZE 40mm,30mm',
    'GAP 2mm,0mm',
    'DIRECTION 1,0',
    'CLS',
    `TEXT 10,10,"TSS24.BF2",0,1,1,"物料编码"`,
    `TEXT 10,40,"TSS24.BF2",0,1,1,"${form.materialCode || ''}"`,
    `TEXT 10,70,"TSS24.BF2",0,1,1,"${form.materialName || ''}"`,
    `TEXT 10,100,"TSS24.BF2",0,1,1,"批次:${form.batchNo || '—'}"`,
    `TEXT 10,130,"TSS24.BF2",0,1,1,"数量:${form.quantity || 0}${form.unit || 'PCS'}"`,
    `TEXT 10,160,"TSS24.BF2",0,1,1,"${form.inDate || todayStr}"`,
    `BARCODE 10,190,"EAN13",60,1,0,2,2,"${barcodeValue.value}"`,
    'PRINT 1'
  ].join('\n')
})

const copyTspl = async () => {
  try {
    await navigator.clipboard.writeText(tspl.value)
    ElMessage.success('TSPL 指令已复制到剪贴板')
  } catch {
    // 回退方案
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
  const win = window.open('', '_blank', 'width=400,height=300')
  if (!win) { ElMessage.warning('请允许弹窗以打印'); return }
  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>物料标签</title><style>${printCSS()}</style></head><body>${el.outerHTML}</body></html>`)
  win.document.close()
  win.focus()
  setTimeout(() => { win.print(); win.close() }, 400)
}

const printCSS = () => `
  @page { size: 40mm 30mm; margin: 1mm; }
  * { margin:0; padding:0; box-sizing:border-box; font-family: 'Microsoft YaHei', 'SimSun', sans-serif; }
  body { color:#000; }
  .label-preview { width: 38mm; height: 28mm; border:1px dashed #999; padding:1mm; font-size:9px; line-height:1.35; display:flex; flex-direction:column; justify-content:space-between; }
  .lbl-row { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .lbl-barcode { font-family: 'Libre Barcode39','C39HrP60DlTt',monospace; font-size:10px; letter-spacing:0; }
`
</script>

<style scoped>
.material-label { display:flex; flex-direction:column; gap:12px; }
.preview-wrap { display:flex; flex-direction:column; align-items:center; gap:6px; }
.label-preview {
  width: 240px; height: 180px; /* 40:30 比例放大 */
  border:1px dashed #999; padding:8px; background:#fff;
  font-size:12px; line-height:1.4; color:#000;
  display:flex; flex-direction:column; justify-content:space-between;
}
.lbl-row { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.lbl-barcode { font-family: monospace; font-size:13px; border-top:1px solid #ccc; padding-top:2px; }
.preview-tip { font-size:12px; color:#909399; }
.label-form { border-top:1px solid #eee; padding-top:8px; }
.tspl-wrap .tspl-title { font-weight:600; font-size:13px; margin-bottom:6px; }
.action-bar { display:flex; justify-content:center; gap:12px; padding-top:4px; }
</style>
