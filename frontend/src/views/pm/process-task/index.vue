<template>
  <div class="pm-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单">
          <el-select v-model="q.workOrderId" filterable placeholder="全部" clearable style="width:220px" @change="loadList">
            <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="q.processName" placeholder="工序名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="工作中心">
          <el-input v-model="q.workCenter" placeholder="工作中心" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:120px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="派工人">
          <el-select v-model="q.assignedUserId" filterable placeholder="全部" clearable style="width:180px" @change="loadList">
            <el-option v-for="u in userOptions" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />工序任务</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:processtask:add'" @click="openAdd">新增任务</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="taskId" size="default">
        <el-table-column label="任务ID" prop="taskId" width="80" align="center" />
        <el-table-column label="工单ID" prop="workOrderId" width="90" align="center" />
        <el-table-column label="工序" prop="processName" min-width="140" show-overflow-tooltip />
        <el-table-column label="工序编码" prop="processCode" width="120" />
        <el-table-column label="顺序" prop="sequence" width="70" align="center" />
        <el-table-column label="工作中心" prop="workCenter" width="110" />
        <el-table-column label="派工人" prop="assignedUser" width="100" />
        <el-table-column label="计划数量" prop="planQty" width="90" align="right" />
        <el-table-column label="投入" prop="inputQty" width="80" align="right" />
        <el-table-column label="合格" prop="passQty" width="80" align="right">
          <template #default="{ row }"><span class="ok">{{ row.passQty || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="不良" prop="failQty" width="80" align="right">
          <template #default="{ row }"><span v-if="row.failQty" class="bad">{{ row.failQty }}</span><span v-else>0</span></template>
        </el-table-column>
        <el-table-column label="节拍(秒)" prop="cycleTimeSec" width="90" align="right" />
        <el-table-column label="关键工序" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isKeyProcess === 1" type="danger" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="需质检" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isQcRequired === 1" type="warning" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="计划开始" prop="planStartTime" width="150" />
        <el-table-column label="计划结束" prop="planEndTime" width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-if="row.status===0 || row.status===10" v-hasPermi="'pm:processtask:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" v-if="row.status===10" v-hasPermi="'pm:processtask:edit'" @click="handleStart(row)">开工</el-button>
            <el-button link type="success" v-if="row.status===20" v-hasPermi="'pm:processtask:edit'" @click="handleFinish(row)">完工</el-button>
            <el-button link type="warning" v-if="row.status===40" v-hasPermi="'pm:processtask:edit'" @click="handleTransfer(row)">流转</el-button>
            <el-button link type="danger" v-if="row.status===0" v-hasPermi="'pm:processtask:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增工序任务' : '修改工序任务'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单" prop="workOrderId">
              <el-select v-model="editDlg.form.workOrderId" filterable placeholder="选择工单" style="width:100%">
                <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称" prop="processName">
              <el-input v-model="editDlg.form.processName" placeholder="如 贴片、焊接、测试" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序编码">
              <el-input v-model="editDlg.form.processCode" placeholder="如 PRC-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="顺序">
              <el-input-number v-model="editDlg.form.sequence" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作中心">
              <el-input v-model="editDlg.form.workCenter" placeholder="如 SMT-Line1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="派工人">
              <el-select v-model="editDlg.form.assignedUserId" filterable placeholder="选择派工人" clearable style="width:100%">
                <el-option v-for="u in userOptions" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划数量">
              <el-input-number v-model="editDlg.form.planQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="节拍(秒)">
              <el-input-number v-model="editDlg.form.cycleTimeSec" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关键工序">
              <el-radio-group v-model="editDlg.form.isKeyProcess">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需质检">
              <el-radio-group v-model="editDlg.form.isQcRequired">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker v-model="editDlg.form.planStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker v-model="editDlg.form.planEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" placeholder="备注" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { processTaskApi, workOrderApi, userApi } from '@/api'
import type { PmmProcessTask } from '@/types/models'

const statusOptions = [
  { value: 0, label: '待派发' },
  { value: 10, label: '已派发' },
  { value: 20, label: '加工中' },
  { value: 30, label: '暂停' },
  { value: 40, label: '已完成' },
  { value: 50, label: '已流转' },
  { value: 90, label: '取消' }
]
function statusLabel(v?: number) { return statusOptions.find(s => s.value === v)?.label || (v ?? '-') }
function statusTagType(v?: number): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<number, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    0: 'info', 10: 'warning', 20: 'primary' as any, 30: 'warning', 40: 'success', 50: 'success', 90: 'danger'
  }
  return v !== undefined ? (map[v] || '') : ''
}

const q = reactive({
  pageNum: 1, pageSize: 20,
  workOrderId: undefined as number | undefined,
  processName: '', workCenter: '',
  status: undefined as number | undefined,
  assignedUserId: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as PmmProcessTask[], total: 0 } })
const workOrderOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

async function loadList() {
  list.loading = true
  try {
    const res = await processTaskApi.list({ ...q })
    list.data = res || { rows: [], total: 0 }
  } catch (e: any) { ElMessage.error(e.message || '查询失败') }
  finally { list.loading = false }
}
async function loadOptions() {
  try {
    const r = await workOrderApi.options() as any
    workOrderOptions.value = r?.data || r || []
  } catch { workOrderOptions.value = [] }
  try {
    const r = await userApi.options() as any
    userOptions.value = r?.data || r || []
  } catch { userOptions.value = [] }
}
function resetQ() {
  Object.assign(q, { workOrderId: undefined, processName: '', workCenter: '', status: undefined, assignedUserId: undefined, pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<PmmProcessTask> })
const editRules = {
  workOrderId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }],
  processName: [{ required: true, message: '工序名称不能为空', trigger: 'blur' }]
}
function openAdd() {
  editDlg.isNew = true
  editDlg.form = { sequence: 1, isKeyProcess: 0, isQcRequired: 0 }
  editDlg.show = true
}
async function openEdit(row: PmmProcessTask) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await processTaskApi.get(row.taskId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await processTaskApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await processTaskApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}
async function handleStart(row: PmmProcessTask) {
  await processTaskApi.start(row.taskId!); ElMessage.success('已开工'); loadList()
}
async function handleFinish(row: PmmProcessTask) {
  await processTaskApi.finish(row.taskId!); ElMessage.success('已完工'); loadList()
}
async function handleTransfer(row: PmmProcessTask) {
  await processTaskApi.transfer(row.taskId!); ElMessage.success('已流转'); loadList()
}
async function handleRemove(row: PmmProcessTask) {
  await ElMessageBox.confirm('确认删除该任务？', '提示', { type: 'warning' })
  await processTaskApi.remove(row.taskId!); ElMessage.success('删除成功'); loadList()
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
.ok { color: #67c23a; font-weight: 600; }
.bad { color: #f56c6c; font-weight: 600; }
</style>
