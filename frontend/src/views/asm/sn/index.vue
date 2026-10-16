<template>
  <div class="asm-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="SN码">
          <el-input v-model="q.snCode" placeholder="SN码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="q.snType" placeholder="全部" clearable style="width:120px">
            <el-option v-for="s in snTypes" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="层级">
          <el-select v-model="q.snLevel" placeholder="全部" clearable style="width:100px">
            <el-option :value="1" label="1-原材料" />
            <el-option :value="2" label="2-半成品" />
            <el-option :value="3" label="3-成品" />
          </el-select>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-select v-model="q.currentStatus" placeholder="全部" clearable style="width:120px">
            <el-option v-for="s in currentStatusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单号">
          <el-input v-model="q.workOrderNo" placeholder="工单号" clearable @keyup.enter="loadList" />
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
          <div class="title"><span class="dot" />SN码管理</div>
          <div>
            <el-button type="primary" :icon="Plus" @click="openAdd">新增SN码</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="snId" size="default">
        <el-table-column label="SN码" prop="snCode" width="180" fixed show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="snTypeTag(row.snType)" size="small">{{ snTypeLabel(row.snType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="层级" prop="snLevel" width="80" align="center">
          <template #default="{ row }">{{ row.snLevel === 1 ? '原材料' : row.snLevel === 2 ? '半成品' : row.snLevel === 3 ? '成品' : '-' }}</template>
        </el-table-column>
        <el-table-column label="料号" prop="materialCode" width="150" show-overflow-tooltip />
        <el-table-column label="名称" prop="materialName" min-width="160" show-overflow-tooltip />
        <el-table-column label="批次号" prop="batchNo" width="130" show-overflow-tooltip />
        <el-table-column label="工单" prop="workOrderNo" width="130" show-overflow-tooltip />
        <el-table-column label="当前工序" prop="currentProcess" width="110" show-overflow-tooltip />
        <el-table-column label="当前状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="currentStatusTag(row.currentStatus)" size="small">{{ currentStatusLabel(row.currentStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="数量" prop="qty" width="80" align="right" />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handleTrace(row)">追溯</el-button>
            <el-button link type="danger" @click="handleRemove(row)">删除</el-button>
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
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增SN码' : '修改SN码'" width="760px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="SN码" prop="snCode">
              <el-input v-model="editDlg.form.snCode" placeholder="请输入SN码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="snType">
              <el-select v-model="editDlg.form.snType" placeholder="请选择类型" style="width:100%">
                <el-option v-for="s in snTypes" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="层级" prop="snLevel">
              <el-select v-model="editDlg.form.snLevel" placeholder="请选择层级" style="width:100%">
                <el-option :value="1" label="1-原材料" />
                <el-option :value="2" label="2-半成品" />
                <el-option :value="3" label="3-成品" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料">
              <el-select v-model="editDlg.form.materialId" placeholder="输入编码/名称搜索" filterable style="width:100%" @change="onMaterialChange">
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                  <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料编码">
              <el-input v-model="editDlg.form.materialCode" placeholder="物料编码（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料名称">
              <el-input v-model="editDlg.form.materialName" placeholder="物料名称（可选）" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="数量">
              <el-input-number v-model="editDlg.form.qty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单位">
              <el-input v-model="editDlg.form.unit" placeholder="PCS" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批次号">
              <el-input v-model="editDlg.form.batchNo" placeholder="批次号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="editDlg.form.supplierName" placeholder="供应商名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产日期">
              <el-date-picker v-model="editDlg.form.productionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期">
              <el-date-picker v-model="editDlg.form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
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
    <el-dialog v-model="detailDlg.show" title="SN码详情" width="640px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="SN码">{{ detailDlg.data?.snCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="snTypeTag(detailDlg.data?.snType)" size="small">{{ snTypeLabel(detailDlg.data?.snType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="层级">{{ detailDlg.data?.snLevel === 1 ? '原材料' : detailDlg.data?.snLevel === 2 ? '半成品' : detailDlg.data?.snLevel === 3 ? '成品' : '-' }}</el-descriptions-item>
        <el-descriptions-item label="料号">{{ detailDlg.data?.materialCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailDlg.data?.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ detailDlg.data?.specification || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detailDlg.data?.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ detailDlg.data?.qty || 0 }} {{ detailDlg.data?.unit || '' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detailDlg.data?.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工单">{{ detailDlg.data?.workOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前工序">{{ detailDlg.data?.currentProcess || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="currentStatusTag(detailDlg.data?.currentStatus)" size="small">{{ currentStatusLabel(detailDlg.data?.currentStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生产日期">{{ detailDlg.data?.productionDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ detailDlg.data?.expiryDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="首次记录">{{ detailDlg.data?.firstSeenTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后更新">{{ detailDlg.data?.lastSeenTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ detailDlg.data?.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { asmSnApi, materialApi } from '@/api'
import type { AsmSn } from '@/types/models'

const route = useRoute()
const router = useRouter()

// ========== 枚举选项 ==========
const snTypes = [
  { value: 'RAW', label: '原材料' },
  { value: 'WIP', label: '半成品' },
  { value: 'FG', label: '成品' },
  { value: 'REEL', label: '料盘' }
]
function snTypeLabel(v?: string) { return snTypes.find(s => s.value === v)?.label || v || '-' }
function snTypeTag(v?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    RAW: 'info', WIP: 'warning', FG: 'success', REEL: 'primary' as any
  }
  return v ? (map[v] || '') : ''
}

const currentStatusOptions = [
  { value: 0, label: '待生产' },
  { value: 1, label: '生产中' },
  { value: 2, label: '在制' },
  { value: 3, label: '已完工' },
  { value: 4, label: '不良' },
  { value: 5, label: '报废' },
  { value: 6, label: '已出货' }
]
function currentStatusLabel(v?: number) { return currentStatusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function currentStatusTag(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'info', 1: 'primary' as any, 2: 'warning', 3: 'success', 4: 'danger', 5: 'danger', 6: 'info'
  }
  return v !== undefined ? (map[v] || '') : ''
}

// ========== 列表查询 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  snCode: '', snType: '', snLevel: undefined as number | undefined,
  currentStatus: undefined as number | undefined,
  workOrderNo: ''
})
const list = reactive({ loading: false, data: { rows: [] as AsmSn[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const params: any = { ...q }
    if (params.workOrderNo) {
      params.workOrderId = undefined
    }
    const res = await asmSnApi.list(params)
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally { list.loading = false }
}
function resetQ() {
  Object.assign(q, { snCode: '', snType: '', snLevel: undefined, currentStatus: undefined, workOrderNo: '', pageNum: 1 })
  loadList()
}

// ========== 新增/编辑 ==========
const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<AsmSn> })
const editRules = {
  snCode: [{ required: true, message: '请输入SN码', trigger: 'blur' }],
  snType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  snLevel: [{ required: true, message: '请选择层级', trigger: 'change' }]
}
function openAdd() {
  editDlg.isNew = true
  editDlg.form = { qty: 1, unit: 'PCS' }
  editDlg.show = true
}
async function openEdit(row: AsmSn) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await asmSnApi.get(row.snId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await asmSnApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await asmSnApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

// ========== 详情 ==========
const detailDlg = reactive({ show: false, data: null as AsmSn | null })
async function openDetail(row: AsmSn) {
  try {
    detailDlg.data = await asmSnApi.get(row.snId!)
    detailDlg.show = true
  } catch (e: any) { ElMessage.error(e.message || '加载详情失败') }
}

// ========== 追溯跳转 ==========
function handleTrace(row: AsmSn) {
  router.push({ path: '/asm/trace', query: { snCode: row.snCode } })
}

// ========== 删除 ==========
async function handleRemove(row: AsmSn) {
  await ElMessageBox.confirm('确认删除该SN码？', '提示', { type: 'warning' })
  await asmSnApi.remove(row.snId!); ElMessage.success('删除成功'); loadList()
}

// ========== 选项加载 ==========
const materialOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const m = await materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    materialOptions.value = m?.rows || []
  } catch { /* ignore */ }
}
function onMaterialChange(val: any) {
  const m = materialOptions.value.find(x => x.materialId === val)
  if (m) {
    editDlg.form.materialCode = m.materialCode
    editDlg.form.materialName = m.materialName
    editDlg.form.specification = m.specification
  }
}

onMounted(() => {
  // 支持从URL query.snCode 自动填充
  if (route.query.snCode) {
    q.snCode = route.query.snCode as string
  }
  loadList()
  loadOptions()
})
</script>

<style scoped>
.asm-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #67c23a; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
