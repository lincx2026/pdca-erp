<template>
  <div class="asm-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="设备编码">
          <el-input v-model="q.equipmentCode" placeholder="设备编码" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="Feeder编号">
          <el-input v-model="q.feederNo" placeholder="Feeder编号" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input v-model="q.materialCode" placeholder="物料编码" clearable @keyup.enter="loadList" />
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
          <div class="title"><span class="dot" />Feeder上料记录</div>
          <div>
            <el-button type="primary" :icon="Plus" @click="openAdd">新增上料</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="loadId" size="default">
        <el-table-column label="设备编码" prop="equipmentCode" width="130" show-overflow-tooltip />
        <el-table-column label="Feeder编号" prop="feederNo" width="110" />
        <el-table-column label="料站位置" prop="slotNo" width="90" align="center">
          <template #default="{ row }">{{ row.slotNo ? `#${row.slotNo}` : '-' }}</template>
        </el-table-column>
        <el-table-column label="料号" prop="materialCode" width="150" show-overflow-tooltip />
        <el-table-column label="名称" prop="materialName" min-width="160" show-overflow-tooltip />
        <el-table-column label="料盘SN" prop="reelSn" width="150" show-overflow-tooltip />
        <el-table-column label="批次号" prop="batchNo" width="130" show-overflow-tooltip />
        <el-table-column label="料盘剩余" prop="reelQty" width="100" align="right">
          <template #default="{ row }">
            <el-tag v-if="row.reelQty != null && row.reelQty < 50" type="danger" size="small">{{ row.reelQty }}</el-tag>
            <span v-else>{{ row.reelQty ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工单" prop="workOrderNo" width="130" show-overflow-tooltip />
        <el-table-column label="上料时间" prop="loadTime" width="170" />
        <el-table-column label="下料时间" prop="unloadTime" width="170" />
        <el-table-column label="操作员" prop="operatorName" width="110" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" v-if="row.status === 0" @click="handleChange(row)">换料</el-button>
            <el-button link type="info" v-if="row.status === 0 || row.status === 1" @click="handleUnload(row)">下料</el-button>
            <el-button link type="danger" v-if="row.status !== 3" @click="handleScrap(row)">报废</el-button>
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
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增Feeder上料' : '修改Feeder上料'" width="760px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备编码" prop="equipmentCode">
              <el-input v-model="editDlg.form.equipmentCode" placeholder="如 SMT-A01" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Feeder编号" prop="feederNo">
              <el-input v-model="editDlg.form.feederNo" placeholder="如 F1-001" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="料站位置">
              <el-input-number v-model="editDlg.form.slotNo" :min="0" style="width:100%" placeholder="料站" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="物料" prop="materialId">
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
            <el-form-item label="料盘SN">
              <el-input v-model="editDlg.form.reelSn" placeholder="料盘SN" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="料盘数量">
              <el-input-number v-model="editDlg.form.reelQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="批次号">
              <el-input v-model="editDlg.form.batchNo" placeholder="批次号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="程序名">
              <el-input v-model="editDlg.form.programName" placeholder="程序名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上料时间">
              <el-date-picker v-model="editDlg.form.loadTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单">
              <el-select v-model="editDlg.form.workOrderId" placeholder="选择工单" filterable style="width:100%">
                <el-option v-for="o in workOrderOptions" :key="o.workOrderId" :label="`${o.orderNo || ''} ${o.materialName || ''}`" :value="o.workOrderId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单号">
              <el-input v-model="editDlg.form.workOrderNo" placeholder="工单号" />
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
    <el-dialog v-model="detailDlg.show" title="Feeder上料详情" width="620px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="设备编码">{{ detailDlg.data?.equipmentCode }}</el-descriptions-item>
        <el-descriptions-item label="Feeder编号">{{ detailDlg.data?.feederNo }}</el-descriptions-item>
        <el-descriptions-item label="料站位置">{{ detailDlg.data?.slotNo ? `#${detailDlg.data?.slotNo}` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="料号">{{ detailDlg.data?.materialCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailDlg.data?.materialName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="料盘SN">{{ detailDlg.data?.reelSn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="批次号">{{ detailDlg.data?.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="料盘剩余">{{ detailDlg.data?.reelQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="程序名">{{ detailDlg.data?.programName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工单">{{ detailDlg.data?.workOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="上料时间">{{ detailDlg.data?.loadTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下料时间">{{ detailDlg.data?.unloadTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作员">{{ detailDlg.data?.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailDlg.data?.status)" size="small">{{ statusLabel(detailDlg.data?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailDlg.data?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { asmFeederApi, materialApi, workOrderApi } from '@/api'
import type { AsmFeederLoad } from '@/types/models'

// ========== 枚举选项 ==========
const statusOptions = [
  { value: 0, label: '在料' },
  { value: 1, label: '已换料' },
  { value: 2, label: '已下料' },
  { value: 3, label: '报废' }
]
function statusLabel(v?: number) { return statusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function statusTagType(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'success', 1: 'warning', 2: 'info', 3: 'danger'
  }
  return v !== undefined ? (map[v] || '') : ''
}

// ========== 列表查询 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  equipmentCode: '', feederNo: '', materialCode: '',
  status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as AsmFeederLoad[], total: 0 } })

async function loadList() {
  list.loading = true
  try {
    const res = await asmFeederApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) {
    ElMessage.error(e.message || '查询失败')
  } finally { list.loading = false }
}
function resetQ() {
  Object.assign(q, { equipmentCode: '', feederNo: '', materialCode: '', status: undefined, pageNum: 1 })
  loadList()
}

// ========== 新增/编辑 ==========
const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<AsmFeederLoad> })
const editRules = {
  equipmentCode: [{ required: true, message: '请输入设备编码', trigger: 'blur' }],
  feederNo: [{ required: true, message: '请输入Feeder编号', trigger: 'blur' }],
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }]
}

// ========== 选项加载 ==========
const materialOptions = ref<any[]>([])
const workOrderOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const [m, o] = await Promise.all([
      materialApi.list({ pageNum: 1, pageSize: 1000 }) as any,
      workOrderApi.options() as any
    ])
    materialOptions.value = (m as any)?.rows || []
    workOrderOptions.value = (o as any)?.data || o || []
  } catch { /* ignore */ }
}
function onMaterialChange(val: any) {
  const m = materialOptions.value.find(x => x.materialId === val)
  if (m) {
    editDlg.form.materialCode = m.materialCode
    editDlg.form.materialName = m.materialName
  }
}
function openAdd() {
  editDlg.isNew = true
  editDlg.form = {
    loadTime: formatNow()
  }
  editDlg.show = true
}
async function openEdit(row: AsmFeederLoad) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await asmFeederApi.get(row.loadId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await asmFeederApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await asmFeederApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

// ========== 详情 ==========
const detailDlg = reactive({ show: false, data: null as AsmFeederLoad | null })
async function openDetail(row: AsmFeederLoad) {
  try {
    detailDlg.data = await asmFeederApi.get(row.loadId!)
    detailDlg.show = true
  } catch (e: any) { ElMessage.error(e.message || '加载详情失败') }
}

// ========== 状态流转 ==========
async function handleChange(row: AsmFeederLoad) {
  await ElMessageBox.confirm('确认执行换料操作？', '换料', { type: 'warning' })
  await asmFeederApi.changeStatus(row.loadId!, 1); ElMessage.success('已换料'); loadList()
}
async function handleUnload(row: AsmFeederLoad) {
  await ElMessageBox.confirm('确认下料？将记录下料时间。', '下料', { type: 'warning' })
  await asmFeederApi.unload(row.loadId!); ElMessage.success('已下料'); loadList()
}
async function handleScrap(row: AsmFeederLoad) {
  await ElMessageBox.confirm('确认报废？此操作不可撤销。', '报废', { type: 'error' })
  await asmFeederApi.changeStatus(row.loadId!, 3); ElMessage.success('已报废'); loadList()
}

// ========== 工具函数 ==========
function formatNow(): string {
  const d = new Date()
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(() => { loadList(); loadOptions() })
</script>

<style scoped>
.asm-page { padding: 0; }
.search-card { margin-bottom: 12px; }
.list-header { display: flex; justify-content: space-between; align-items: center; }
.list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #f7ba2a; display: inline-block; }
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
