<template>
  <div class="sal-kitting-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="齐套单号">
          <el-input v-model="q.kittingNo" placeholder="齐套单号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="销售订单号">
          <el-input v-model="q.soNo" placeholder="销售订单号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:130px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工具栏 + 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />销售齐套管理</div>
          <div>
            <el-button type="primary" :icon="Plus" @click="openAdd">新增齐套单</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="kittingId" size="default">
        <el-table-column label="齐套单号" prop="kittingNo" width="170" fixed />
        <el-table-column label="销售订单号" prop="soNo" width="150" show-overflow-tooltip />
        <el-table-column label="客户" prop="customerName" min-width="150" show-overflow-tooltip />
        <el-table-column label="机型" prop="kitModel" width="120" show-overflow-tooltip />
        <el-table-column label="套数" prop="kitQty" width="80" align="right" />
        <el-table-column label="明细行数" prop="totalLines" width="90" align="right" />
        <el-table-column label="需求总量" prop="totalRequiredQty" width="100" align="right" />
        <el-table-column label="可用库存" prop="totalAvailableQty" width="100" align="right" />
        <el-table-column label="缺口量" width="100" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.totalShortageQty > 0) ? '#f56c6c' : '', fontWeight: (row.totalShortageQty > 0) ? '600' : 'normal' }">
              {{ row.totalShortageQty || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" v-if="row.status === 0" @click="handleCheck(row)">齐套检查</el-button>
            <el-button link type="success" v-if="row.status === 2 && !row.stockOutId" @click="handleStockOut(row)">生成出库</el-button>
            <el-button link type="primary" v-if="[1,2].includes(row.status) && !row.requisitionId" @click="handleRequisition(row)">生成请购</el-button>
            <el-button link type="warning" v-if="[0,1].includes(row.status)" @click="handleCancel(row)">取消</el-button>
            <el-button link type="danger" v-if="[0,5].includes(row.status)" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="q.pageNum"
        v-model:page-size="q.pageSize"
        :total="list.data.total || 0"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 新增/编辑 对话框 -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增齐套单' : '修改齐套单'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="齐套单号">
              <el-input v-model="editDlg.form.kittingNo" placeholder="自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售订单" prop="soId">
              <el-select v-model="editDlg.form.soId" filterable placeholder="选择销售订单" style="width:100%" @change="onSoChange">
                <el-option v-for="o in soOptions" :key="o.soId" :label="`${o.soNo} - ${o.customerName || ''}`" :value="o.soId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="齐套BOM" prop="bomId">
              <el-select v-model="editDlg.form.bomId" filterable placeholder="选择销售BOM" style="width:100%" @change="onBomChange">
                <el-option v-for="b in bomOptions" :key="b.bomId" :label="`${b.bomCode} - ${b.bomName}`" :value="b.bomId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="齐套数量" prop="kitQty">
              <el-input-number v-model="editDlg.form.kitQty" :min="1" :precision="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发货仓库">
              <el-select v-model="editDlg.form.warehouseId" placeholder="选择仓库" filterable clearable style="width:100%">
                <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="`${w.warehouseCode || ''} ${w.warehouseName || ''}`" :value="w.warehouseId">
                  <span style="float:left">{{ w.warehouseCode }} {{ w.warehouseName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ w.warehouseType || '' }} {{ w.address || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机型">
              <el-input v-model="editDlg.form.kitModel" placeholder="自动从BOM填充" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" placeholder="备注信息" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailDlg.show" title="齐套单详情" width="900px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="齐套单号">{{ detailDlg.data?.kittingNo }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{ detailDlg.data?.soNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detailDlg.data?.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="机型">{{ detailDlg.data?.kitModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="齐套数量">{{ detailDlg.data?.kitQty }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailDlg.data?.status)" size="small">{{ statusLabel(detailDlg.data?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="明细行数">{{ detailDlg.data?.totalLines || 0 }}</el-descriptions-item>
        <el-descriptions-item label="需求总量">{{ detailDlg.data?.totalRequiredQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="可用库存">{{ detailDlg.data?.totalAvailableQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="缺口总量">
          <span :style="{ color: (detailDlg.data?.totalShortageQty > 0) ? '#f56c6c' : '', fontWeight: '600' }">
            {{ detailDlg.data?.totalShortageQty || 0 }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="检查时间">{{ detailDlg.data?.checkTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detailDlg.data?.completeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="出库单ID">{{ detailDlg.data?.stockOutId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请购单ID">{{ detailDlg.data?.requisitionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailDlg.data?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">齐套明细</el-divider>
      <el-table :data="detailDlg.lines" v-loading="detailDlg.loadingLines" stripe border size="small">
        <el-table-column label="行号" prop="lineNo" width="60" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="140" show-overflow-tooltip />
        <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
        <el-table-column label="规格" prop="specification" min-width="100" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="60" align="center" />
        <el-table-column label="需求量" prop="reqQty" width="90" align="right" />
        <el-table-column label="可用库存" prop="availableQty" width="90" align="right" />
        <el-table-column label="缺口量" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.shortageQty > 0) ? '#f56c6c' : '', fontWeight: '600' }">{{ row.shortageQty }}</span>
          </template>
        </el-table-column>
        <el-table-column label="建议批次" prop="batchNo" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isShortage ? 'danger' : 'success'" size="small">{{ row.isShortage ? '缺料' : '齐套' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { salKittingApi, salOrderApi, bomApi, warehouseApi } from '@/api'
import type { SalKitting, SalKittingLine } from '@/types/models'

// ========== 状态枚举 ==========
const statusOptions = [
  { value: 0, label: '待检查' },
  { value: 1, label: '部分齐套' },
  { value: 2, label: '齐套完成' },
  { value: 3, label: '已生成出库' },
  { value: 4, label: '已生成请购' },
  { value: 5, label: '已取消' }
]
function statusLabel(v?: number) { return statusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function statusTagType(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'info', 1: 'warning', 2: 'success', 3: '', 4: 'warning', 5: 'danger'
  }
  return v !== undefined ? (map[v] || '') : ''
}

// ========== 列表查询 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  kittingNo: '', soNo: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as SalKitting[], total: 0 } })
const warehouseOptions = ref<any[]>([])

async function loadList() {
  list.loading = true
  try {
    const res = await salKittingApi.list({ ...q } as any)
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally { list.loading = false }
}
function resetQ() {
  Object.assign(q, { kittingNo: '', soNo: '', status: undefined, pageNum: 1 })
  loadList()
}

// ========== 下拉数据 ==========
const soOptions = ref<any[]>([])
const bomOptions = ref<any[]>([])
async function loadOptions() {
  try { soOptions.value = (await salOrderApi.options()) || [] } catch { soOptions.value = [] }
  try {
    const res = await bomApi.list({ pageNum: 1, pageSize: 200, bomType: 3 } as any)
    bomOptions.value = (res as any)?.rows || []
  } catch { bomOptions.value = [] }
  try {
    const resp = (await warehouseApi.options()) as any
    warehouseOptions.value = resp?.data || resp || []
  } catch { warehouseOptions.value = [] }
}

function onSoChange(soId: number) {
  const so = soOptions.value.find(o => o.soId === soId)
  if (so) {
    editDlg.form.soNo = so.soNo
    editDlg.form.customerId = so.customerId
    editDlg.form.customerName = so.customerName
  }
}
function onBomChange(bomId: number) {
  const bom = bomOptions.value.find(b => b.bomId === bomId)
  if (bom) {
    editDlg.form.kitModel = bom.kitModel
    editDlg.form.productMaterialId = bom.productMaterialId
    editDlg.form.productMaterialCode = bom.productMaterialCode
    editDlg.form.productMaterialName = bom.productMaterialName
  }
}

// ========== 新增/编辑 ==========
const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<SalKitting> })
const editRules = {
  soId: [{ required: true, message: '请选择销售订单', trigger: 'change' }],
  bomId: [{ required: true, message: '请选择齐套BOM', trigger: 'change' }],
  kitQty: [{ required: true, message: '请输入齐套数量', trigger: 'blur' }]
}
async function openAdd() {
  editDlg.isNew = true
  editDlg.form = { kitQty: 1 }
  editDlg.show = true
  try {
    const code = await salKittingApi.nextCode()
    editDlg.form.kittingNo = code
  } catch { /* 忽略 */ }
}
async function openEdit(row: SalKitting) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await salKittingApi.get(row.kittingId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await salKittingApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await salKittingApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

// ========== 详情 ==========
const detailDlg = reactive({ show: false, data: null as SalKitting | null, lines: [] as SalKittingLine[], loadingLines: false })
async function openDetail(row: SalKitting) {
  detailDlg.data = null
  detailDlg.lines = []
  detailDlg.show = true
  try {
    detailDlg.data = await salKittingApi.get(row.kittingId!)
  } catch (e: any) { ElMessage.error(e.message || '加载详情失败') }
  detailDlg.loadingLines = true
  try {
    detailDlg.lines = (await salKittingApi.lines(row.kittingId!)) || []
  } catch { detailDlg.lines = [] }
  finally { detailDlg.loadingLines = false }
}

// ========== 核心动作 ==========
async function handleCheck(row: SalKitting) {
  await ElMessageBox.confirm('确认执行齐套检查？系统将展开BOM并检查库存。', '提示', { type: 'warning' })
  await salKittingApi.check(row.kittingId!); ElMessage.success('齐套检查完成'); loadList()
}
async function handleStockOut(row: SalKitting) {
  await ElMessageBox.confirm('确认生成出库单？将创建WMS销售出库单。', '提示', { type: 'warning' })
  const stockOutId = await salKittingApi.stockOut(row.kittingId!)
  ElMessage.success(`出库单已生成 ID=${stockOutId}`)
  loadList()
}
async function handleRequisition(row: SalKitting) {
  await ElMessageBox.confirm('确认生成请购单？缺料明细将转为采购申请。', '提示', { type: 'warning' })
  const reqId = await salKittingApi.requisition(row.kittingId!)
  ElMessage.success(`请购单已生成 ID=${reqId}`)
  loadList()
}
async function handleCancel(row: SalKitting) {
  await ElMessageBox.confirm('确认取消该齐套单？', '提示', { type: 'warning' })
  await salKittingApi.cancel(row.kittingId!); ElMessage.success('已取消'); loadList()
}
async function handleRemove(row: SalKitting) {
  await ElMessageBox.confirm('确认删除该齐套单？', '提示', { type: 'warning' })
  await salKittingApi.remove(row.kittingId!); ElMessage.success('删除成功'); loadList()
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.sal-kitting-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
