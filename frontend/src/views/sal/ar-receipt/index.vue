<template>
  <div class="page-sal-ar-receipt">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="收款单号"><el-input v-model="query.receiptNo" placeholder="收款单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width:220px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="收款日期"><el-date-picker v-model="query.receiptDate" type="date" value-format="YYYY-MM-DD" placeholder="YYYY-MM-DD" style="width:160px" /></el-form-item>
      <el-form-item label="应收单据">
        <el-select v-model="query.receivableId" placeholder="全部应收单" clearable filterable style="width:240px">
          <el-option v-for="r in receivables" :key="r.receivableId" :label="`${r.receivableNo || ''} ${r.customerName || ''} ${fmt(r.totalAmount)}`" :value="r.receivableId" />
        </el-select>
      </el-form-item>
      <el-form-item label="收款计划">
        <el-select v-model="query.planId" placeholder="全部计划" clearable filterable style="width:220px">
          <el-option v-for="p in plans" :key="p.planId" :label="`${p.planNo || ''} ${p.customerName || ''} ${fmt(p.planAmount)}`" :value="p.planId" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:150px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:arreceipt:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增收款单</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="receiptNo" label="收款单号" width="180" />
      <el-table-column prop="customerName" label="客户" width="180" show-overflow-tooltip />
      <el-table-column prop="payerName" label="付款方" width="160" show-overflow-tooltip />
      <el-table-column prop="receiptDate" label="收款日期" width="110" />
      <el-table-column prop="receiptCurrency" label="币种" width="70" align="center" />
      <el-table-column prop="totalAmount" label="收款金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="cnyTotalAmount" label="本币金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.cnyTotalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="feeAmount" label="手续费" width="110" align="right">
        <template #default="{ row }">{{ fmt(row.feeAmount) }}</template>
      </el-table-column>
      <el-table-column prop="recvChannel" label="收款渠道" width="110" show-overflow-tooltip />
      <el-table-column prop="planId" label="计划ID" width="90" align="right" />
      <el-table-column prop="receivableId" label="应收单ID" width="100" align="right" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="500" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'sal:arreceipt:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前收款单？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:arreceipt:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in actions(row)" :key="a.key">
            <el-tooltip v-if="a.disabled" :content="a.disabledReason" placement="top">
              <el-button size="small" link disabled>{{ a.label }}</el-button>
            </el-tooltip>
            <el-button v-else v-hasPermi="[a.permission]" size="small" link :type="a.type" @click="a.onClick?.(row)">{{ a.label }}</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :total="total" :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)" @current-change="loadList()" />
    </div>

    <!-- 主单弹窗 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="780px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="收款单号" prop="receiptNo"><el-input v-model="form.receiptNo" placeholder="留空自动生成" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户" required prop="customerId">
            <el-select v-model="form.customerId" placeholder="选择客户" filterable style="width:100%" @change="onCustomerChange">
              <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
            </el-select></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款方"><el-input v-model="form.payerName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="应收单据" prop="receivableId">
            <el-select v-model="form.receivableId" placeholder="选择应收单" clearable filterable style="width:100%">
              <el-option v-for="r in receivables" :key="r.receivableId" :label="`${r.receivableNo || ''} ${r.customerName || ''} ${fmt(r.totalAmount)}`" :value="r.receivableId" />
            </el-select></el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="收款计划" prop="planId">
            <el-select v-model="form.planId" placeholder="选择收款计划" clearable filterable style="width:100%">
              <el-option v-for="p in plans" :key="p.planId" :label="`${p.planNo || ''} ${p.customerName || ''} ${fmt(p.planAmount)}`" :value="p.planId" />
            </el-select></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="币种" prop="receiptCurrency"><el-input v-model="form.receiptCurrency" placeholder="CNY" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="汇率"><el-input-number v-model="form.exchangeRate" :precision="6" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="收款日期" required prop="receiptDate"><el-date-picker v-model="form.receiptDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="收款金额" required prop="totalAmount"><el-input-number v-model="form.totalAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="本币金额"><el-input-number v-model="form.cnyTotalAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="手续费"><el-input-number v-model="form.feeAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="付款银行"><el-input v-model="form.payerBank" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="付款账号"><el-input v-model="form.payerAccount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="SWIFT"><el-input v-model="form.payerSwift" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="收款银行"><el-input v-model="form.recvBankName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="收款账号"><el-input v-model="form.recvBankAccount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="收款渠道"><el-input v-model="form.recvChannel" placeholder="银行/支付宝/微信" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="水单号"><el-input v-model="form.waterNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发票号"><el-input v-model="form.invoiceNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发票金额"><el-input-number v-model="form.invoiceAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="费用承担方"><el-input v-model="form.feeBearer" placeholder="OUR/SHA/BEN" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态">
            <el-tag v-if="typeof form.status === 'number'" :type="statusMap[form.status]?.t || ''">
              {{ statusMap[form.status]?.l ?? form.status }}
            </el-tag>
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 操作弹窗：失败原因/作废原因 -->
    <el-dialog v-model="reasonDialog" :title="reasonTitle" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item :label="reasonLabel">
          <el-input v-model="reasonText" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reasonDialog = false">取消</el-button>
        <el-button type="primary" :loading="reasonLoading" @click="onReasonSubmit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { salArReceiptApi, salArReceivableApi, salArPlanApi, salCustomerApi, type SalArReceiptListQ } from '@/api'
