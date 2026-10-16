<template>
  <div class="fas-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单">
          <el-select v-model="q.orderId" filterable placeholder="全部" clearable style="width:240px" @change="loadList">
            <el-option v-for="o in orderOptions" :key="o.orderId" :label="`${o.orderNo} ${o.productName || ''}`" :value="o.orderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="工位名称">
          <el-input v-model="q.stepName" placeholder="模糊搜索" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="q.status" placeholder="全部" clearable style="width:130px" @change="loadList">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadList">查询</el-button>
          <el-button :icon="Refresh" @click="resetQ">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="list-header">
          <div class="title"><span class="dot" />工序流转</div>
          <el-button type="primary" :icon="Plus" v-hasPermi="'pm:fas:step:add'" @click="openAdd">新增工序</el-button>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="stepId" size="default">
        <el-table-column label="工单号" width="170" fixed>
          <template #default="{ row }">{{ orderMap.get(row.orderId)?.orderNo || row.orderId }}</template>
        </el-table-column>
        <el-table-column label="工序号" prop="stepNo" width="80" align="center" />
        <el-table-column label="工位名称" prop="stepName" min-width="160" show-overflow-tooltip />
        <el-table-column label="工位/产线" prop="workcenter" width="120" show-overflow-tooltip />
        <el-table-column label="标准工时(分)" prop="stdTimeMin" width="110" align="right" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行人" prop="operator" width="100" />
        <el-table-column label="开始时间" prop="startTime" width="160" />
        <el-table-column label="结束时间" prop="endTime" width="160" />
        <el-table-column label="合格数" prop="qtyPass" width="90" align="right" />
        <el-table-column label="不良数" prop="qtyNg" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.qtyNg || 0) > 0 ? 'var(--el-color-danger)' : '' }">{{ row.qtyNg || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-if="row.status === 0" v-hasPermi="'pm:fas:step:edit'" @click="openStart(row)">开始工序</el-button>
            <el-button link type="success" v-if="row.status === 1" v-hasPermi="'pm:fas:step:edit'" @click="openComplete(row)">完成工序</el-button>
            <el-button link type="primary" v-hasPermi="'pm:fas:step:edit'" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" v-if="row.status === 0" v-hasPermi="'pm:fas:step:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新增/编辑 Dialog -->
    <el-dialog v-model="editDlg.show" :title="editDlg.isNew ? '新增工序' : '修改工序'" width="600px" destroy-on-close>
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="工单" prop="orderId">
              <el-select v-model="editDlg.form.orderId" filterable placeholder="选择工单" style="width:100%">
                <el-option v-for="o in orderOptions" :key="o.orderId" :label="`${o.orderNo} ${o.productName || ''}`" :value="o.orderId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序号" prop="stepNo">
              <el-input-number v-model="editDlg.form.stepNo" :min="1" :max="999" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工位名称" prop="stepName">
              <el-input v-model="editDlg.form.stepName" maxlength="100" placeholder="如 装配-1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工位/产线">
              <el-input v-model="editDlg.form.workcenter" maxlength="50" placeholder="如 FAS-A" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标准工时(分)">
              <el-input-number v-model="editDlg.form.stdTimeMin" :min="0" :precision="2" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="editDlg.form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
          </el-col>
        </el-row>
        <template #footer>
          <el-button @click="editDlg.show = false">取消</el-button>
          <el-button type="primary" @click="submitEdit">确定</el-button>
        </template>
      </el-form>
    </el-dialog>

    <!-- 开始工序 Dialog -->
    <el-dialog v-model="startDlg.show" title="开始工序" width="420px" destroy-on-close>
      <el-form :model="startDlg.form" :rules="startRules" ref="startFormRef" label-width="100px">
        <el-form-item label="工序">
          <span>{{ startDlg.row?.stepName }}</span>
        </el-form-item>
        <el-form-item label="执行人" prop="operator">
          <el-input v-model="startDlg.form.operator" maxlength="50" placeholder="请输入执行人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDlg.show = false">取消</el-button>
        <el-button type="primary" @click="submitStart">确定开始</el-button>
      </template>
    </el-dialog>

    <!-- 完成工序 Dialog -->
    <el-dialog v-model="completeDlg.show" title="完成工序" width="480px" destroy-on-close>
      <el-form :model="completeDlg.form" :rules="completeRules" ref="completeFormRef" label-width="100px">
        <el-form-item label="工序">
          <span>{{ completeDlg.row?.stepName }}</span>
        </el-form-item>
        <el-form-item label="合格数" prop="qtyPass">
          <el-input-number v-model="completeDlg.form.qtyPass" :min="0" :max="999999" style="width:100%" controls-position="right" />
        </el-form-item>
        <el-form-item label="不良数" prop="qtyNg">
          <el-input-number v-model="completeDlg.form.qtyNg" :min="0" :max="999999" style="width:100%" controls-position="right" />
        </el-form-item>
        <el-form-item label="执行人" prop="operator">
          <el-input v-model="completeDlg.form.operator" maxlength="50" placeholder="请输入执行人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDlg.show = false">取消</el-button>
        <el-button type="primary" @click="submitComplete">确定完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmFasApi } from '@/api/index'
