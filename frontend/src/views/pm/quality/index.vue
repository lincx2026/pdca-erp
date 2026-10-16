<template>
  <div class="pm-page">
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单">
          <el-select v-model="q.workOrderId" filterable placeholder="全部" clearable style="width:220px" @change="loadList">
            <el-option v-for="w in workOrderOptions" :key="w.workOrderId" :label="`${w.orderNo || ''} ${w.materialName || ''}`" :value="w.workOrderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="不良代码">
          <el-input v-model="q.defectCode" placeholder="D001" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="不良等级">
          <el-select v-model="q.defectLevel" placeholder="全部" clearable style="width:120px">
            <el-option v-for="l in levelOptions" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="检验类型">
          <el-select v-model="q.checkType" placeholder="全部" clearable style="width:120px">
            <el-option v-for="t in checkTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置方式">
          <el-select v-model="q.disposition" placeholder="全部" clearable style="width:120px">
            <el-option v-for="d in dispositionOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序名称">
          <el-input v-model="q.processName" placeholder="工序名称" clearable @keyup.enter="loadList" />
        </el-form-item>
        <el-form-item label="检验员">
          <el-select v-model="q.inspectorId" filterable placeholder="全部" clearable style="width:180px" @change="loadList">
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
          <div class="title"><span class="dot" />质量不良</div>
          <div>
            <el-button type="primary" :icon="Plus" v-hasPermi="'pm:quality:add'" @click="openAdd">新增记录</el-button>
            <el-button :icon="Download" v-hasPermi="'pm:quality:edit'" @click="handleExport">导出</el-button>
          </div>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="defectId" size="default">
        <el-table-column label="ID" prop="defectId" width="70" align="center" />
        <el-table-column label="工单ID" prop="workOrderId" width="90" align="center" />
        <el-table-column label="任务ID" prop="taskId" width="80" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="140" show-overflow-tooltip />
        <el-table-column label="工序" prop="processName" min-width="110" />
        <el-table-column label="不良代码" prop="defectCode" width="110" />
        <el-table-column label="不良名称" prop="defectName" min-width="140" show-overflow-tooltip />
        <el-table-column label="等级" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="levelTagType(row.defectLevel)" size="small">{{ levelLabel(row.defectLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="不良数量" prop="defectQty" width="90" align="right">
          <template #default="{ row }"><span class="bad">{{ row.defectQty }}</span></template>
        </el-table-column>
        <el-table-column label="抽样数" prop="sampleQty" width="80" align="right" />
        <el-table-column label="检验类型" width="90" align="center">
          <template #default="{ row }">{{ checkTypeLabel(row.checkType) }}</template>
        </el-table-column>
        <el-table-column label="检验员" prop="inspectorName" width="100" />
        <el-table-column label="检验时间" prop="checkTime" width="150" />
        <el-table-column label="处置方式" width="100" align="center">
          <template #default="{ row }">{{ dispositionLabel(row.disposition) }}</template>
        </el-table-column>
        <el-table-column label="处理人" prop="handledBy" width="90" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="'pm:quality:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="info" size="small" @click="openPrint(row)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增质量不良' : '修改质量不良'" width="720px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单">
              <el-select v-model="editDlg.form.workOrderId" filterable placeholder="选择工单" clearable style="width:100%">
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
            <el-form-item label="物料">
              <el-select v-model="editDlg.form.materialId" filterable placeholder="选择物料" clearable style="width:100%">
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                  <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序名称">
              <el-input v-model="editDlg.form.processName" placeholder="如 贴片" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良代码" prop="defectCode">
              <el-input v-model="editDlg.form.defectCode" placeholder="如 D001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良名称">
              <el-input v-model="editDlg.form.defectName" placeholder="如 虚焊" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良等级" prop="defectLevel">
              <el-select v-model="editDlg.form.defectLevel" placeholder="选择等级" style="width:100%">
                <el-option v-for="l in levelOptions" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验类型" prop="checkType">
              <el-select v-model="editDlg.form.checkType" placeholder="选择类型" style="width:100%">
                <el-option v-for="t in checkTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="不良数量" prop="defectQty">
              <el-input-number v-model="editDlg.form.defectQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="抽样数">
              <el-input-number v-model="editDlg.form.sampleQty" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="检验员">
              <el-select v-model="editDlg.form.inspectorId" filterable placeholder="选择检验员" clearable style="width:100%">
                <el-option v-for="u in userOptions" :key="u.userId" :label="`${u.userName || ''} ${u.nickName || ''}`" :value="u.userId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验时间">
              <el-date-picker v-model="editDlg.form.checkTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="处置方式">
              <el-select v-model="editDlg.form.disposition" placeholder="选择处置方式" style="width:100%" clearable>
                <el-option v-for="d in dispositionOptions" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="根本原因">
              <el-input v-model="editDlg.form.rootCause" type="textarea" :rows="2" placeholder="根本原因分析" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纠正措施">
              <el-input v-model="editDlg.form.correctiveAction" type="textarea" :rows="2" placeholder="纠正措施" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预防措施">
              <el-input v-model="editDlg.form.preventiveAction" type="textarea" :rows="2" placeholder="预防措施" />
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

    <!-- 打印预览弹窗 -->
    <el-dialog v-model="printVisible" title="打印预览 - 质检报告" width="800px" :close-on-click-modal="false">
      <QualityReportPrint :record="printRow" :visible="printVisible" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { Search, Refresh, Plus, Download } from '@element-plus/icons-vue'
import { qualityApi, workOrderApi, processTaskApi, materialApi, userApi } from '@/api'
import type { PmmQualityDefect } from '@/types/models'
import QualityReportPrint from '@/views/print/QualityReportPrint.vue'

// 打印预览
const printVisible = ref(false)
const printRow = ref<any>(null)
const openPrint = (row: any) => { printRow.value = row; printVisible.value = true }

const levelOptions = [
  { value: 'CRITICAL', label: '严重' },
  { value: 'MAJOR', label: '主要' },
  { value: 'MINOR', label: '次要' },
  { value: 'SUGGESTION', label: '建议' }
]
function levelLabel(v?: string) { return levelOptions.find(l => l.value === v)?.label || v || '-' }
function levelTagType(v?: string): '' | 'success' | 'info' | 'warning' | 'danger' {
  const map: Record<string, '' | 'success' | 'info' | 'warning' | 'danger'> = {
    CRITICAL: 'danger', MAJOR: 'warning', MINOR: 'primary' as any, SUGGESTION: 'info'
  }
  return v ? (map[v] || '') : ''
}

const checkTypeOptions = [
  { value: 'IQC', label: '进料检' },
  { value: 'IPQC', label: '过程检' },
  { value: 'FQC', label: '成品检' },
  { value: 'OQC', label: '出货检' },
  { value: 'SPC', label: '统计过程' }
]
function checkTypeLabel(v?: string) { return checkTypeOptions.find(t => t.value === v)?.label || v || '-' }

const dispositionOptions = [
  { value: 'PENDING', label: '待处理' },
  { value: 'REWORK', label: '返工' },
  { value: 'SCRAP', label: '报废' },
  { value: 'CONCESSION', label: '让步' },
  { value: 'RETURN', label: '退回' }
]
function dispositionLabel(v?: string) { return dispositionOptions.find(d => d.value === v)?.label || v || '-' }

const q = reactive({
  pageNum: 1, pageSize: 20,
  workOrderId: undefined as number | undefined,
  defectCode: '', defectLevel: '', checkType: '', disposition: '',
  processName: '', inspectorId: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as PmmQualityDefect[], total: 0 } })
const workOrderOptions = ref<any[]>([])
const taskOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])
const userOptions = ref<any[]>([])

