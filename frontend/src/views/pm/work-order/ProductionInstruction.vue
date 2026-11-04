<template>
  <el-dialog :model-value="modelValue" title="生产指令" width="min(1200px, 95vw)" top="4vh" @update:model-value="$emit('update:modelValue', $event)">
    <div v-loading="loading">
      <el-alert title="保存后供生产人员查看和打印；图片和固件附件归属于当前工单。" type="info" :closable="false" show-icon />
      <el-form :model="form" label-width="100px" :disabled="!canEdit" class="instruction-form">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="品牌名称"><el-input v-model="form.brandName" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="品牌型号"><el-input v-model="form.brandModel" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="整机型号"><el-input v-model="form.deviceModel" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="蓝牙名称"><el-input v-model="form.bluetoothName" maxlength="200" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="设备类型"><el-input v-model="form.deviceType" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="加工厂"><el-input :model-value="order.factoryName || ''" disabled /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="生产备注"><el-input v-model="form.productionNote" type="textarea" :rows="2" maxlength="2000" show-word-limit /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="固件说明"><el-input v-model="form.firmwareDescription" type="textarea" :rows="2" maxlength="1000" show-word-limit /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="产品图片">
            <el-select v-model="form.productImageId" clearable placeholder="选择已上传图片" @change="ensureImage" style="width:100%">
              <el-option v-for="a in images" :key="a.attachmentId" :label="a.fileName" :value="a.attachmentId" />
            </el-select>
            <label class="upload-label">上传图片<input type="file" accept="image/png,image/jpeg" :disabled="!canEdit || uploading" @change="upload($event, 'IMAGE', -1)" /></label>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="固件程序">
            <el-select v-model="form.firmwareAttachmentId" clearable placeholder="选择已上传固件" style="width:100%">
              <el-option v-for="a in firmwareFiles" :key="a.attachmentId" :label="a.fileName" :value="a.attachmentId" />
            </el-select>
            <label class="upload-label">上传固件<input type="file" :disabled="!canEdit || uploading" @change="upload($event, 'FIRMWARE', -1)" /></label>
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="line-toolbar">
        <b>配件和图文说明</b>
        <div v-if="canEdit">
          <el-button v-hasPermi="'pm:workbom:explode'" size="small" :disabled="uploading" @click="importBom">从工单 BOM 带入</el-button>
          <el-button size="small" type="primary" :disabled="uploading || form.items.length >= 200" @click="form.items.push({ name: '', code: '', description: '', unit: '' })">添加配件</el-button>
        </div>
      </div>
      <div v-if="form.items.length" class="item-labels"><span>名称</span><span>编码</span><span>数量</span><span>单位</span><span>图片</span><span></span><span></span></div>
      <div v-for="(item, i) in form.items" :key="i" class="item-edit">
        <el-input v-model="item.name" :disabled="!canEdit" placeholder="名称" maxlength="200" aria-label="配件名称" />
        <el-input v-model="item.code" :disabled="!canEdit" placeholder="编码" maxlength="100" aria-label="配件编码" />
        <el-input-number v-model="item.quantity" :disabled="!canEdit" :min="0" :controls="false" placeholder="数量" aria-label="配件数量" />
        <el-input v-model="item.unit" :disabled="!canEdit" placeholder="单位" maxlength="20" aria-label="配件单位" />
        <el-select v-model="item.imageId" :disabled="!canEdit" clearable placeholder="选择图片" @change="ensureImage">
          <el-option v-for="a in images" :key="a.attachmentId" :label="a.fileName" :value="a.attachmentId" />
        </el-select>
        <label v-if="canEdit" class="upload-label">上传图片<input type="file" accept="image/png,image/jpeg" :disabled="uploading" @change="upload($event, 'IMAGE', i)" /></label>
        <el-button v-if="canEdit" link type="danger" :disabled="uploading" @click="form.items.splice(i, 1)">移除</el-button>
        <el-input v-model="item.description" class="item-description" :disabled="!canEdit" placeholder="规格、操作要点或图文说明" type="textarea" :rows="2" maxlength="1000" aria-label="配件说明" />
      </div>
      <div class="line-toolbar"><b>指令预览</b><el-button v-if="form.firmwareAttachmentId" @click="downloadFirmware">下载固件</el-button></div>
      <div ref="preview" class="instruction-preview">
        <h2>生产指令 · {{ order.orderNo }}</h2>
        <div class="summary-grid">
          <div><b>物料：</b>{{ order.materialCode }} {{ order.materialName }}</div><div><b>工单数量：</b>{{ order.orderQty }} {{ order.unit }}</div>
          <div><b>销售订单：</b>{{ order.saleOrderNo || '—' }}</div><div><b>客户订单号：</b>{{ order.customerOrderNo || '—' }}</div>
          <div><b>品牌名称：</b>{{ form.brandName || '—' }}</div><div><b>品牌型号：</b>{{ form.brandModel || '—' }}</div>
          <div><b>加工厂：</b>{{ order.factoryName || '—' }}</div><div><b>整机型号：</b>{{ form.deviceModel || '—' }}</div>
          <div><b>蓝牙名称：</b>{{ form.bluetoothName || '—' }}</div><div><b>设备类型：</b>{{ form.deviceType || '—' }}</div>
        </div>
        <img v-if="form.productImageId && imageUrls[form.productImageId]" class="product-image" :src="imageUrls[form.productImageId]" alt="产品图片" />
        <p><b>生产备注：</b>{{ form.productionNote || '—' }}</p>
        <p><b>固件：</b>{{ selectedFirmwareName }}　{{ form.firmwareDescription }}</p>
        <h3>齐套物料与操作说明</h3>
        <div v-if="!form.items.length">暂无配件说明</div>
        <div class="preview-items">
          <div v-for="(item, i) in form.items" :key="i" class="preview-item">
            <img v-if="item.imageId && imageUrls[item.imageId]" :src="imageUrls[item.imageId]" :alt="item.name || '配件图片'" />
            <div v-else class="image-placeholder">无图片</div>
            <div><b>名称：</b>{{ item.name || '—' }}</div><div><b>编码：</b>{{ item.code || '—' }}</div>
            <div><b>数量：</b>{{ item.quantity ?? '—' }} {{ item.unit }}</div>
            <p>{{ item.description || '—' }}</p>
          </div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
      <el-button @click="printInstruction">打印预览</el-button>
      <el-button v-if="canEdit" type="primary" :loading="saving" :disabled="loading || uploading || !loaded" @click="save">保存指令</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { workInstructionApi, workBomApi } from '@/api'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'