import type { FasStep, FasOrder } from '@/types/models'

// ========== 字典 ==========
const STATUS_OPTIONS: { label: string; value: number }[] = [
  { label: '待开始', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '跳过',   value: 3 },
]
const statusTag = (v: number) => {
  const map: Record<string, { label: string; type: any }> = {
    '0': { label: '待开始', type: 'info' },
    '1': { label: '进行中', type: 'primary' },
    '2': { label: '已完成', type: 'success' },
    '3': { label: '跳过',   type: 'danger' },
  }
  return map[String(v)] || { label: '-', type: 'info' }
}

// ========== 搜索/列表 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  orderId: undefined as number | undefined,
  stepName: '', status: undefined as number | undefined
})
const list = reactive({ loading: false, data: { rows: [] as FasStep[], total: 0 } })
const orderOptions = ref<FasOrder[]>([])
const orderMap = computed(() => {
  const m = new Map<number, FasOrder>()
  orderOptions.value.forEach(e => { if (e.orderId) m.set(e.orderId, e) })
  return m
})
const loadList = async () => {
  list.loading = true
  try {
    const r = await pmFasApi.stepList({ ...q })
    list.data = { rows: r?.rows || [], total: r?.total || 0 }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20
  q.orderId = undefined; q.stepName = ''; q.status = undefined
  loadList()
}
const loadOrders = async () => {
  try {
    const r = await pmFasApi.orderList({ pageNum: 1, pageSize: 500 })
    orderOptions.value = r?.rows || []
  } catch {}
}

// ========== 新增/编辑 ==========
const editDlg = reactive<{ show: boolean; isNew: boolean; form: Partial<FasStep> }>({ show: false, isNew: true, form: {} })
const editFormRef = ref<FormInstance>()
const editRules: FormRules = {
  orderId:  [{ required: true, message: '请选择工单', trigger: 'change' }],
  stepNo:   [{ required: true, message: '请填写工序号', trigger: 'blur' }],
  stepName: [{ required: true, message: '请填写工位名称', trigger: 'blur' }],
}
const openAdd = () => {
  editDlg.isNew = true
  editDlg.form = { stepNo: 1, status: 0, qtyPass: 0, qtyNg: 0 }
  editDlg.show = true
}
const openEdit = (row: FasStep) => {
  editDlg.isNew = false
  editDlg.form = JSON.parse(JSON.stringify(row))
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await editFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    if (editDlg.isNew) {
      await pmFasApi.stepAdd(editDlg.form)
      ElMessage.success('新增工序成功')
    } else {
      await pmFasApi.stepEdit(editDlg.form)
      ElMessage.success('修改成功')
    }
    editDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  }
}
const handleRemove = async (row: FasStep) => {
  try {
    await ElMessageBox.confirm(`确认删除工序【${row.stepName}】？`, '删除确认', { type: 'warning' })
    await pmFasApi.stepRemove(row.stepId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 开始工序 ==========
const startDlg = reactive<{ show: boolean; row: FasStep | null; form: { operator: string } }>({
  show: false, row: null, form: { operator: '' }
})
const startFormRef = ref<FormInstance>()
const startRules: FormRules = {
  operator: [{ required: true, message: '请输入执行人', trigger: 'blur' }],
}
const openStart = (row: FasStep) => {
  startDlg.row = row
  startDlg.form = { operator: '' }
  startDlg.show = true
}
const submitStart = async () => {
  const ok = await startFormRef.value?.validate().catch(() => false)
  if (!ok || !startDlg.row) return
  try {
    await pmFasApi.stepStart(startDlg.row.stepId, startDlg.form.operator)
    ElMessage.success('工序已开始')
    startDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '开始失败')
  }
}

// ========== 完成工序 ==========
const completeDlg = reactive<{ show: boolean; row: FasStep | null; form: { qtyPass: number; qtyNg: number; operator: string } }>({
  show: false, row: null, form: { qtyPass: 0, qtyNg: 0, operator: '' }
})
const completeFormRef = ref<FormInstance>()
const completeRules: FormRules = {
  qtyPass:  [{ required: true, message: '请填写合格数', trigger: 'blur' }],
  qtyNg:    [{ required: true, message: '请填写不良数(无则填0)', trigger: 'blur' }],
  operator: [{ required: true, message: '请输入执行人', trigger: 'blur' }],
}
const openComplete = (row: FasStep) => {
  completeDlg.row = row
  completeDlg.form = { qtyPass: 0, qtyNg: 0, operator: row.operator || '' }
  completeDlg.show = true
}
const submitComplete = async () => {
  const ok = await completeFormRef.value?.validate().catch(() => false)
  if (!ok || !completeDlg.row) return
  try {
    await pmFasApi.stepComplete(completeDlg.row.stepId, completeDlg.form)
    ElMessage.success('工序已完成')
    completeDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '完成失败')
  }
}

onMounted(() => { loadList(); loadOrders() })
</script>

<style scoped>
.fas-page { display: flex; flex-direction: column; gap: 14px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
</style>
