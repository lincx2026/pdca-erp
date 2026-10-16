<template>
  <div class="page-sal-ar-plan">
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="计划号"><el-input v-model="query.planNo" placeholder="计划号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width:220px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="应收单据">
        <el-select v-model="query.receivableId" placeholder="全部应收单" clearable filterable style="width:240px">
          <el-option v-for="r in receivables" :key="r.receivableId" :label="`${r.receivableNo || ''} ${r.customerName || ''} ${fmt(r.totalAmount)}`" :value="r.receivableId" />
        </el-select>
      </el-form-item>
      <el-form-item label="期次"><el-input-number v-model="query.phaseNo" :min="1" :precision="0" controls-position="right" style="width:120px" /></el-form-item>
      <el-form-item label="计划日期"><el-date-picker v-model="query.planDate" type="date" value-format="YYYY-MM-DD" style="width:160px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:140px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:arplan:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增收款计划</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="planNo" label="计划号" width="200" />
      <el-table-column prop="receivableId" label="应收单ID" width="100" align="right" />
      <el-table-column prop="customerName" label="客户" width="180" show-overflow-tooltip />
      <el-table-column prop="phaseNo" label="期次" width="70" align="center" />
      <el-table-column prop="planDate" label="计划日期" width="120" />
      <el-table-column prop="ratio" label="比例(%)" width="90" align="right" />
      <el-table-column prop="planAmount" label="计划金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.planAmount) }}</template>
      </el-table-column>
      <el-table-column prop="actualAmount" label="已收金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.actualAmount) }}</template>
      </el-table-column>
      <el-table-column prop="unpaidAmount" label="未收金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.unpaidAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'sal:arplan:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除该收款计划？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:arplan:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :total="total" :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)" @current-change="loadList()" />
    </div>

    <el-dialog v-model="dialog" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="计划号"><el-input v-model="form.planNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="应收单据" required prop="receivableId">
            <el-select v-model="form.receivableId" placeholder="选择应收单" clearable filterable style="width:100%">
              <el-option v-for="r in receivables" :key="r.receivableId" :label="`${r.receivableNo || ''} ${r.customerName || ''} ${fmt(r.totalAmount)}`" :value="r.receivableId" />
            </el-select></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="客户" required prop="customerId">
            <el-select v-model="form.customerId" placeholder="选择客户" filterable style="width:100%" @change="onCustomerChange">
              <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
            </el-select></el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="期次" prop="phaseNo"><el-input-number v-model="form.phaseNo" :min="1" :precision="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="比例(%)"><el-input-number v-model="form.ratio" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="计划日期" required prop="planDate"><el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划金额" required prop="planAmount"><el-input-number v-model="form.planAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-descriptions :column="3" border size="small" style="margin-bottom:8px;">
          <el-descriptions-item label="已收">{{ fmt(form.actualAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未收">{{ fmt(form.unpaidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof form.status === 'number'" :type="statusMap[form.status]?.t || ''">
              {{ statusMap[form.status]?.l ?? form.status }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { salArPlanApi, salArReceivableApi, salCustomerApi, type SalArPlanListQ } from '@/api'
import type { SalArReceiptPlan, SalArReceivable, SalCustomer } from '@/types/models'

defineOptions({ name: 'SalArPlanPage' })

function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '待收', t: 'info' }, 1: { l: '部分收', t: 'warning' },
  2: { l: '已收清', t: 'success' }, 3: { l: '收款中', t: 'primary' }, 9: { l: '作废', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l }))
const statusMap = STATUS_LABEL
function canEdit(row: SalArReceiptPlan) { return [0, 1].includes(Number(row.status)) }
function canRemove(row: SalArReceiptPlan) { return Number(row.status) === 0 }

const query = reactive<SalArPlanListQ>({
  pageNum: 1, pageSize: 20, planNo: '', receivableId: undefined, customerId: undefined,
  customerName: '', status: undefined, planDate: '', phaseNo: undefined
})
const list = ref<SalArReceiptPlan[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<SalCustomer[]>([])
const receivables = ref<SalArReceivable[]>([])

async function loadCustomers() {
  try {
    const resp = (await salCustomerApi.options()) as any
    customers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载客户失败') }
}

async function loadReceivables() {
  try {
    const resp = (await salArReceivableApi.list({ pageNum: 1, pageSize: 1000 } as any)) as any
    receivables.value = resp?.rows || []
  } catch (e: any) { ElMessage.error(e?.message || '加载应收单失败') }
}

function onCustomerChange(val: any) {
  const c = customers.value.find((x: any) => x.customerId === val)
  if (c) form.customerName = c.customerName
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await salArPlanApi.list({ ...query })) as any
    list.value = resp?.rows || []; total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { planNo: '', receivableId: undefined, customerId: undefined, customerName: '', status: undefined, planDate: '', phaseNo: undefined, pageNum: 1 })
  loadList(1)
}

const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.planId ? (readonly.value ? '收款计划详情' : '编辑收款计划') : '新增收款计划')
const form = reactive<Partial<SalArReceiptPlan>>({
  planNo: '', receivableId: undefined as any, customerId: undefined as any, customerName: '',
  phaseNo: 1, ratio: 0, planDate: '', planAmount: 0,
  actualAmount: 0, unpaidAmount: 0, status: 0, remark: ''
})
function formDefaults() {
  Object.assign(form, {
    planId: undefined, planNo: '', receivableId: undefined as any, customerId: undefined as any, customerName: '',
    phaseNo: 1, ratio: 0, planDate: '', planAmount: 0,
    actualAmount: 0, unpaidAmount: 0, status: 0, remark: ''
  })
}
function openDialog(row?: SalArReceiptPlan) {
  formDefaults()
  if (row?.planId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: SalArReceiptPlan) {
  formDefaults()
  try {
    const detail = (await salArPlanApi.get(row.planId!)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.planId) { await salArPlanApi.edit(form as any); ElMessage.success('修改成功') }
    else { await salArPlanApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: SalArReceiptPlan) {
  try { await salArPlanApi.remove(row.planId!); ElMessage.success('删除成功'); await loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(async () => {
  await Promise.all([loadCustomers(), loadReceivables()])
  await loadList()
})
</script>

<style scoped>
.page-sal-ar-plan { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