import type { PmmWorkOrder, PmmWorkOrderBom } from '@/types/models'

interface Item { materialId?: number; code?: string; name?: string; description?: string; quantity?: number; unit?: string; imageId?: number }
interface Attachment { attachmentId: number; fileName: string; kind: 'IMAGE' | 'FIRMWARE' }
interface Instruction { version: number; brandName?: string; brandModel?: string; deviceModel?: string; deviceType?: string; bluetoothName?: string; productionNote?: string; firmwareDescription?: string; productImageId?: number; firmwareAttachmentId?: number; items: Item[] }
const props = defineProps<{ modelValue: boolean; order: PmmWorkOrder }>()
defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()
const user = useUserStore()
const canEdit = computed(() => hasPerm('pm:workorder:edit', user.userInfo?.permissions))
const id = props.order.workOrderId!
const loading = ref(false), saving = ref(false), uploading = ref(false), loaded = ref(false)
const preview = ref<HTMLElement>()
const form = reactive<Instruction>({ version: 0, items: [] })
const attachments = ref<Attachment[]>([])
const imageUrls = reactive<Record<number, string>>({})
const images = computed(() => attachments.value.filter(a => a.kind === 'IMAGE'))
const firmwareFiles = computed(() => attachments.value.filter(a => a.kind === 'FIRMWARE'))
const selectedFirmwareName = computed(() => firmwareFiles.value.find(a => a.attachmentId === form.firmwareAttachmentId)?.fileName || '未选择')

