<template>
  <div class="fas-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="q" @submit.prevent="loadList">
        <el-form-item label="工单号">
          <el-input v-model="q.orderNo" placeholder="如 FAS-2024-001" clearable @keyup.enter="loadList" style="width:180px" />
        </el-form-item>
        <el-form-item label="记录类型">
          <el-select v-model="q.recordType" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option v-for="t in RECORD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行人">
          <el-input v-model="q.operator" placeholder="姓名" clearable @keyup.enter="loadList" style="width:120px" />
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
          <div class="title"><span class="dot" />过程记录</div>
          <el-button type="primary" :icon="Plus" v-hasPermi="'pm:fas:record:add'" @click="openAdd">新增记录</el-button>
        </div>
      </template>

      <el-table :data="list.data.rows || []" v-loading="list.loading" stripe border row-key="recordId" size="default">
        <el-table-column label="工单号" width="170" fixed>
          <template #default="{ row }">{{ orderMap.get(row.orderId)?.orderNo || row.orderId }}</template>
        </el-table-column>
        <el-table-column label="记录类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="recordTypeTag(row.recordType).type" size="small" effect="plain">{{ recordTypeTag(row.recordType).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行人" prop="operator" width="100" />
        <el-table-column label="事件时间" prop="eventTime" width="160" />
        <el-table-column label="批次号" prop="batchNo" width="130" show-overflow-tooltip />
        <el-table-column label="子件ID" prop="componentId" width="100" align="right" />
        <el-table-column label="数量" prop="qtyUsed" width="90" align="right" />
        <el-table-column label="质检结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.checkResult === 'OK'" type="success" size="small">合格</el-tag>
            <el-tag v-else-if="row.checkResult === 'NG'" type="danger" size="small">不合格</el-tag>
            <span v-else style="color:var(--el-text-color-secondary)">-</span>
          </template>
        </el-table-column>
        <el-table-column label="不良类型" prop="ngType" width="120" show-overflow-tooltip />
        <el-table-column label="不良描述" prop="ngDesc" min-width="160" show-overflow-tooltip />
        <el-table-column label="费用" prop="costAmount" width="90" align="right">
          <template #default="{ row }">{{ formatNum(row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" v-hasPermi="'pm:fas:record:remove'" @click="handleRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" v-model:current-page="q.pageNum" v-model:page-size="q.pageSize"
        :total="list.data.total || 0" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadList" @current-change="loadList" />
    </el-card>

    <!-- 新增 Dialog -->
    <el-dialog v-model="editDlg.show" title="新增过程记录" width="760px" destroy-on-close top="6vh">
      <el-form :model="editDlg.form" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单" prop="orderId">
              <el-select v-model="editDlg.form.orderId" filterable placeholder="选择工单" style="width:100%" @change="onOrderChange">
                <el-option v-for="o in orderOptions" :key="o.orderId" :label="`${o.orderNo} ${o.productName || ''}`" :value="o.orderId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工序">
              <el-select v-model="editDlg.form.stepId" filterable placeholder="选择工序(可选)" clearable style="width:100%">
                <el-option v-for="s in stepOptions" :key="s.stepId" :label="`工序${s.stepNo} ${s.stepName}`" :value="s.stepId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="记录类型" prop="recordType">
              <el-select v-model="editDlg.form.recordType" placeholder="请选择" style="width:100%">
                <el-option v-for="t in RECORD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执行人" prop="operator">
              <el-input v-model="editDlg.form.operator" maxlength="50" placeholder="执行人姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="事件时间" prop="eventTime">
              <el-date-picker v-model="editDlg.form.eventTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="批次号">
              <el-input v-model="editDlg.form.batchNo" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="子件(物料)">
              <el-select v-model="editDlg.form.componentId" placeholder="输入编码/名称搜索" filterable style="width:100%">
                <el-option v-for="m in materialOptions" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName}`" :value="m.materialId">
                  <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ m.specification || '' }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量">
              <el-input-number v-model="editDlg.form.qtyUsed" :min="0" :precision="4" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="质检结果">
              <el-select v-model="editDlg.form.checkResult" placeholder="无" clearable style="width:100%">
                <el-option label="合格" value="OK" />
                <el-option label="不合格" value="NG" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不良类型">
              <el-input v-model="editDlg.form.ngType" maxlength="50" placeholder="如 短路/缺件/错件" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="费用">
              <el-input-number v-model="editDlg.form.costAmount" :min="0" :precision="2" style="width:100%" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="不良描述">
              <el-input v-model="editDlg.form.ngDesc" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editDlg.form.remark" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>
        <template #footer>
          <el-button @click="editDlg.show = false">取消</el-button>
          <el-button type="primary" @click="submitEdit">确定</el-button>
        </template>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { pmFasApi, materialApi } from '@/api/index'
import type { FasRecord, FasOrder, FasStep } from '@/types/models'

// ========== 字典 ==========
const RECORD_TYPES: { label: string; value: string }[] = [
  { label: '投料', value: 'FEED' },
  { label: '工时', value: 'WORK' },
  { label: '质检', value: 'CHECK' },
  { label: '不良', value: 'NG' },
]
const recordTypeTag = (v?: string) => {
  const map: Record<string, { label: string; type: any }> = {
    FEED:  { label: '投料', type: 'primary' },
    WORK:  { label: '工时', type: 'info' },
    CHECK: { label: '质检', type: 'success' },
    NG:    { label: '不良', type: 'danger' },
  }
  return map[v || ''] || { label: v || '-', type: 'info' }
}

// ========== 工具 ==========
const formatNum = (n: any, digits = 2) => {
  if (n === null || n === undefined || n === '') return '0'
  const x = Number(n)
  if (Number.isNaN(x)) return '0'
  return x.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: digits })
}

// ========== 搜索/列表 ==========
const q = reactive({
  pageNum: 1, pageSize: 20,
  orderNo: '', recordType: '' as string | undefined, operator: ''
})
const list = reactive({ loading: false, data: { rows: [] as FasRecord[], total: 0 } })
const orderOptions = ref<FasOrder[]>([])
const orderMap = computed(() => {
  const m = new Map<number, FasOrder>()
  orderOptions.value.forEach(e => { if (e.orderId) m.set(e.orderId, e) })
  return m
})
const loadList = async () => {
  list.loading = true
  try {
    const r = await pmFasApi.recordList({ ...q })
    list.data = { rows: r?.rows || [], total: r?.total || 0 }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '查询失败')
  } finally { list.loading = false }
}
const resetQ = () => {
  q.pageNum = 1; q.pageSize = 20
  q.orderNo = ''; q.recordType = undefined; q.operator = ''
  loadList()
}
const loadOrders = async () => {
  try {
    const r = await pmFasApi.orderList({ pageNum: 1, pageSize: 500 })
    orderOptions.value = r?.rows || []
  } catch {}
}

// ========== 新增 ==========
const editDlg = reactive<{ show: boolean; form: Partial<FasRecord> }>({ show: false, form: {} })
const editFormRef = ref<FormInstance>()
const stepOptions = ref<FasStep[]>([])
const editRules: FormRules = {
  orderId:     [{ required: true, message: '请选择工单', trigger: 'change' }],
  recordType:  [{ required: true, message: '请选择记录类型', trigger: 'change' }],
  operator:    [{ required: true, message: '请输入执行人', trigger: 'blur' }],
  eventTime:   [{ required: true, message: '请选择事件时间', trigger: 'change' }],
}
const onOrderChange = async (orderId: number) => {
  editDlg.form.stepId = undefined
  if (!orderId) { stepOptions.value = []; return }
  try { stepOptions.value = (await pmFasApi.stepByOrder(orderId)) || [] }
  catch { stepOptions.value = [] }
}
const openAdd = () => {
  editDlg.form = {
    recordType: 'FEED',
    eventTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    qtyUsed: 0, costAmount: 0
  }
  stepOptions.value = []
  editDlg.show = true
}
const submitEdit = async () => {
  const ok = await editFormRef.value?.validate().catch(() => false)
  if (!ok) return
  try {
    await pmFasApi.recordAdd(editDlg.form)
    ElMessage.success('新增记录成功')
    editDlg.show = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e?.message || '保存失败')
  }
}
const handleRemove = async (row: FasRecord) => {
  try {
    await ElMessageBox.confirm(`确认删除该过程记录？`, '删除确认', { type: 'warning' })
    await pmFasApi.recordRemove(row.recordId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.msg || e?.message || '删除失败')
  }
}

// ========== 选项加载 ==========
const materialOptions = ref<any[]>([])
async function loadOptions() {
  try {
    const m = await materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    materialOptions.value = m?.rows || []
  } catch { /* ignore */ }
}

onMounted(() => { loadList(); loadOrders(); loadOptions() })
</script>

<style scoped>
.fas-page { display: flex; flex-direction: column; gap: 14px; }
.search-card :deep(.el-form-item) { margin-bottom: 0; }
.list-card .list-header { display: flex; align-items: center; justify-content: space-between; }
.list-card .list-header .title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-card .list-header .title .dot { width:4px; height:16px; border-radius: 2px; background: var(--el-color-primary); display:inline-block; }
.pager { margin-top: 14px; justify-content: flex-end; display: flex; }
</style>
