<template>
  <div class="wms-stockin-page">
    <!-- 查询区 -->
    <el-card shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent>
        <el-form-item label="入库单号">
          <el-input v-model="q.orderNo" placeholder="模糊" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="q.warehouseId" placeholder="全部" clearable filterable style="width:180px">
            <el-option v-for="w in warehouseOpts" :key="w.warehouseId"
                       :label="w.warehouseName" :value="w.warehouseId!" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库类型">
          <el-select v-model="q.inType" placeholder="全部" clearable style="width:150px">
            <el-option v-for="t in IN_TYPES" :key="t.val" :label="t.label" :value="t.val" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:140px">
            <el-option v-for="s in STATUS_OPTS" :key="s.val" :label="s.label" :value="s.val" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="pager.pageNum = 1; loadList()" :icon="Search">搜索</el-button>
          <el-button @click="resetQ" :icon="Refresh">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left:auto">
          <el-button type="primary" @click="openAdd()" :icon="Plus" v-hasPermi="'wms:stockin:add'">新增入库单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="mt-16">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="orderNo" label="入库单号" width="200">
          <template #default="{ row }"><b>{{ row.orderNo }}</b></template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="仓库" min-width="140" show-overflow-tooltip />
        <el-table-column label="入库类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="primary" effect="plain">{{ inTypeLabel(row.inType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName || '-' }}</template>
        </el-table-column>
        <el-table-column label="总数量" width="110" align="right">
          <template #default="{ row }">{{ fmtNum(row.totalQty) }}</template>
        </el-table-column>
        <el-table-column label="总金额" width="130" align="right">
          <template #default="{ row }">¥ {{ fmtMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="380" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 草稿(0): 编辑、提交审核、删除 -->
            <template v-if="row.status === 0">
              <el-button link size="small" type="primary" @click="openEdit(row)" v-hasPermi="'wms:stockin:edit'">编辑</el-button>
              <el-button link size="small" type="warning" @click="doSubmit(row)">提交审核</el-button>
              <el-button link size="small" type="danger" @click="doRemove(row)" v-hasPermi="'wms:stockin:remove'">删除</el-button>
            </template>
            <!-- 待审核(1): 审核通过、驳回 -->
            <template v-else-if="row.status === 1">
              <el-button link size="small" type="success" @click="doAudit(row)">审核通过</el-button>
              <el-button link size="small" type="danger" @click="doReject(row)">驳回</el-button>
            </template>
            <!-- 已审核(2): 确认入库 -->
            <template v-else-if="row.status === 2">
              <el-button link size="small" type="success" @click="doConfirm(row)">确认入库</el-button>
            </template>
            <!-- 已入库(3): 详情 -->
            <template v-else-if="row.status === 3">
              <el-button link size="small" type="primary" @click="openView(row)">详情</el-button>
            </template>
            <!-- 已驳回(4): 编辑、提交审核 -->
            <template v-else-if="row.status === 4">
              <el-button link size="small" type="primary" @click="openEdit(row)" v-hasPermi="'wms:stockin:edit'">编辑</el-button>
              <el-button link size="small" type="warning" @click="doSubmit(row)">提交审核</el-button>
            </template>
            <!-- 打印/标签：所有状态可见 -->
            <el-button link size="small" type="primary" @click="openPrint(row)">打印</el-button>
            <el-button link size="small" type="info" @click="openLabel(row)">标签</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination background layout="total, sizes, prev, pager, next, jumper"
          :total="pager.total" v-model:current-page="pager.pageNum"
          v-model:page-size="pager.pageSize" :page-sizes="[20, 50, 100, 200]"
          @size-change="loadList" @current-change="loadList" />
      </div>
    </el-card>

    <!-- 新增/编辑/详情 对话框 -->
    <el-dialog v-model="dlg.visible" :title="dlg.title" width="1100px" destroy-on-close top="5vh" class="stockin-dlg">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="dlg.viewOnly">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="入库单号" prop="orderNo">
              <el-input v-model="form.orderNo" readonly placeholder="自动生成">
                <template #append>
                  <el-button :icon="Refresh" @click="genNextNo" :disabled="dlg.viewOnly">生成</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" filterable style="width:100%">
                <el-option v-for="w in warehouseOpts" :key="w.warehouseId"
                           :label="w.warehouseName" :value="w.warehouseId!" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="入库类型" prop="inType">
              <el-select v-model="form.inType" placeholder="请选择" style="width:100%">
                <el-option v-for="t in IN_TYPES" :key="t.val" :label="t.label" :value="t.val" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="form.supplierName" placeholder="选填" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-tag size="small" :type="statusTagType(form.status)">{{ statusLabel(form.status) }}</el-tag>
              <span class="hint ml-8" v-if="form.rejectReason">驳回原因：{{ form.rejectReason }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 明细表格 -->
      <div class="lines-bar">
        <span class="lines-title">入库明细</span>
        <el-button size="small" type="primary" plain :icon="Plus" @click="addLine"
          v-if="!dlg.viewOnly" v-hasPermi="'wms:stockin:edit'">新增行</el-button>
      </div>
      <el-table :data="lineRows" border size="small" max-height="380">
        <el-table-column label="物料" min-width="300">
          <template #default="{ row }">
            <div style="display:flex;gap:4px">
              <el-select v-model="row.materialCode" filterable remote allow-create default-first-option
                placeholder="编码(搜索/手输)" :remote-method="searchMaterial"
                style="flex:1" @change="(v:any)=>onMaterialChange(row, v)" :disabled="dlg.viewOnly">
                <el-option v-for="m in materialOpts" :key="m.materialId"
                           :label="`${m.materialCode} - ${m.materialName}`"
                           :value="m.materialCode" />
              </el-select>
              <el-input v-model="row.materialName" placeholder="名称" style="flex:1" :disabled="dlg.viewOnly" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="130">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="批次号" maxlength="64" :disabled="dlg.viewOnly" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="110" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="4" :controls="false" size="small"
              style="width:100%" @change="calcLine(row)" :disabled="dlg.viewOnly" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80" align="center">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="PCS" :disabled="dlg.viewOnly" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false" size="small"
              style="width:100%" @change="calcLine(row)" :disabled="dlg.viewOnly" />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <b style="color:#409eff">¥ {{ fmtMoney(row.amount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="备注" maxlength="200" :disabled="dlg.viewOnly" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right" align="center" v-if="!dlg.viewOnly">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" :icon="Delete"
              @click="lineRows.splice($index,1)" v-hasPermi="'wms:stockin:edit'" />
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部合计 -->
      <div class="totals-bar">
        <span>总计数量：<b>{{ fmtNum(totalQty) }}</b></span>
        <span>总计金额：<b style="color:#e6a23c">¥ {{ fmtMoney(totalAmount) }}</b></span>
      </div>

      <template #footer>
        <el-button @click="dlg.visible = false">{{ dlg.viewOnly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!dlg.viewOnly" type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览 Dialog -->
    <el-dialog v-model="printVisible" title="打印预览 - 入库单" width="800px" destroy-on-close>
      <StockInPrint :order="printRow" :visible="printVisible" />
    </el-dialog>

    <!-- 物料标签 Dialog -->
    <el-dialog v-model="labelVisible" title="物料标签打印" width="600px" destroy-on-close>
      <MaterialLabel :row="labelRow" :visible="labelVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { stockInApi, warehouseApi, custMapApi } from '@/api'
import type { WmsStockIn, WmsStockInLine } from '@/types/models'
import StockInPrint from '@/views/print/StockInPrint.vue'
import MaterialLabel from '@/views/print/MaterialLabel.vue'

// ============ 常量映射 ============
const IN_TYPES = [
  { label: '采购入库', val: 'PURCHASE' },
  { label: '生产入库', val: 'PRODUCTION' },
  { label: '退货入库', val: 'RETURN' },
  { label: '其他', val: 'OTHER' }
]
const STATUS_OPTS = [
  { label: '草稿', val: 0 },
  { label: '待审核', val: 1 },
  { label: '已审核', val: 2 },
  { label: '已入库', val: 3 },
  { label: '已驳回', val: 4 }
]
function inTypeLabel(t: string) {
  return IN_TYPES.find(x => x.val === t)?.label || t || '-'
}
function statusLabel(s?: number) {
  return STATUS_OPTS.find(x => x.val === s)?.label || '草稿'
}
function statusTagType(s?: number): 'info' | 'warning' | 'primary' | 'success' | 'danger' {
  switch (s) {
    case 1: return 'warning'
    case 2: return 'primary'
    case 3: return 'success'
    case 4: return 'danger'
    default: return 'info' // 0 草稿
  }
}
function fmtNum(v: any) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  return Number.isNaN(n) ? '0' : Math.round(n * 1_000_000) / 1_000_000
}
function fmtMoney(v: any) {
  if (v === null || v === undefined || v === '') return '0.00'
  const n = Number(v)
  return Number.isNaN(n) ? '0.00' : n.toFixed(2)
}

// ============ 下拉数据 ============
const warehouseOpts = ref<any[]>([])
const materialOpts = ref<any[]>([])
async function loadWarehouseOpts() {
  try { warehouseOpts.value = (await warehouseApi.options()) || [] }
  catch { warehouseOpts.value = [] }
}
async function loadMaterialOpts(keyword?: string) {
  try { materialOpts.value = (await custMapApi.materialOptions(keyword)) || [] }
  catch { materialOpts.value = [] }
}
function searchMaterial(query: string) { loadMaterialOpts(query) }
function onMaterialChange(row: any, code: string) {
  const m = materialOpts.value.find((x: any) => x.materialCode === code)
  if (m) {
    row.materialId = m.materialId
    row.materialName = m.materialName
    row.specification = m.specification
    if (!row.unit) row.unit = 'PCS'
  } else {
    // 手输：无匹配选项
    row.materialId = undefined
  }
}

// ============ 查询分页 ============
const loading = ref(false)
const rows = ref<WmsStockIn[]>([])
const q = reactive({
  orderNo: '',
  warehouseId: undefined as number | undefined,
  inType: undefined as string | undefined,
  status: undefined as number | undefined
})
const pager = reactive({ pageNum: 1, pageSize: 20, total: 0 })
async function loadList() {
  loading.value = true
  try {
    const resp: any = await stockInApi.list({
      ...q,
      pageNum: pager.pageNum,
      pageSize: pager.pageSize
    })
    rows.value = resp?.rows || []
    pager.total = resp?.total || 0
  } finally { loading.value = false }
}
function resetQ() {
  Object.assign(q, { orderNo: '', warehouseId: undefined, inType: undefined, status: undefined })
  pager.pageNum = 1
  loadList()
}

// ============ 新增/编辑/详情 对话框 ============
const formRef = ref<FormInstance>()
const submitting = ref(false)
const dlg = reactive<{ visible: boolean; isEdit: boolean; viewOnly: boolean; title: string }>({
  visible: false, isEdit: false, viewOnly: false, title: ''
})
const form = reactive<Partial<WmsStockIn>>({
  orderNo: '', warehouseId: undefined as any, inType: 'PURCHASE',
  supplierName: '', status: 0, remark: ''
})
const lineRows = ref<any[]>([])

const rules: FormRules = {
  orderNo: [{ required: true, message: '请生成入库单号', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  inType: [{ required: true, message: '请选择入库类型', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, {
    stockInId: undefined, orderNo: '', warehouseId: undefined as any, inType: 'PURCHASE',
    supplierId: undefined, supplierName: '', totalQty: 0, totalAmount: 0,
    status: 0, auditBy: '', auditTime: '', rejectReason: '', remark: ''
  })
  lineRows.value = []
}
async function genNextNo() {
  try {
    const r: any = await stockInApi.nextNo()
    form.orderNo = (typeof r === 'string') ? r : (r?.data ?? r)
  } catch {}
}
function calcLine(row: any) {
  const qty = Number(row.quantity ?? 0)
  const price = Number(row.unitPrice ?? 0)
  row.amount = Math.round(qty * price * 100) / 100
}
const totalQty = computed(() => lineRows.value.reduce((s, r) => s + Number(r.quantity ?? 0), 0))
const totalAmount = computed(() =>
  lineRows.value.reduce((s, r) => s + Number(r.amount ?? 0), 0)
)
function addLine() {
  lineRows.value.push({
    materialId: undefined,
    materialCode: '', materialName: '', specification: '',
    batchNo: '', quantity: 0, unit: 'PCS', unitPrice: 0, amount: 0, remark: ''
  })
}
function syncLinesMaterialOpts() {
  // 确保编辑/详情回显时已存在的物料在 select 选项中可见
  for (const l of lineRows.value) {
    if (l.materialCode && !materialOpts.value.find((m: any) => m.materialCode === l.materialCode)) {
      materialOpts.value.push({
        materialId: l.materialId, materialCode: l.materialCode,
        materialName: l.materialName, specification: l.specification
      })
    }
  }
}

async function openAdd() {
  resetForm()
  dlg.isEdit = false
  dlg.viewOnly = false
  dlg.title = '新增入库单'
  await loadMaterialOpts()
  await genNextNo()
  dlg.visible = true
}
async function openEdit(row: WmsStockIn) {
  resetForm()
  const detail: any = await stockInApi.get(row.stockInId!)
  Object.assign(form, detail)
  lineRows.value = ((detail?.lines || []) as any[]).map((l: any) => ({ ...l }))
  await loadMaterialOpts()
  syncLinesMaterialOpts()
  dlg.isEdit = true
  dlg.viewOnly = false
  dlg.title = '编辑入库单 - ' + detail.orderNo
  dlg.visible = true
}
async function openView(row: WmsStockIn) {
  resetForm()
  const detail: any = await stockInApi.get(row.stockInId!)
  Object.assign(form, detail)
  lineRows.value = ((detail?.lines || []) as any[]).map((l: any) => ({ ...l }))
  await loadMaterialOpts()
  syncLinesMaterialOpts()
  dlg.isEdit = false
  dlg.viewOnly = true
  dlg.title = '入库单详情 - ' + detail.orderNo
  dlg.visible = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!lineRows.value.length) {
    ElMessage.warning('请至少添加一条入库明细')
    return
  }
  for (const l of lineRows.value) {
    if (!l.materialCode) {
      ElMessage.warning('明细存在未填写物料的行，请填写或移除')
      return
    }
    if (Number(l.quantity ?? 0) <= 0) {
      ElMessage.warning('明细数量必须大于 0')
      return
    }
  }
  submitting.value = true
  try {
    const payload: Partial<WmsStockIn> = {
      ...form,
      totalQty: totalQty.value,
      totalAmount: totalAmount.value,
      lines: buildCleanLines()
    }
    if (dlg.isEdit) {
      await stockInApi.edit(payload)
      ElMessage.success('修改成功')
    } else {
      await stockInApi.add(payload)
      ElMessage.success('新增成功，当前状态【草稿】，可点击列表「提交审核」送审')
    }
    dlg.visible = false
    loadList()
  } finally { submitting.value = false }
}
function buildCleanLines(): WmsStockInLine[] {
  return lineRows.value.map((l: any) => ({
    lineId: l.lineId,
    stockInId: l.stockInId,
    materialId: l.materialId,
    materialCode: l.materialCode ?? '',
    materialName: l.materialName ?? '',
    specification: l.specification ?? '',
    batchNo: l.batchNo ?? '',
    quantity: l.quantity,
    unit: l.unit ?? 'PCS',
    unitPrice: l.unitPrice ?? 0,
    amount: l.amount ?? 0,
    remark: l.remark ?? ''
  }))
}

// ============ 列表行操作（状态机） ============
async function doSubmit(row: WmsStockIn) {
  await ElMessageBox.confirm(`确认提交入库单【${row.orderNo}】审核？`, '提示', { type: 'warning' })
  try { await stockInApi.submit(row.stockInId!); ElMessage.success('已提交审核'); loadList() } catch {}
}
async function doAudit(row: WmsStockIn) {
  await ElMessageBox.confirm(`确认通过入库单【${row.orderNo}】审核？`, '提示', { type: 'success' })
  try { await stockInApi.audit(row.stockInId!); ElMessage.success('审核通过'); loadList() } catch {}
}
async function doReject(row: WmsStockIn) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', `驳回 - ${row.orderNo}`, {
    confirmButtonText: '驳回', cancelButtonText: '取消', inputType: 'textarea',
    inputPlaceholder: '请输入驳回原因', inputValidator: (v) => !!v?.trim() || '驳回原因不能为空'
  })
  try { await stockInApi.reject(row.stockInId!, value.trim()); ElMessage.success('已驳回'); loadList() } catch {}
}
async function doConfirm(row: WmsStockIn) {
  await ElMessageBox.confirm(`确认将入库单【${row.orderNo}】执行入库？`, '提示', { type: 'success' })
  try { await stockInApi.confirm(row.stockInId!); ElMessage.success('入库成功'); loadList() } catch {}
}
async function doRemove(row: WmsStockIn) {
  await ElMessageBox.confirm(`确认删除入库单【${row.orderNo}】？仅草稿可删除`, '提示', { type: 'error' })
  try { await stockInApi.remove(row.stockInId!); ElMessage.success('已删除'); loadList() } catch {}
}

// ============ 打印 / 标签 ============
const printVisible = ref(false)
const printRow = ref<any>(null)
const labelVisible = ref(false)
const labelRow = ref<any>(null)

function openPrint(row: WmsStockIn) {
  printRow.value = { ...row }
  printVisible.value = true
}
function openLabel(row: WmsStockIn) {
  labelRow.value = { ...row }
  labelVisible.value = true
}

// ============ 初始化 ============
onMounted(async () => {
  await loadWarehouseOpts()
  loadList()
})
</script>

<style scoped lang="scss">
.wms-stockin-page {
  .mt-16 { margin-top: 16px }
  .pagination-wrap { display: flex; justify-content: flex-end; padding-top: 12px }
  .hint { color: #909399; font-size: 12px }
  .ml-8 { margin-left: 8px }
}
.stockin-dlg {
  .lines-bar {
    display: flex; align-items: center; justify-content: space-between;
    margin: 8px 0 8px;
    .lines-title { font-weight: 600; font-size: 14px }
  }
  .totals-bar {
    display: flex; justify-content: flex-end; gap: 32px;
    padding: 12px 4px 0; font-size: 14px;
    b { margin-left: 4px }
  }
}
</style>