async function loadList() {
  list.loading = true
  try {
    const res = await qualityApi.list({ ...q })
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
    const r = await materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    materialOptions.value = (r as any)?.rows || []
  } catch { materialOptions.value = [] }
  try {
    const r = await userApi.options() as any
    userOptions.value = r?.data || r || []
  } catch { userOptions.value = [] }
}
function resetQ() {
  Object.assign(q, { workOrderId: undefined, defectCode: '', defectLevel: '', checkType: '', disposition: '', processName: '', inspectorId: undefined, pageNum: 1 })
  loadList()
}

const editFormRef = ref<FormInstance>()
const editDlg = reactive({ show: false, isNew: true, saving: false, form: {} as Partial<PmmQualityDefect> })
const editRules = {
  defectCode: [{ required: true, message: '不良代码不能为空', trigger: 'blur' }],
  defectLevel: [{ required: true, message: '请选择不良等级', trigger: 'change' }],
  defectQty: [{ required: true, message: '不良数量不能为空', trigger: 'blur' }],
  checkType: [{ required: true, message: '请选择检验类型', trigger: 'change' }]
}
function openAdd() {
  editDlg.isNew = true
  editDlg.form = { defectLevel: 'MINOR', checkType: 'IPQC', defectQty: 1, disposition: 'PENDING' }
  editDlg.show = true
}
async function openEdit(row: PmmQualityDefect) {
  editDlg.isNew = false
  editDlg.show = true
  try {
    const res = await qualityApi.get(row.defectId!)
    editDlg.form = { ...res }
  } catch { editDlg.form = { ...row } }
}
async function submitEdit() {
  await editFormRef.value?.validate()
  editDlg.saving = true
  try {
    if (editDlg.isNew) { await qualityApi.add(editDlg.form); ElMessage.success('新增成功') }
    else { await qualityApi.edit(editDlg.form); ElMessage.success('修改成功') }
    editDlg.show = false; loadList()
  } catch (e: any) { ElMessage.error(e.message || '保存失败') }
  finally { editDlg.saving = false }
}

async function handleExport() {
  try {
    const blob = await qualityApi.export({ ...q })
    const url = URL.createObjectURL(new Blob([blob]))
    const a = document.createElement('a')
    a.href = url; a.download = `quality-defect-${Date.now()}.xlsx`; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch { ElMessage.error('导出失败') }
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
.bad { color: #f56c6c; font-weight: 600; }
</style>
