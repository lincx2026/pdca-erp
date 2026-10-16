<template>
  <div class="wms-stockout-page">
    <!-- 搜索栏 -->
    <el-card shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent>
        <el-form-item label="出库单号">
          <el-input v-model="q.orderNo" placeholder="模糊查询" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="q.warehouseId" placeholder="全部" clearable filterable style="width: 180px">
            <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="w.warehouseId!" />
          </el-select>
        </el-form-item>
        <el-form-item label="出库类型">
          <el-select v-model="q.outType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="t in OUT_TYPES" :key="t.val" :label="t.label" :value="t.val" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.val" :label="s.label" :value="s.val" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="pager.pageNum = 1; loadList()">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
        <el-form-item style="margin-left: auto">
          <el-button type="success" :icon="Plus" @click="openAdd" v-hasPermi="'wms:stockout:add'">新增出库单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" class="mt-16">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="orderNo" label="出库单号" width="180">
          <template #default="{ row }"><b>{{ row.orderNo }}</b></template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="仓库" min-width="140" show-overflow-tooltip />
        <el-table-column label="出库类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="outTypeTag(row.outType)" size="small">{{ outTypeLabel(row.outType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.customerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="totalQty" label="总数量" width="100" align="right" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 草稿(0): 编辑、提交审核、删除 -->
            <template v-if="row.status === 0">
              <el-button link size="small" type="primary" @click="openEdit(row)" v-hasPermi="'wms:stockout:edit'">编辑</el-button>
              <el-button link size="small" type="warning" @click="doSubmit(row)" v-hasPermi="'wms:stockout:edit'">提交审核</el-button>
              <el-button link size="small" type="danger" @click="doRemove(row)" v-hasPermi="'wms:stockout:remove'">删除</el-button>
            </template>
            <!-- 待审核(1): 审核通过、驳回 -->
            <template v-else-if="row.status === 1">
              <el-button link size="small" type="success" @click="doAudit(row)" v-hasPermi="'wms:stockout:edit'">审核通过</el-button>
              <el-button link size="small" type="danger" @click="doReject(row)" v-hasPermi="'wms:stockout:edit'">驳回</el-button>
            </template>
            <!-- 已审核(2): 确认出库 -->
            <template v-else-if="row.status === 2">
              <el-button link size="small" type="success" @click="doConfirm(row)" v-hasPermi="'wms:stockout:edit'">确认出库</el-button>
            </template>
            <!-- 已出库(3): 详情 -->
            <template v-else-if="row.status === 3">
              <el-button link size="small" type="primary" @click="openView(row)">详情</el-button>
            </template>
            <!-- 已驳回(4): 编辑、提交审核 -->
            <template v-else-if="row.status === 4">
              <el-button link size="small" type="primary" @click="openEdit(row)" v-hasPermi="'wms:stockout:edit'">编辑</el-button>
              <el-button link size="small" type="warning" @click="doSubmit(row)" v-hasPermi="'wms:stockout:edit'">提交审核</el-button>
            </template>
            <!-- 打印：所有状态可见 -->
            <el-button link size="small" type="primary" @click="openPrint(row)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="pager.total"
          v-model:current-page="pager.pageNum"
          v-model:page-size="pager.pageSize"
          :page-sizes="[20, 50, 100, 200]"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dlg.visible" :title="dlg.title" width="960px" destroy-on-close top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="出库单号" prop="orderNo">
              <el-input v-model="form.orderNo" placeholder="自动生成" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="form.warehouseId" placeholder="请选择仓库" filterable style="width: 100%">
                <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="w.warehouseId!" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库类型" prop="outType">
              <el-select v-model="form.outType" placeholder="请选择" style="width: 100%">
                <el-option v-for="t in OUT_TYPES" :key="t.val" :label="t.label" :value="t.val" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户">
              <el-input v-model="form.customerName" placeholder="选填" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 明细表格 -->
      <div class="lines-toolbar">
        <span class="lines-title">出库明细</span>
        <el-button size="small" type="primary" plain :icon="Plus" @click="addLine">新增行</el-button>
      </div>
      <el-table :data="form.lines" border size="small" max-height="360">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="物料" min-width="420">
          <template #default="{ row }">
            <div class="mat-cell">
              <el-select v-model="row.materialId" placeholder="输入编码/名称搜索" filterable style="width: 200px"
                @change="(v:any)=>fillMaterial(row, v)">
                <el-option v-for="m in materialOptions" :key="m.materialId"
                  :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                  <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                </el-option>
              </el-select>
              <el-input v-model="row.materialCode" placeholder="物料编码" style="width: 120px" />
              <el-input v-model="row.materialName" placeholder="物料名称" style="flex: 1" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="140">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="批次号" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="4" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="90">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="PCS" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.remark" placeholder="备注" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ $index }">
            <el-button size="small" link type="danger" :icon="Delete" @click="removeLine($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="lines-total">
        总计数量：<b class="total-num">{{ lineTotalQty }}</b>
      </div>

      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="viewDlg.visible" title="出库单详情" width="900px" destroy-on-close top="5vh">
      <el-descriptions :column="2" border size="default" class="mb-16">
        <el-descriptions-item label="出库单号">{{ viewForm.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ viewForm.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库类型">
          <el-tag :type="outTypeTag(viewForm.outType)" size="small">{{ outTypeLabel(viewForm.outType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户">{{ viewForm.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ viewForm.totalQty ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(viewForm.status)" size="small">{{ statusLabel(viewForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewForm.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核人/时间">
          {{ viewForm.auditBy || '-' }}<span v-if="viewForm.auditTime"> / {{ viewForm.auditTime }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="驳回原因" :span="2">
          <span v-if="viewForm.rejectReason" style="color: var(--el-color-danger)">{{ viewForm.rejectReason }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ viewForm.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="viewForm.lines" border size="small" max-height="360">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="150" show-overflow-tooltip />
        <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="batchNo" label="批次号" width="130" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="100" align="right" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>

      <template #footer>
        <el-button @click="viewDlg.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 打印预览 Dialog -->
    <el-dialog v-model="printVisible" title="打印预览 - 出库单" width="800px" destroy-on-close>
      <StockOutPrint :order="printRow" :visible="printVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { stockOutApi, warehouseApi, materialApi } from '@/api'
import type { WmsStockOut, WmsStockOutLine, WmsWarehouse, PdmMaterial } from '@/types/models'
import StockOutPrint from '@/views/print/StockOutPrint.vue'

// ============ 常量映射 ============
const OUT_TYPES = [
  { val: 'PRODUCTION', label: '生产领料' },
  { val: 'SALES', label: '销售出库' },
  { val: 'TRANSFER', label: '调拨出库' },
  { val: 'OTHER', label: '其他' }
]
const STATUS_OPTIONS = [
  { val: 0, label: '草稿' },
  { val: 1, label: '待审核' },
  { val: 2, label: '已审核' },
  { val: 3, label: '已出库' },
  { val: 4, label: '已驳回' }
]

function outTypeLabel(v?: string) {
  return OUT_TYPES.find(t => t.val === v)?.label || v || '-'
}
function outTypeTag(v?: string): 'success' | 'warning' | 'primary' | 'info' {
  switch (v) {
    case 'PRODUCTION': return 'warning'
    case 'SALES': return 'success'
    case 'TRANSFER': return 'primary'
    default: return 'info'
  }
}
function statusLabel(v?: number) {
  return STATUS_OPTIONS.find(s => s.val === v)?.label || '-'
}
function statusTag(v?: number): 'info' | 'warning' | 'primary' | 'success' | 'danger' {
  switch (v) {
    case 0: return 'info'
    case 1: return 'warning'
    case 2: return 'primary'
    case 3: return 'success'
    case 4: return 'danger'
    default: return 'info'
  }
}

// ============ 仓库下拉 ============
const warehouseOptions = ref<WmsWarehouse[]>([])
async function loadWarehouseOptions() {
  try {
    warehouseOptions.value = (await warehouseApi.options() as any) || []
  } catch {
    warehouseOptions.value = []
  }
}

// ============ 物料下拉 ============
const materialOptions = ref<PdmMaterial[]>([])
async function loadMaterialOptions() {
  try {
    const resp: any = await materialApi.list({ pageNum: 1, pageSize: 1000 })
    materialOptions.value = (resp?.rows || []) as PdmMaterial[]
  } catch {
    materialOptions.value = []
  }
}
function fillMaterial(row: any, id: number) {
  if (!id) return
  const m: any = materialOptions.value.find(x => x.materialId === id)
  if (m) {
    row.materialCode = m.materialCode
    row.materialName = m.materialName
    if (!row.unit) row.unit = m.unit || 'PCS'
  }
}

// ============ 查询分页 ============
const loading = ref(false)
const rows = ref<WmsStockOut[]>([])
const q = reactive({
  pageNum: 1,
  pageSize: 20,
  orderNo: '',
  warehouseId: undefined as number | undefined,
  outType: '',
  status: undefined as number | undefined
})
const pager = reactive({ pageNum: 1, pageSize: 20, total: 0 })

async function loadList() {
  loading.value = true
  try {
    q.pageNum = pager.pageNum
    q.pageSize = pager.pageSize
    const resp: any = await stockOutApi.list(q as any)
    rows.value = resp?.rows || []
    pager.total = resp?.total || 0
  } finally {
    loading.value = false
  }
}
function resetQ() {
  q.orderNo = ''
  q.warehouseId = undefined
  q.outType = ''
  q.status = undefined
  pager.pageNum = 1
  loadList()
}

// ============ 新增/编辑弹窗 ============
const formRef = ref<FormInstance>()
const submitting = ref(false)
const dlg = reactive<{ visible: boolean; isEdit: boolean; title: string }>({
  visible: false, isEdit: false, title: ''
})
const form = reactive<Partial<WmsStockOut>>({
  stockOutId: undefined,
  orderNo: '',
  warehouseId: undefined,
  outType: '',
  customerName: '',
  totalQty: 0,
  remark: '',
  lines: []
})
const rules: FormRules = {
  orderNo: [{ required: true, message: '单号不能为空', trigger: 'blur' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  outType: [{ required: true, message: '请选择出库类型', trigger: 'change' }]
}

const lineTotalQty = computed(() => {
  return (form.lines || []).reduce((s, ln) => s + (Number(ln.quantity) || 0), 0)
})

function resetForm() {
  Object.keys(form).forEach(k => delete (form as any)[k])
  Object.assign(form, {
    stockOutId: undefined,
    orderNo: '',
    warehouseId: undefined,
    outType: '',
    customerName: '',
    totalQty: 0,
    remark: '',
    lines: []
  })
}

function makeLine(): WmsStockOutLine {
  return {
    materialId: undefined as any,
    materialCode: '',
    materialName: '',
    batchNo: '',
    quantity: 0,
    unit: 'PCS',
    remark: ''
  }
}
function addLine() {
  if (!form.lines) form.lines = []
  form.lines.push(makeLine())
}
function removeLine(idx: number) {
  form.lines?.splice(idx, 1)
}

async function openAdd() {
  resetForm()
  dlg.isEdit = false
  dlg.title = '新增出库单'
  // 自动生成单号
  try {
    const no = await stockOutApi.nextNo() as any
    form.orderNo = no || ''
  } catch { /* 忽略, 用户可手填 */ }
  // 预加载物料下拉
  if (materialOptions.value.length === 0) loadMaterialOptions()
  // 默认带一行
  addLine()
  dlg.visible = true
}

async function openEdit(row: WmsStockOut) {
  resetForm()
  try {
    const detail = await stockOutApi.get(row.stockOutId!) as any
    Object.assign(form, detail)
    if (!form.lines) form.lines = []
  } catch {
    ElMessage.error('加载出库单失败')
    return
  }
  // 预加载物料下拉
  if (materialOptions.value.length === 0) loadMaterialOptions()
  dlg.isEdit = true
  dlg.title = '编辑出库单 - ' + form.orderNo
  dlg.visible = true
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.lines || form.lines.length === 0) {
    ElMessage.warning('请至少添加一条出库明细')
    return
  }
  // 校验明细物料与数量
  for (let i = 0; i < form.lines.length; i++) {
    const ln = form.lines[i]
    if (!ln.materialId) {
      ElMessage.warning(`第 ${i + 1} 行物料ID不能为空`)
      return
    }
    if (Number(ln.quantity) <= 0) {
      ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`)
      return
    }
  }
  form.totalQty = lineTotalQty.value
  submitting.value = true
  try {
    if (dlg.isEdit) {
      await stockOutApi.edit(form as any)
      ElMessage.success('修改成功')
    } else {
      await stockOutApi.add(form as any)
      ElMessage.success('新增成功，当前状态为【草稿】')
    }
    dlg.visible = false
    loadList()
  } finally {
    submitting.value = false
  }
}

// ============ 详情弹窗 ============
const viewDlg = reactive<{ visible: boolean }>({ visible: false })
const viewForm = reactive<Partial<WmsStockOut>>({})

async function openView(row: WmsStockOut) {
  try {
    const detail = await stockOutApi.get(row.stockOutId!) as any
    Object.keys(viewForm).forEach(k => delete (viewForm as any)[k])
    Object.assign(viewForm, detail)
    viewDlg.visible = true
  } catch {
    ElMessage.error('加载详情失败')
  }
}

// ============ 行操作 ============
async function doSubmit(row: WmsStockOut) {
  await ElMessageBox.confirm(`确认提交出库单【${row.orderNo}】审核？`, '提示', { type: 'warning' })
  try {
    await stockOutApi.submit(row.stockOutId!)
    ElMessage.success('已提交审核')
    loadList()
  } catch { /* ignore */ }
}
async function doAudit(row: WmsStockOut) {
  await ElMessageBox.confirm(`确认通过出库单【${row.orderNo}】审核？`, '提示', { type: 'success' })
  try {
    await stockOutApi.audit(row.stockOutId!)
    ElMessage.success('已审核通过')
    loadList()
  } catch { /* ignore */ }
}
async function doReject(row: WmsStockOut) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回出库单', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '请输入驳回原因',
    inputValidator: (v: string) => (!!v && !!v.trim()) || '驳回原因不能为空'
  })
  try {
    await stockOutApi.reject(row.stockOutId!, value.trim())
    ElMessage.success('已驳回')
    loadList()
  } catch { /* ignore */ }
}
async function doConfirm(row: WmsStockOut) {
  await ElMessageBox.confirm(`确认出库单【${row.orderNo}】完成出库？`, '提示', { type: 'success' })
  try {
    await stockOutApi.confirm(row.stockOutId!)
    ElMessage.success('已确认出库')
    loadList()
  } catch { /* ignore */ }
}
async function doRemove(row: WmsStockOut) {
  await ElMessageBox.confirm(`确认删除出库单【${row.orderNo}】？`, '提示', { type: 'error' })
  try {
    await stockOutApi.remove(row.stockOutId!)
    ElMessage.success('已删除')
    loadList()
  } catch { /* ignore */ }
}

// ============ 打印 ============
const printVisible = ref(false)
const printRow = ref<any>(null)

function openPrint(row: WmsStockOut) {
  printRow.value = { ...row }
  printVisible.value = true
}

// ============ 初始化 ============
onMounted(async () => {
  await loadWarehouseOptions()
  loadMaterialOptions()
  loadList()
})
</script>

<style scoped lang="scss">
.wms-stockout-page {
  .mt-16 { margin-top: 16px; }
  .mb-16 { margin-bottom: 16px; }
  .pagination-wrap {
    display: flex;
    justify-content: flex-end;
    padding-top: 12px;
  }
  .lines-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin: 12px 0 8px;
    .lines-title {
      font-weight: 600;
      font-size: 14px;
    }
  }
  .mat-cell {
    display: flex;
    gap: 4px;
    align-items: center;
  }
  .lines-total {
    margin-top: 10px;
    text-align: right;
    font-size: 14px;
    color: #606266;
    .total-num {
      color: var(--el-color-primary);
      font-size: 16px;
    }
  }
}
</style>