async function ensureImage(attachmentId?: number) {
  if (!attachmentId || imageUrls[attachmentId]) return
  try { const response: any = await workInstructionApi.file(id, attachmentId); imageUrls[attachmentId] = URL.createObjectURL(response.data) }
  catch { ElMessage.error('图片加载失败') }
}
async function load() {
  loading.value = true
  try {
    const [data, files] = await Promise.all([workInstructionApi.get(id), workInstructionApi.attachments(id)])
    Object.assign(form, data, { items: data?.items || [] })
    attachments.value = files || []
    await Promise.all([ensureImage(form.productImageId), ...form.items.map(item => ensureImage(item.imageId))])
    loaded.value = true
  } catch { ElMessage.error('生产指令加载失败') } finally { loading.value = false }
}
async function upload(event: Event, kind: 'IMAGE' | 'FIRMWARE', index: number) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const result = await workInstructionApi.upload(id, kind, file)
    attachments.value = await workInstructionApi.attachments(id)
    if (kind === 'FIRMWARE') form.firmwareAttachmentId = result.attachmentId
    else if (index < 0) form.productImageId = result.attachmentId
    else if (form.items[index]) form.items[index].imageId = result.attachmentId
    if (kind === 'IMAGE') await ensureImage(result.attachmentId)
    ElMessage.success('上传成功，请保存指令')
  } catch (e: any) { ElMessage.error(e?.message || '上传失败') }
  finally { input.value = ''; uploading.value = false }
}
async function importBom() {
  if (form.items.length) {
    try { await ElMessageBox.confirm('带入 BOM 将追加到现有配件说明，是否继续？', '提示') } catch { return }
  }
  try {
    const rows: PmmWorkOrderBom[] = await workBomApi.listByWorkOrder(id)
    if (!rows.length) { ElMessage.warning('该工单没有 BOM 明细，请先维护工单 BOM'); return }
    if (rows.length + form.items.length > 200) { ElMessage.warning('配件说明不能超过 200 条'); return }
    form.items.push(...rows.map(row => ({ materialId: row.materialId, code: row.materialCode, name: row.materialName, description: row.specification, quantity: row.requiredQty, unit: row.unit })))
  } catch (e: any) { ElMessage.error(e?.message || '读取 BOM 失败') }
}
async function save() {
  saving.value = true
  try { await workInstructionApi.save(id, form); await load(); ElMessage.success('生产指令已保存') }
  catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { saving.value = false }
}
async function downloadFirmware() {
  if (!form.firmwareAttachmentId) return
  try {
    const response: any = await workInstructionApi.file(id, form.firmwareAttachmentId)
    const url = URL.createObjectURL(response.data), link = document.createElement('a')
    link.href = url; link.download = selectedFirmwareName.value; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 10000)
  } catch { ElMessage.error('固件下载失败') }
}
function printInstruction() {
  if (!preview.value) return
  const win = window.open('', '_blank', 'width=1100,height=800')
  if (!win) { ElMessage.warning('请允许浏览器打开打印窗口'); return }
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>生产指令</title><style>body{font:14px sans-serif;color:#222;padding:20px}h2,h3{background:#3075b0;color:white;padding:10px}.summary-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:8px}.product-image,.preview-item img{max-width:150px;max-height:150px;object-fit:contain}.preview-items{display:flex;flex-wrap:wrap;gap:12px}.preview-item{border:1px solid #ddd;padding:10px;width:180px;break-inside:avoid}.preview-item p,body p{white-space:pre-wrap}.image-placeholder{height:100px;color:#777}</style></head><body>${preview.value.innerHTML}</body></html>`)
  win.document.close()
  Promise.all(Array.from(win.document.images).map(img => img.decode().catch(() => {}))).then(() => { win.focus(); win.print() })
}
onMounted(load)
onUnmounted(() => Object.values(imageUrls).forEach(url => URL.revokeObjectURL(url)))
</script>

<style scoped>
.instruction-form { margin-top: 16px; }
.line-toolbar { display:flex; justify-content:space-between; align-items:center; gap:12px; margin:18px 0 10px; }
.item-edit, .item-labels { display:grid; grid-template-columns:2fr 1.5fr 100px 80px 1.5fr 88px 44px; gap:8px; }
.item-edit { margin-bottom:12px; padding:12px; border:1px solid var(--el-border-color); border-radius:4px; }
.item-labels { padding:0 12px 6px; color:var(--el-text-color-secondary); font-size:13px; }
.item-description { grid-column:1 / -1; }
.upload-label { position:relative; display:inline-flex; justify-content:center; align-items:center; margin:6px 0; padding:5px 8px; color:var(--el-color-primary); border:1px solid var(--el-border-color); border-radius:4px; cursor:pointer; white-space:nowrap; overflow:hidden; }
.upload-label:focus-within { outline:2px solid var(--el-color-primary); outline-offset:2px; }
.upload-label input { position:absolute; inset:0; width:100%; height:100%; opacity:0; cursor:pointer; }
.instruction-preview { border:1px solid var(--el-border-color); padding:16px; color:var(--el-text-color-primary); }
.instruction-preview h2, .instruction-preview h3 { color:#fff; background:#3075b0; padding:8px; }
.instruction-preview p { white-space:pre-wrap; overflow-wrap:anywhere; }
.summary-grid { display:grid; grid-template-columns:repeat(2,1fr); gap:10px; }
.product-image, .preview-item img { max-width:150px; max-height:150px; object-fit:contain; }
.preview-items { display:flex; flex-wrap:wrap; gap:12px; }
.preview-item { border:1px solid var(--el-border-color); padding:10px; width:190px; overflow-wrap:anywhere; }
.image-placeholder { height:100px; color:var(--el-text-color-secondary); }
@media (max-width: 800px) { .item-edit { grid-template-columns:1fr 1fr; } .item-labels { display:none; } .summary-grid { grid-template-columns:1fr; } }
</style>
