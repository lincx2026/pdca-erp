<template>
  <div class="pm-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单号">
          <el-input v-model="q.orderNo" placeholder="WO-xxxx" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="q.materialCode" placeholder="物料编码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="q.materialName" placeholder="物料名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="q.sourceType" placeholder="全部" clearable style="width:130px">
            <el-option v-for="s in sourceTypes" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作中心">
          <el-input v-model="q.workCenter" placeholder="工作中心" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="销售订单">
          <el-input v-model="q.saleOrderNo" placeholder="销售订单号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:120px">
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
          <div class="title"><span class="dot" />生产工单</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:workorder:add'" @click="openAdd">新增工单</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="workOrderId" size="default">
        <el-table-column label="工单号" prop="orderNo" width="150" fixed />
        <el-table-column label="来源" width="90" align="center">
          <template #default="{ row }">{{ sourceLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="150" show-overflow-tooltip />
        <el-table-column label="物料名称" prop="materialName" min-width="180" show-overflow-tooltip />
        <el-table-column label="规格" prop="specification" min-width="140" show-overflow-tooltip />
        <el-table-column label="订单数量" prop="orderQty" width="100" align="right" />
        <el-table-column label="已完成" prop="finishedQty" width="90" align="right" />
        <el-table-column label="报废" prop="scrappedQty" width="80" align="right" />
        <el-table-column label="工作中心" prop="workCenter" width="110" />
        <el-table-column label="计划开始" prop="planStartDate" width="110" />
        <el-table-column label="计划结束" prop="planEndDate" width="110" />
        <el-table-column label="销售订单" prop="saleOrderNo" width="130" show-overflow-tooltip />
        <el-table-column label="优先级" prop="priority" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.priority === 1" type="danger" size="small">紧急</el-tag>
            <el-tag v-else-if="row.priority === 2" type="warning" size="small">高</el-tag>
            <el-tag v-else size="small">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-if="row.status===0" v-hasPermi="'pm:workorder:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" v-if="row.status===0" v-hasPermi="'pm:workorder:audit'" @click="handleRelease(row)">下发</el-button>
            <el-button link type="success" v-if="row.status===10 || row.status===20" v-hasPermi="'pm:workorder:audit'" @click="handleFinish(row)">完工</el-button>
            <el-button link type="info" v-if="row.status===40" v-hasPermi="'pm:workorder:audit'" @click="handleClose(row)">关闭</el-button>
            <el-button link type="warning" v-if="row.status===0" v-hasPermi="'pm:workorder:audit'" @click="handleCancel(row)">取消</el-button>
            <el-button link type="danger" v-if="row.status===0" v-hasPermi="'pm:workorder:remove'" @click="handleRemove(row)">删除</el-button>
            <el-button link type="primary" @click="handlePrint(row)">打印</el-button>
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
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增生产工单' : '修改生产工单'" width="760px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="editDlg.form.sourceType" placeholder="选择来源类型" style="width:100%">
                <el-option v-for="s in sourceTypes" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料" prop="materialId">
              <el-select v-model="editDlg.form.materialId" placeholder="选择物料" filterable style="width:100%" @change="onMaterialChange">
                <el-option
                  v-for="m in materialOptions"
                  :key="m.materialId"
                  :label="`${m.materialCode} - ${m.materialName}`"
                  :value="m.materialId"
                >
                  <div style="display:flex;justify-content:space-between;width:100%">
                    <span>{{ m.materialCode }} - {{ m.materialName }}</span>
                    <span style="color:#999;font-size:12px">{{ m.specification }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单数量" prop="orderQty">
              <el-input-number v-model="editDlg.form.orderQty" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位">
              <el-input v-model="editDlg.form.unit" placeholder="如 PCS" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="BOM">
              <el-select v-model="editDlg.form.bomId" placeholder="选择BOM" filterable clearable style="width:100%" @change="onBomChange">
                <el-option
                  v-for="b in bomOptions"
                  :key="b.bomId"
                  :label="`${b.bomCode} - ${b.bomName}`"
                  :value="b.bomId"
                >
                  <div style="display:flex;justify-content:space-between;width:100%">
                    <span>{{ b.bomCode }} - {{ b.bomName }}</span>
                    <span style="color:#999;font-size:12px">v{{ b.version }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工艺路线">
              <el-select v-model="editDlg.form.routeId" placeholder="选择工艺路线" filterable clearable style="width:100%" @change="onRouteChange">
                <el-option
                  v-for="r in routeOptions"
                  :key="r.routeId"
                  :label="`${r.routeCode} - ${r.routeName}`"
                  :value="r.routeId!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="editDlg.form.planStartDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="editDlg.form.planEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-radio-group v-model="editDlg.form.priority">
                <el-radio :value="0">普通</el-radio>
                <el-radio :value="2">高</el-radio>
                <el-radio :value="1">紧急</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作中心">
              <el-input v-model="editDlg.form.workCenter" placeholder="如 SMT-Line1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售订单">
              <el-input v-model="editDlg.form.saleOrderNo" placeholder="销售订单号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="editDlg.form.customerName" placeholder="客户名称" />
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
    <el-dialog v-model="detailDlg.show" title="工单详情" width="680px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="工单号">{{ detailDlg.data?.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ sourceLabel(detailDlg.data?.sourceType) }}</el-descriptions-item>
        <el-descriptions-item label="物料编码">{{ detailDlg.data?.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ detailDlg.data?.materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格" :span="2">{{ detailDlg.data?.specification }}</el-descriptions-item>
        <el-descriptions-item label="订单数量">{{ detailDlg.data?.orderQty }} {{ detailDlg.data?.unit }}</el-descriptions-item>
        <el-descriptions-item label="已完成">{{ detailDlg.data?.finishedQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="报废数量">{{ detailDlg.data?.scrappedQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="工作中心">{{ detailDlg.data?.workCenter }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ detailDlg.data?.planStartDate }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ detailDlg.data?.planEndDate }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ detailDlg.data?.actualStartDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ detailDlg.data?.actualEndDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{ detailDlg.data?.saleOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detailDlg.data?.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailDlg.data?.status)" size="small">{{ statusLabel(detailDlg.data?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="驳回原因">{{ detailDlg.data?.rejectReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailDlg.data?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailDlg.data?.updateTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 打印预览 -->
    <el-dialog v-model="printVisible" title="打印预览 - 生产工单随工单" width="800px" :close-on-click-modal="false" destroy-on-close>
      <WorkOrderPrint :order="printOrder" :visible="printVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { workOrderApi, materialApi, bomApi, processRouteApi } from '@/api'
import type { PmmWorkOrder, PdmMaterial, PdmBom, PdmProcessRoute } from '@/types/models'
import WorkOrderPrint from '@/views/print/WorkOrderPrint.vue'

// ========== 下拉选项 ==========
const materialOptions = ref<PdmMaterial[]>([])
const bomOptions = ref<PdmBom[]>([])
const routeOptions = ref<PdmProcessRoute[]>([])

async function loadOptions() {
  try {
    const [matRes, bomRes, routeRes] = await Promise.all([
      materialApi.list({ pageNum: 1, pageSize: 1000 }),
      bomApi.list({ pageNum: 1, pageSize: 1000 }),
      processRouteApi.list({ pageNum: 1, pageSize: 1000 })
    ])
    materialOptions.value = ((matRes as any)?.rows || (matRes as any)?.data?.rows || []) as PdmMaterial[]
    bomOptions.value = ((bomRes as any)?.rows || (bomRes as any)?.data?.rows || []) as PdmBom[]
    routeOptions.value = ((routeRes as any)?.rows || (routeRes as any)?.data?.rows || []) as PdmProcessRoute[]
  } catch (e: any) {
    // 忽略选项加载失败，不阻塞主列表
  }
}

// ========== 打印预览 ==========
const printVisible = ref(false)
const printOrder = ref<PmmWorkOrder | null>(null)
function handlePrint(row: PmmWorkOrder) {
  printOrder.value = row
  printVisible.value = true
}

const sourceTypes = [
  { value: 'MTO', label: '按单生产' },
  { value: 'MTS', label: '备货生产' },
  { value: 'REWORK', label: '返工' },
  { value: 'SAMPLE', label: '打样' }
]
function sourceLabel(v?: string) { return sourceTypes.find(s => s.value === v)?.label || v || '-' }

const statusOptions = [
  { value: 0, label: '草稿' },
  { value: 10, label: '已下发' },
  { value: 20, label: '生产中' },
  { value: 30, label: '暂停' },
  { value: 40, label: '完工' },
  { value: 50, label: '关闭' },
  { value: 90, label: '取消' }
]
function statusLabel(v?: number) { return statusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function statusTagType(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'info', 10: 'warning', 20: 'primary' as any, 30: 'warning', 40: 'success', 50: 'info', 90: 'danger'
  }
  return v !== undefined ? (map[v] || '') : ''
}

const q = reactive({
  pageNum: 1, pageSize: 20,
  orderNo: '', materialCode: '', materialName: '',
  sourceType: '', workCenter: '', saleOrderNo: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as PmmWorkOrder[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await workOrderApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally { list.loading = false }
}
function resetQ() {
  Object.assign(q, { orderNo: '', materialCode: '', materialName: '', sourceType: '', workCenter: '', saleOrderNo: '', status: undefined, pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<PmmWorkOrder> })
const editRules = {
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  materialId: [{ required: true, message: '物料ID不能为空', trigger: 'blur' }],
  orderQty: [{ required: true, message: '订单数量不能为空', trigger: 'blur' }]
}

// 物料选择变更：自动回填 unit / 物料信息
function onMaterialChange(materialId?: number) {
  if (!materialId) return
  const m = materialOptions.value.find(x => x.materialId === materialId)
  if (m) {
    if (m.unit && !editDlg.form.unit) editDlg.form.unit = m.unit
    if (m.materialCode) editDlg.form.materialCode = m.materialCode
    if (m.materialName) editDlg.form.materialName = m.materialName
    if (m.specification) editDlg.form.specification = m.specification
  }
}

// BOM 选择变更：暂无额外字段回填
function onBomChange(_bomId?: number) { /* 预留 */ }

// 工艺路线选择变更：暂无额外字段回填
function onRouteChange(_routeId?: number) { /* 预留 */ }

function openAdd() {
  editDlg.isNew = true
  editDlg.form = { sourceType: 'MTO', orderQty: 1, unit: 'PCS', priority: 0 }
  editDlg.show = true
}
async function openEdit(row: PmmWorkOrder) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await workOrderApi.get(row.workOrderId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await workOrderApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await workOrderApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

const detailDlg = reactive({ show: false, data: null as PmmWorkOrder | null })
async function openDetail(row: PmmWorkOrder) {
  try {
    detailDlg.data = await workOrderApi.get(row.workOrderId!)
    detailDlg.show = true
  } catch (e: any) { ElMessage.error(e.message || '加载详情失败') }
}

async function handleRelease(row: PmmWorkOrder) {
  await ElMessageBox.confirm('确认下发该工单？下发后不可修改。', '提示', { type: 'warning' })
  await workOrderApi.release(row.workOrderId!); ElMessage.success('已下发'); loadList()
}
async function handleFinish(row: PmmWorkOrder) {
  await ElMessageBox.confirm('确认完工该工单？', '提示', { type: 'warning' })
  await workOrderApi.finish(row.workOrderId!); ElMessage.success('已完工'); loadList()
}
async function handleClose(row: PmmWorkOrder) {
  await ElMessageBox.confirm('确认关闭该工单？', '提示', { type: 'warning' })
  await workOrderApi.close(row.workOrderId!); ElMessage.success('已关闭'); loadList()
}
async function handleCancel(row: PmmWorkOrder) {
  await ElMessageBox.confirm('确认取消该工单？', '提示', { type: 'warning' })
  await workOrderApi.cancel(row.workOrderId!); ElMessage.success('已取消'); loadList()
}
async function handleRemove(row: PmmWorkOrder) {
  await ElMessageBox.confirm('确认删除该工单？', '提示', { type: 'warning' })
  await workOrderApi.remove(row.workOrderId!); ElMessage.success('删除成功'); loadList()
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.pm-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