import type { SalArReceipt, SalArReceivable, SalArReceiptPlan, SalCustomer } from '@/types/models'

defineOptions({ name: 'SalArReceiptPage' })

function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }

const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '草稿', t: 'info' },
  1: { l: '已审批', t: 'warning' },
  2: { l: '收款成功', t: 'success' },
  3: { l: '收款失败', t: 'danger' },
  9: { l: '作废', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l }))
const statusMap = STATUS_LABEL

function canEdit(row: SalArReceipt)   { return [0].includes(Number(row.status)) }
function canRemove(row: SalArReceipt) { return [0].includes(Number(row.status)) }

// 业务动作配置
const ACTIONS = [
  { key: 'audit', label: '审批', type: 'primary', permission: 'sal:arreceipt:audit',
    allowFrom: [0], disabledReason: (s: number) => `仅「草稿」可审批（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'confirm', label: '确认收款', type: 'success', permission: 'sal:arreceipt:confirm',
    allowFrom: [1], disabledReason: (s: number) => `仅「已审批」可确认收款（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'fail', label: '收款失败', type: 'danger', permission: 'sal:arreceipt:fail',
    allowFrom: [1], disabledReason: (s: number) => `仅「已审批」可标记失败（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'cancel', label: '作废', type: 'danger', permission: 'sal:arreceipt:cancel',
    allowFrom: [0, 1], disabledReason: (s: number) => `仅「草稿/已审批」可作废（当前:${STATUS_LABEL[s]?.l ?? s}）` }
]
function actions(row: SalArReceipt) {
  const s = Number(row.status)
  return ACTIONS.map(a => {
    const enabled = a.allowFrom.includes(s)
    return {
      ...a,
      disabled: !enabled,
      disabledReason: enabled ? '' : a.disabledReason(s),
      onClick: (r: SalArReceipt) => onAction(a.key, r)
    }
  })
}

const query = reactive<SalArReceiptListQ>({
  pageNum: 1, pageSize: 20, receiptNo: '', customerId: undefined, customerName: '',
  status: undefined, receiptDate: '', planId: undefined, receivableId: undefined
})
const list = ref<SalArReceipt[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<SalCustomer[]>([])
const receivables = ref<SalArReceivable[]>([])
const plans = ref<SalArReceiptPlan[]>([])

async function loadCustomers() {
  try {
    const resp = (await salCustomerApi.options()) as any
    customers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载客户失败') }
}

async function loadReceivablesAndPlans() {
  try {
    const [rResp, pResp] = await Promise.all([
      salArReceivableApi.list({ pageNum: 1, pageSize: 1000 } as any),
      salArPlanApi.list({ pageNum: 1, pageSize: 1000 } as any)
    ]) as any[]
    receivables.value = (rResp as any)?.rows || []
    plans.value = (pResp as any)?.rows || []
  } catch (e: any) { ElMessage.error(e?.message || '加载应收单/收款计划失败') }
}

function onCustomerChange(val: any) {
  const c = customers.value.find((x: any) => x.customerId === val)
  if (c) form.customerName = c.customerName
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await salArReceiptApi.list({ ...query })) as any
    list.value = resp?.rows || []; total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, {
    receiptNo: '', customerId: undefined, customerName: '', status: undefined,
    receiptDate: '', planId: undefined, receivableId: undefined, pageNum: 1
  })
  loadList(1)
}

// 主单弹窗
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.receiptId ? (readonly.value ? '收款单详情' : '编辑收款单') : '新增收款单')
const form = reactive<Partial<SalArReceipt>>({
  receiptNo: '', customerId: undefined as any, customerName: '', payerName: '',
  payerBank: '', payerAccount: '', payerSwift: '', recvBankName: '', recvBankAccount: '',
  recvChannel: '', receiptDate: '', receiptCurrency: 'CNY', exchangeRate: 1,
  totalAmount: 0, cnyTotalAmount: 0, feeBearer: '', feeAmount: 0, waterNo: '',
  invoiceNo: '', invoiceAmount: 0, planId: undefined, receivableId: undefined,
  status: 0, remark: ''
})
const rules = {
  customerId:   [{ required: true, message: '客户ID必填', trigger: 'change' }],
  receiptDate:  [{ required: true, message: '收款日期必填', trigger: 'change' }],
  totalAmount:  [{ required: true, message: '收款金额必填', trigger: 'change' }]
} as any
function formDefaults() {
  Object.assign(form, {
    receiptId: undefined, receiptNo: '', customerId: undefined as any, customerName: '', payerName: '',
    payerBank: '', payerAccount: '', payerSwift: '', recvBankName: '', recvBankAccount: '',
    recvChannel: '', receiptDate: '', receiptCurrency: 'CNY', exchangeRate: 1,
    totalAmount: 0, cnyTotalAmount: 0, feeBearer: '', feeAmount: 0, waterNo: '',
    invoiceNo: '', invoiceAmount: 0, planId: undefined, receivableId: undefined,
    status: 0, remark: ''
  })
}
function openDialog(row?: SalArReceipt) {
  formDefaults()
  if (row?.receiptId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: SalArReceipt) {
  formDefaults()
  try {
    const detail = (await salArReceiptApi.get(row.receiptId!)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.receiptId) { await salArReceiptApi.edit(form as any); ElMessage.success('修改成功') }
    else { await salArReceiptApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: SalArReceipt) {
  try { await salArReceiptApi.remove(row.receiptId!); ElMessage.success('删除成功'); await loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// 业务动作执行
async function onAction(key: string, row: SalArReceipt) {
  // 失败/作废需要原因输入
  if (key === 'fail' || key === 'cancel') {
    openReasonDialog(key, row)
    return
  }
  // audit / confirm 直接执行
  try {
    await ElMessageBox.confirm(`确认执行「${ACTIONS.find(a => a.key === key)?.label}」操作？`, '操作确认', { type: 'warning' })
  } catch { return }
  try {
    const resp = (key === 'audit'
      ? await salArReceiptApi.audit(row.receiptId!)
      : await salArReceiptApi.confirm(row.receiptId!)) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    ElMessage.success('操作成功'); await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}

// 原因弹窗（fail/cancel）
const reasonDialog = ref(false)
const reasonLoading = ref(false)
const reasonText = ref('')
const reasonTitle = ref('')
const reasonLabel = ref('原因')
const reasonActionKey = ref('')
const reasonTarget = ref<SalArReceipt | null>(null)
function openReasonDialog(key: string, row: SalArReceipt) {
  reasonActionKey.value = key
  reasonTarget.value = row
  reasonText.value = ''
  if (key === 'fail') {
    reasonTitle.value = '标记收款失败'
    reasonLabel.value = '失败原因'
  } else {
    reasonTitle.value = '作废收款单'
    reasonLabel.value = '作废原因'
  }
  reasonDialog.value = true
}
async function onReasonSubmit() {
  if (!reasonTarget.value) return
  reasonLoading.value = true
  try {
    const resp = (reasonActionKey.value === 'fail'
      ? await salArReceiptApi.fail(reasonTarget.value.receiptId!, reasonText.value)
      : await salArReceiptApi.cancel(reasonTarget.value.receiptId!, reasonText.value)) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
    ElMessage.success('操作成功')
    reasonDialog.value = false
    await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  finally { reasonLoading.value = false }
}

onMounted(async () => {
  await Promise.all([loadCustomers(), loadReceivablesAndPlans()])
  await loadList()
})
</script>

<style scoped>
.page-sal-ar-receipt { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
