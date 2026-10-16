<template>
  <div class="asm-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="配料单号">
          <el-input v-model="q.kittingNo" placeholder="配料单号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="工单号">
          <el-input v-model="q.workOrderNo" placeholder="工单号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="q.materialCode" placeholder="物料编码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="配料类型">
          <el-select v-model="q.kittingType" placeholder="全部" clearable style="width:130px">
            <el-option v-for="s in kittingTypes" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
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
          <div class="title"><span class="dot" />配料单管理</div>
          <div>
            <el-button type="primary" :icon="Plus" @click="openAdd">新增配料单</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="kittingId" size="default">
        <el-table-column label="配料单号" prop="kittingNo" width="170" fixed />
        <el-table-column label="工单号" prop="workOrderNo" width="150" show-overflow-tooltip />
        <el-table-column label="成品料号" prop="materialCode" width="150" show-overflow-tooltip />
        <el-table-column label="配料类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="kittingTypeTag(row.kittingType)" size="small">{{ kittingTypeLabel(row.kittingType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="明细行数" prop="totalLines" width="90" align="right" />
        <el-table-column label="需求总量" prop="totalRequiredQty" width="100" align="right" />
        <el-table-column label="已发料" prop="totalIssuedQty" width="90" align="right" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作员" prop="operatorName" width="110" />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" v-if="row.status === 0" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" v-if="row.status === 0" @click="handleStart(row)">开始配料</el-button>
            <el-button link type="success" v-if="row.status === 1" @click="handleFinish(row)">完成配料</el-button>
            <el-button link type="success" v-if="row.status === 2" @click="handleIssue(row)">发料</el-button>
            <el-button link type="warning" v-if="row.status === 0" @click="handleCancel(row)">取消</el-button>
            <el-button link type="danger" v-if="row.status === 0" @click="handleRemove(row)">删除</el-button>
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
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增配料单' : '修改配料单'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单号" prop="workOrderId">
              <el-input v-model="editDlg.form.workOrderId" placeholder="输入工单号ID" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="配料单号">
              <el-input v-model="editDlg.form.kittingNo" placeholder="自动生成" :disabled="!editDlg.isNew" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="配料类型" prop="kittingType">
              <el-select v-model="editDlg.form.kittingType" placeholder="请选择配料类型" style="width:100%">
                <el-option v-for="s in kittingTypes" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库">
              <el-select v-model="editDlg.form.warehouseId" placeholder="选择仓库" filterable style="width:100%">
                <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="`${w.warehouseCode || ''} ${w.warehouseName || ''}`" :value="w.warehouseId">
                  <span style="float:left">{{ w.warehouseCode }} {{ w.warehouseName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ w.warehouseType || '' }} {{ w.address || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作员">
              <el-input v-model="editDlg.form.operatorName" placeholder="操作员姓名" />
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
    <el-dialog v-model="detailDlg.show" title="配料单详情" width="820px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="配料单号">{{ detailDlg.data?.kittingNo }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ detailDlg.data?.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="成品料号">{{ detailDlg.data?.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="成品名称">{{ detailDlg.data?.materialName }}</el-descriptions-item>
        <el-descriptions-item label="配料类型">
          <el-tag :type="kittingTypeTag(detailDlg.data?.kittingType)" size="small">{{ kittingTypeLabel(detailDlg.data?.kittingType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailDlg.data?.status)" size="small">{{ statusLabel(detailDlg.data?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="明细行数">{{ detailDlg.data?.totalLines || 0 }}</el-descriptions-item>
        <el-descriptions-item label="需求总量">{{ detailDlg.data?.totalRequiredQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="已发料">{{ detailDlg.data?.totalIssuedQty || 0 }}</el-descriptions-item>
        <el-descriptions-item label="操作员">{{ detailDlg.data?.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际开始">{{ detailDlg.data?.actualStartTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际结束">{{ detailDlg.data?.actualEndTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailDlg.data?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detailDlg.data?.updateTime }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">配料明细</el-divider>
      <el-table :data="detailDlg.lines" v-loading="detailDlg.loadingLines" stripe border size="small">
        <el-table-column label="行号" prop="lineNo" width="80" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="150" show-overflow-tooltip />
        <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
        <el-table-column label="规格" prop="specification" min-width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="需求数量" prop="reqQty" width="90" align="right" />
        <el-table-column label="已发数量" prop="issuedQty" width="90" align="right" />
        <el-table-column label="批次号" prop="batchNo" width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="kittingLineStatusTag(row.lineStatus)" size="small">{{ kittingLineStatusLabel(row.lineStatus) }}</el-tag>
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
import { asmKittingApi, warehouseApi } from '@/api'
import type { AsmKitting, AsmKittingLine } from '@/types/models'

// ========== 枚举选项 ==========
const kittingTypes = [
  { value: 'FULL', label: '全配料' },
  { value: 'PARTIAL', label: '补配料' },
  { value: 'REWORK', label: '返工配料' }
]
function kittingTypeLabel(v?: string) { return kittingTypes.find(s => s.value === v)?.label || v || '-' }
function kittingTypeTag(v?: string): '' | 'primary' | 'warning' | 'danger' {
  const map: Record<string, '' | 'primary' | 'warning' | 'danger'> = { FULL: '', PARTIAL: 'warning', REWORK: 'danger' }
  return v ? (map[v] || '') : ''
}

const statusOptions = [
  { value: 0, label: '待配料' },
  { value: 1, label: '配料中' },
  { value: 2, label: '已配料待发' },
  { value: 3, label: '已发料' },
  { value: 4, label: '部分发料' },
  { value: 5, label: '已退料' },
  { value: 6, label: '已取消' }
]
function statusLabel(v?: number) { return statusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function statusTagType(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'info', 1: 'warning', 2: 'primary' as any, 3: 'success', 4: 'warning', 5: 'info', 6: 'danger'
  }
  return v !== undefined ? (map[v] || '') : ''
}

const lineStatusOptions = [
  { value: 0, label: '待拣' },
  { value: 1, label: '拣货中' },
  { value: 2, label: '已拣' },
  { value: 3, label: '已发' },
  { value: 4, label: '已退' },
  { value: 5, label: '缺料' }
]
function kittingLineStatusLabel(v?: number) { return lineStatusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function kittingLineStatusTag(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = { 0: 'info', 1: 'warning', 2: 'primary' as any, 3: 'success', 4: 'info', 5: 'danger' }
  return v !== undefined ? (map[v] || '') : ''
}

// ========== 列表查询 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  kittingNo: '', workOrderNo: '', materialCode: '',
  kittingType: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as AsmKitting[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await asmKittingApi.list({ ...q } as any)
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally { list.loading = false }
}
function resetQ() {
  Object.assign(q, { kittingNo: '', workOrderNo: '', materialCode: '', kittingType: '', status: undefined, pageNum: 1 })
  loadList()
}

// ========== 新增/编辑 ==========
const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<AsmKitting> })
const editRules = {
  workOrderId: [{ required: true, message: '请输入工单号ID', trigger: 'blur' }],
  kittingType: [{ required: true, message: '请选择配料类型', trigger: 'change' }]
}
async function openAdd() {
  editDlg.isNew = true
  editDlg.form = { kittingType: 'FULL' }
  editDlg.show = true
  try {
    const code = await asmKittingApi.nextCode()
    editDlg.form.kittingNo = code
  } catch { /* 忽略 */ }
}
async function openEdit(row: AsmKitting) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await asmKittingApi.get(row.kittingId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await asmKittingApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await asmKittingApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

// ========== 详情 ==========
const detailDlg = reactive({ show: false, data: null as AsmKitting | null, lines: [] as AsmKittingLine[], loadingLines: false })
async function openDetail(row: AsmKitting) {
  detailDlg.data = null
  detailDlg.lines = []
  detailDlg.show = true
  try {
    detailDlg.data = await asmKittingApi.get(row.kittingId!)
  } catch (e: any) { ElMessage.error(e.message || '加载详情失败') }
  // 加载明细
  detailDlg.loadingLines = true
  try {
    detailDlg.lines = (await asmKittingApi.lines(row.kittingId!)) || []
  } catch { detailDlg.lines = [] }
  finally { detailDlg.loadingLines = false }
}

// ========== 状态流转 ==========
async function handleStart(row: AsmKitting) {
  await ElMessageBox.confirm('确认开始配料？', '提示', { type: 'warning' })
  await asmKittingApi.start(row.kittingId!); ElMessage.success('已开始配料'); loadList()
}
async function handleFinish(row: AsmKitting) {
  await ElMessageBox.confirm('确认完成配料？', '提示', { type: 'warning' })
  await asmKittingApi.finish(row.kittingId!); ElMessage.success('配料完成'); loadList()
}
async function handleIssue(row: AsmKitting) {
  await ElMessageBox.confirm('确认发料？发料后将扣减库存。', '提示', { type: 'warning' })
  await asmKittingApi.issue(row.kittingId!); ElMessage.success('已发料'); loadList()
}
async function handleCancel(row: AsmKitting) {
  await ElMessageBox.confirm('确认取消该配料单？', '提示', { type: 'warning' })
  await asmKittingApi.cancel(row.kittingId!); ElMessage.success('已取消'); loadList()
}
async function handleRemove(row: AsmKitting) {
  await ElMessageBox.confirm('确认删除该配料单？', '提示', { type: 'warning' })
  await asmKittingApi.remove(row.kittingId!); ElMessage.success('删除成功'); loadList()
}

// ========== 选项加载 ==========
const warehouseOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const w = await warehouseApi.options() as any
    warehouseOptions.value = w?.data || w || []
  } catch { /* ignore */ }
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.asm-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
