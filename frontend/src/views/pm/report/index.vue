<template>
  <div class="pm-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="报工单号">
          <el-input v-model="q.reportNo" placeholder="RPT-xxxx" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="工单">
          <el-select v-model="q.workOrderId" filterable placeholder="全部" clearable style="width:220px" @change="loadList">
            <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务">
          <el-select v-model="q.taskId" filterable placeholder="全部" clearable style="width:180px" @change="loadList">
            <el-option v-for="t in taskOptions" :key="t.taskId" :label="`#${t.taskId} ${t.processName || ''}`" :value="t.taskId" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-select v-model="q.operatorId" filterable placeholder="全部" clearable style="width:180px" @change="loadList">
            <el-option v-for="u in userOptions" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="报工类型">
          <el-select v-model="q.reportType" placeholder="全部" clearable style="width:120px">
            <el-option v-for="t in reportTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="班次">
          <el-select v-model="q.shift" placeholder="全部" clearable style="width:110px">
            <el-option label="白班" value="DAY" />
            <el-option label="夜班" value="NIGHT" />
            <el-option label="加班" value="OT" />
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
          <div class="title"><span class="dot" />生产报工</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:report:add'" @click="openAdd">新增报工</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="reportId" size="default">
        <el-table-column label="报工单号" prop="reportNo" width="150" fixed />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.reportType)" size="small">{{ typeLabel(row.reportType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工单ID" prop="workOrderId" width="90" align="center" />
        <el-table-column label="任务ID" prop="taskId" width="80" align="center" />
        <el-table-column label="工序" prop="processName" min-width="120" />
        <el-table-column label="工作中心" prop="workCenter" width="110" />
        <el-table-column label="操作人" prop="operatorName" width="100" />
        <el-table-column label="班次" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.shift==='DAY'">白班</span>
            <span v-else-if="row.shift==='NIGHT'">夜班</span>
            <span v-else-if="row.shift==='OT'">加班</span>
            <span v-else>{{ row.shift || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="投入" prop="inputQty" width="80" align="right" />
        <el-table-column label="合格" prop="passQty" width="80" align="right">
          <template #default="{ row }"><span class="ok">{{ row.passQty || 0 }}</span></template>
        </el-table-column>
        <el-table-column label="不良" prop="failQty" width="80" align="right">
          <template #default="{ row }"><span v-if="row.failQty" class="bad">{{ row.failQty }}</span><span v-else>0</span></template>
        </el-table-column>
        <el-table-column label="工时(分)" prop="usedMinutes" width="90" align="right" />
        <el-table-column label="设备" prop="machineCode" width="110" />
        <el-table-column label="模具" prop="moldCode" width="110" />
        <el-table-column label="质检结果" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isQcPass === 1" type="success" size="small">合格</el-tag>
            <el-tag v-else-if="row.isQcPass === 0" type="danger" size="small">不合格</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="质检人" prop="qcBy" width="90" />
        <el-table-column label="质检时间" prop="qcTime" width="150" />
        <el-table-column label="创建时间" prop="createTime" width="150" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="warning" v-hasPermi="'pm:report:edit'" @click="handleRevoke(row)">撤销</el-button>
            <el-button link type="success" v-if="row.isQcPass !== 1" v-hasPermi="'pm:report:edit'" @click="openQc(row)">质检</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新增报工 -->
    <el-dialog v-model="editDlg.show" title="新增生产报工" width="700px" destroy-on-close>
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
            <el-form-item label="任务" prop="taskId">
              <el-select v-model="editDlg.form.taskId" filterable placeholder="选择任务" clearable style="width:100%">
                <el-option v-for="t in taskOptions" :key="t.taskId" :label="`#${t.taskId} ${t.processName || ''}`" :value="t.taskId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报工类型" prop="reportType">
              <el-select v-model="editDlg.form.reportType" placeholder="选择类型" style="width:100%">
                <el-option v-for="t in reportTypes" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="班次">
              <el-select v-model="editDlg.form.shift" placeholder="选择班次" style="width:100%" clearable>
                <el-option label="白班" value="DAY" />
                <el-option label="夜班" value="NIGHT" />
                <el-option label="加班" value="OT" />
              </el-select>
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
            <el-form-item label="工时(分钟)">
              <el-input-number v-model="editDlg.form.usedMinutes" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="投入数量">
              <el-input-number v-model="editDlg.form.inputQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="合格数量">
              <el-input-number v-model="editDlg.form.passQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="不良数量">
              <el-input-number v-model="editDlg.form.failQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备编码">
              <el-input v-model="editDlg.form.machineCode" placeholder="设备编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模具编码">
              <el-input v-model="editDlg.form.moldCode" placeholder="模具编码" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="不良代码">
              <el-input v-model="editDlg.form.defectCodes" placeholder="多个以逗号分隔，如 D001,D002" />
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
        <el-button type="primary" :loading="editDlg.saving" @click="submitEdit">提交报工</el-button>
      </template>
    </el-dialog>

    <!-- 质检对话框 -->
    <el-dialog v-model="qcDlg.show" title="质检处理" width="520px" destroy-on-close>
      <el-form :model="qcDlg.form" :rules="qcRules" ref="qcFormRef" label-width="100px">
        <el-form-item label="报工单号">
          <span>{{ qcDlg.reportNo }}</span>
        </el-form-item>
        <el-form-item label="质检结果" prop="isQcPass">
          <el-radio-group v-model="qcDlg.form.isQcPass">
            <el-radio :value="1">合格</el-radio>
            <el-radio :value="0">不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="质检备注">
          <el-input v-model="qcDlg.form.qcRemark" type="textarea" :rows="3" placeholder="质检备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="qcDlg.saving" @click="submitQc">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { workReportApi, workOrderApi, processTaskApi, userApi } from '@/api'
import type { PmmWorkReport } from '@/types/models'

const reportTypes = [
  { value: 'START', label: '开工' },
  { value: 'END', label: '完工' },
  { value: 'PAUSE', label: '暂停' },
  { value: 'PROGRESS', label: '进度' }
]
function typeLabel(v?: string) { return reportTypes.find(t => t.value === v)?.label || v || '-' }
function typeTagType(v?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    START: 'warning', END: 'success', PAUSE: 'info', PROGRESS: 'primary' as any
  }
  return v ? (map[v] || '') : ''
}

const q = reactive({
  pageNum: 1, pageSize: 20,
  reportNo: '', workOrderId: undefined as number | undefined,
  taskId: undefined as number | undefined, operatorId: undefined as number | undefined,
  reportType: '', shift: ''
})
const list = reactive({ loading: false, data: { rows: [] as PmmWorkReport[], total: 0 } })
const workOrderOptions = ref<any[]>([])
const taskOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

async function loadList() {
  list.loading = true
  try {
    const res = await workReportApi.list({ ...q })
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
  Object.assign(q, { reportNo: '', workOrderId: undefined, taskId: undefined, operatorId: undefined, reportType: '', shift: '', pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, saving: false, form: {} as Partial<PmmWorkReport> })
const editRules = {
  workOrderId: [{ required: true, message: '工单ID不能为空', trigger: 'blur' }],
  taskId: [{ required: true, message: '任务ID不能为空', trigger: 'blur' }],
  reportType: [{ required: true, message: '请选择报工类型', trigger: 'change' }]
}
function openAdd() {
  editDlg.form = { reportType: 'PROGRESS', shift: 'DAY', inputQty: 0, passQty: 0, failQty: 0, usedMinutes: 0 }
  editDlg.show = true
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    await workReportApi.add(editDlg.form)
    ElMessage.success('报工成功')
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '报工失败') }
  finally { editDlg.saving = false }
}

async function handleRevoke(row: PmmWorkReport) {
  await ElMessageBox.confirm('确认撤销该报工记录？', '提示', { type: 'warning' })
  await workReportApi.revoke(row.reportId!); ElMessage.success('已撤销'); loadList()
}

const qcFormRef = ref<FormInstance>()
const qcDlg = reactive({ show: false, saving: false, reportNo: '', form: {} as Partial<PmmWorkReport> })
const qcRules = {
  isQcPass: [{ required: true, message: '请选择质检结果', trigger: 'change' }]
}
function openQc(row: PmmWorkReport) {
  qcDlg.reportNo = row.reportNo
  qcDlg.form = { reportId: row.reportId, isQcPass: undefined as any, qcRemark: '' }
  qcDlg.show = true
}
async function submitQc() {
  await qcFormRef.value?.validate()
  qcDlg.saving = true
  try {
    await workReportApi.qc(qcDlg.form)
    ElMessage.success('质检完成')
    qcDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '质检失败') }
  finally { qcDlg.saving = false }
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
