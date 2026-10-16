<template>
  <div class="pm-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="排产日期">
          <el-date-picker v-model="q.scheduleDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:160px" @change="loadList" />
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="q.shift" placeholder="全部" clearable style="width:110px" @change="loadList">
            <el-option label="白班" value="DAY" />
            <el-option label="夜班" value="NIGHT" />
            <el-option label="加班" value="OT" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单">
          <el-select v-model="q.workOrderId" filterable placeholder="全部" clearable style="width:220px" @change="loadList">
            <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作中心">
          <el-input v-model="q.workCenter" placeholder="工作中心" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="设备编码">
          <el-input v-model="q.machineCode" placeholder="设备编码" clearable @keyup.enter="loadList" />
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
          <div class="title"><span class="dot" />排产计划</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:schedule:list'" @click="openAdd">新增排产</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="planId" size="default">
        <el-table-column label="ID" prop="planId" width="70" align="center" />
        <el-table-column label="排产日期" prop="scheduleDate" width="110" />
        <el-table-column label="班次" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.shift==='DAY'" type="warning" size="small">白班</el-tag>
            <el-tag v-else-if="row.shift==='NIGHT'" type="primary" size="small">夜班</el-tag>
            <el-tag v-else-if="row.shift==='OT'" type="danger" size="small">加班</el-tag>
            <span v-else>{{ row.shift || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工单ID" prop="workOrderId" width="90" align="center" />
        <el-table-column label="任务ID" prop="taskId" width="80" align="center" />
        <el-table-column label="工作中心" prop="workCenter" width="110" />
        <el-table-column label="设备编码" prop="machineCode" width="120" />
        <el-table-column label="操作人" prop="operatorName" width="100" />
        <el-table-column label="计划数量" prop="planQty" width="90" align="right" />
        <el-table-column label="开始时间" prop="startTime" width="150" />
        <el-table-column label="结束时间" prop="endTime" width="150" />
        <el-table-column label="顺序" prop="sequence" width="70" align="center" />
        <el-table-column label="锁定" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isFixed === 1" type="danger" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:schedule:list'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="'pm:schedule:list'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增排产计划' : '修改排产计划'" width="680px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排产日期" prop="scheduleDate">
              <el-date-picker v-model="editDlg.form.scheduleDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次" prop="shift">
              <el-select v-model="editDlg.form.shift" placeholder="选择班次" style="width:100%">
                <el-option label="白班" value="DAY" />
                <el-option label="夜班" value="NIGHT" />
                <el-option label="加班" value="OT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工单" prop="workOrderId">
              <el-select v-model="editDlg.form.workOrderId" filterable placeholder="选择工单" style="width:100%">
                <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务">
              <el-select v-model="editDlg.form.taskId" filterable placeholder="选择任务" clearable style="width:100%">
                <el-option v-for="t in taskOptions" :key="t.taskId" :label="`#${t.taskId} ${t.processName || ''}`" :value="t.taskId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工作中心">
              <el-input v-model="editDlg.form.workCenter" placeholder="如 SMT-Line1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备编码">
              <el-input v-model="editDlg.form.machineCode" placeholder="设备编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="操作人">
              <el-select v-model="editDlg.form.operatorId" filterable placeholder="选择操作人" clearable style="width:100%">
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
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker v-model="editDlg.form.startTime" value-format="HH:mm:ss" placeholder="选择开始时间" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker v-model="editDlg.form.endTime" value-format="HH:mm:ss" placeholder="选择结束时间" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="editDlg.form.sequence" :min="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="锁定">
              <el-radio-group v-model="editDlg.form.isFixed">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
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
import { scheduleApi, workOrderApi, processTaskApi, userApi } from '@/api'
import type { PmmSchedulePlan } from '@/types/models'

const today = new Date().toISOString().slice(0, 10)
const q = reactive({
  pageNum: 1, pageSize: 20,
  scheduleDate: '',
  shift: '', workOrderId: undefined as number | undefined,
  workCenter: '', machineCode: ''
})
const list = reactive({ loading: false, data: { rows: [] as PmmSchedulePlan[], total: 0 } })
const workOrderOptions = ref<any[]>([])
const taskOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

async function loadList() {
  list.loading = true
  try {
    const res = await scheduleApi.list({ ...q })
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
    const r = await processTaskApi.list({ pageNum: 1, pageSize: 1000 }) as any
    taskOptions.value = (r as any)?.rows || []
  } catch { taskOptions.value = [] }
  try {
    const r = await userApi.options() as any
    userOptions.value = r?.data || r || []
  } catch { userOptions.value = [] }
}
function resetQ() {
  Object.assign(q, { scheduleDate: '', shift: '', workOrderId: undefined, workCenter: '', machineCode: '', pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<PmmSchedulePlan> })
const editRules = {
  scheduleDate: [{ required: true, message: '请选择排产日期', trigger: 'change' }],
  shift: [{ required: true, message: '请选择班次', trigger: 'change' }],
  workOrderId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}
function openAdd() {
  editDlg.isNew = true
  editDlg.form = { scheduleDate: today, shift: 'DAY', isFixed: 0, sequence: 1, startTime: '08:00:00', endTime: '17:00:00' }
  editDlg.show = true
}
async function openEdit(row: PmmSchedulePlan) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await scheduleApi.get(row.planId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    await scheduleApi.save(editDlg.form)
    ElMessage.success(editDlg.isNew ? '新增成功' : '修改成功')
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}
async function handleRemove(row: PmmSchedulePlan) {
  await ElMessageBox.confirm('确认删除该排产？', '提示', { type: 'warning' })
  await scheduleApi.remove(row.planId!); ElMessage.success('删除成功'); loadList()
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
