<template>
  <div class="page-scm-ap-payment">
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="付款单号"><el-input v-model="query.paymentNo" placeholder="付款单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商" clearable style="width:180px" /></el-form-item>
      <el-form-item label="付款日期"><el-input v-model="query.payDate" placeholder="YYYY-MM-DD" clearable style="width:160px" /></el-form-item>
      <el-form-item label="应付单据">
        <el-select v-model="query.payableId" filterable clearable placeholder="应付单据" style="width:200px">
          <el-option v-for="p in payables" :key="p.payableId" :label="`${p.payableNo || ''} ${p.supplierName || ''} ${p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : ''}`" :value="p.payableId">
            <span style="float:left">{{ p.payableNo }} {{ p.supplierName }}</span>
            <span style="float:right;color:#999;font-size:12px">{{ p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : '-' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="付款计划">
        <el-select v-model="query.planId" filterable clearable placeholder="付款计划" style="width:200px">
          <el-option v-for="p in plans" :key="p.planId" :label="`${p.planNo || ''} ${p.supplierName || ''} ${p.planAmount != null ? '¥' + Number(p.planAmount).toFixed(2) : ''}`" :value="p.planId">
            <span style="float:left">{{ p.planNo }} {{ p.supplierName }}</span>
            <span style="float:right;color:#999;font-size:12px">{{ p.planAmount != null ? '¥' + Number(p.planAmount).toFixed(2) : '-' }}</span>
          </el-option>
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

    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'scm:apayment:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增付款单</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="paymentNo" label="付款单号" width="200" />
      <el-table-column prop="supplierName" label="供应商" width="180" show-overflow-tooltip />
      <el-table-column prop="payDate" label="付款日期" width="110" />
      <el-table-column prop="payChannel" label="渠道" width="90" />
      <el-table-column prop="payCurrency" label="币种" width="70" align="center" />
      <el-table-column prop="totalAmount" label="付款金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="cnyTotalAmount" label="折本金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.cnyTotalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="feeAmount" label="手续费" width="100" align="right">
        <template #default="{ row }">{{ fmt(row.feeAmount) }}</template>
      </el-table-column>
      <el-table-column prop="waterNo" label="流水号" width="160" show-overflow-tooltip />
      <el-table-column prop="invoiceNo" label="发票号" width="140" show-overflow-tooltip />
      <el-table-column prop="payableId" label="应付ID" width="80" align="right" />
      <el-table-column prop="planPhaseNo" label="期次" width="70" align="right" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="failReason" label="失败原因" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="520" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'scm:apayment:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除该付款单？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:apayment:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in actions(row)" :key="a.key">
            <el-tooltip v-if="a.disabled" :content="a.disabledReason" placement="top">
              <el-button size="small" link disabled>{{ a.label }}</el-button>
            </el-tooltip>
            <el-button v-else size="small" link :type="a.type" @click="a.onClick?.(row)">{{ a.label }}</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
                     :total="total" :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)" @current-change="loadList()" />
    </div>

    <el-dialog v-model="dialog" :title="dialogTitle" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="付款单号"><el-input v-model="form.paymentNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款日期" required prop="payDate">
            <el-date-picker v-model="form.payDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="供应商" required prop="supplierId">
            <el-select v-model="form.supplierId" filterable clearable placeholder="选择供应商" style="width:100%">
              <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
                <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
              </el-option>
            </el-select></el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="供应商名称"><el-input v-model="form.supplierName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="币种"><el-input v-model="form.payCurrency" placeholder="CNY" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="汇率"><el-input-number v-model="form.payExchangeRate" :precision="6" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="渠道"><el-input v-model="form.payChannel" placeholder="银行转账/承兑/..." /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="应付总额" prop="totalAmount">
            <el-input-number v-model="form.totalAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="折本金额">
            <el-input-number v-model="form.cnyTotalAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="手续费">
            <el-input-number v-model="form.feeAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="应付单据">
            <el-select v-model="form.payableId" filterable clearable placeholder="应付单据" style="width:100%">
              <el-option v-for="p in payables" :key="p.payableId" :label="`${p.payableNo || ''} ${p.supplierName || ''} ${p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : ''}`" :value="p.payableId">
                <span style="float:left">{{ p.payableNo }} {{ p.supplierName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : '-' }}</span>
              </el-option>
            </el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="付款计划">
            <el-select v-model="form.planId" filterable clearable placeholder="付款计划" style="width:100%">
              <el-option v-for="p in plans" :key="p.planId" :label="`${p.planNo || ''} ${p.supplierName || ''} ${p.planAmount != null ? '¥' + Number(p.planAmount).toFixed(2) : ''}`" :value="p.planId">
                <span style="float:left">{{ p.planNo }} {{ p.supplierName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ p.planAmount != null ? '¥' + Number(p.planAmount).toFixed(2) : '-' }}</span>
              </el-option>
            </el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划期次"><el-input-number v-model="form.planPhaseNo" :precision="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">收款方</el-divider>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="收款单位"><el-input v-model="form.payeeName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开户行"><el-input v-model="form.payeeBank" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="账号"><el-input v-model="form.payeeAccount" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="SWIFT"><el-input v-model="form.payeeSwift" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="付款行"><el-input v-model="form.payBankName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款账号"><el-input v-model="form.payBankAccount" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="流水号"><el-input v-model="form.waterNo" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="发票号"><el-input v-model="form.invoiceNo" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="发票金额"><el-input-number v-model="form.invoiceAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
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
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { scmApPaymentApi, type ScmApPaymentListQ, scmSupplierApi, scmApPayableApi, scmApPlanApi } from '@/api'
import type { ScmApPayment } from '@/types/models'

defineOptions({ name: 'ScmApPaymentPage' })

function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '草稿', t: 'info' }, 1: { l: '已审批', t: 'primary' },
  2: { l: '付款成功', t: 'success' }, 3: { l: '付款失败', t: 'danger' }, 9: { l: '作废', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l }))
const statusMap = STATUS_LABEL
function canEdit(row: ScmApPayment) { return Number(row.status) === 0 }
function canRemove(row: ScmApPayment) { return Number(row.status) === 0 }

const ACTION_DEF = [
  { key: 'audit',   label: '审批',         type: 'primary', permission: 'scm:apayment:audit',   allowFrom: [0],
    disabledReason: (s: number) => `仅「草稿」可审批（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'confirm', label: '确认付款成功', type: 'success', permission: 'scm:apayment:confirm', allowFrom: [1, 3],
    disabledReason: (s: number) => `仅「已审批/付款失败」可标记成功（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'fail',    label: '标记失败',     type: 'warning', permission: 'scm:apayment:confirm', allowFrom: [1],
    disabledReason: (s: number) => `仅「已审批」可标记失败（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'cancel',  label: '作废/回滚',    type: 'danger',  permission: 'scm:apayment:cancel',  allowFrom: [0, 1, 2, 3],
    disabledReason: (s: number) => `作废（当前:${STATUS_LABEL[s]?.l ?? s}）` }
]
function actions(row: ScmApPayment) {
  const s = Number(row.status)
  return ACTION_DEF.map(a => {
    const enabled = a.allowFrom.includes(s)
    return {
      ...a, disabled: !enabled,
      disabledReason: enabled ? '' : a.disabledReason(s),
      onClick: async (r: ScmApPayment) => {
        try {
          if (a.key === 'fail' || a.key === 'cancel') {
            const { value } = await ElMessageBox.prompt(`请输入${a.key === 'fail' ? '失败原因' : '作废原因'}`, `${a.label}-${r.paymentNo}`, {
              confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: '选填', inputType: 'text'
            }).catch(() => ({ value: undefined }))
            if (a.key === 'fail') { const resp = (await scmApPaymentApi.fail(r.paymentId!, value)) as any; if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败') }
            else { const resp = (await scmApPaymentApi.cancel(r.paymentId!, value)) as any; if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败') }
          } else if (a.key === 'audit') {
            const resp = (await scmApPaymentApi.audit(r.paymentId!)) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          } else {
            const resp = (await scmApPaymentApi.confirm(r.paymentId!)) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          }
          ElMessage.success(a.label + '成功'); await loadList()
        } catch (e: any) {
          if (e === undefined || e === null) return
          ElMessage.error(e?.message || a.label + '失败')
        }
      }
    }
  })
}

const query = reactive<ScmApPaymentListQ>({ pageNum: 1, pageSize: 20, paymentNo: '', supplierId: undefined, supplierName: '', status: undefined, payDate: '', planId: undefined, payableId: undefined })
const list = ref<ScmApPayment[]>([])
const total = ref(0)
const loading = ref(false)

// 下拉选项数据
const suppliers = ref<any[]>([])
const payables = ref<any[]>([])
const plans = ref<any[]>([])
async function loadOptions() {
  try {
    const [s, p, pl] = await Promise.all([
      (async () => { try { const r = (await scmSupplierApi.options()) as any; return r?.data || r || [] } catch { return [] } })(),
      (async () => { try { const r = (await scmApPayableApi.list({ pageNum: 1, pageSize: 1000 })) as any; return (r as any)?.rows || [] } catch { return [] } })(),
      (async () => { try { const r = (await scmApPlanApi.list({ pageNum: 1, pageSize: 1000 })) as any; return (r as any)?.rows || [] } catch { return [] } })()
    ])
    suppliers.value = Array.isArray(s) ? s : []
    payables.value = Array.isArray(p) ? p : []
    plans.value = Array.isArray(pl) ? pl : []
  } catch { /* ignore */ }
}
async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmApPaymentApi.list({ ...query })) as any
    list.value = resp?.rows || []; total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { paymentNo: '', supplierId: undefined, supplierName: '', status: undefined, payDate: '', planId: undefined, payableId: undefined, pageNum: 1 })
  loadList(1)
}

const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.paymentId ? (readonly.value ? '付款单详情' : '编辑付款单') : '新增付款单')
const form = reactive<Partial<ScmApPayment>>({
  paymentNo: '', supplierId: undefined as any, supplierCode: '', supplierName: '',
  payeeName: '', payeeBank: '', payeeAccount: '', payeeSwift: '',
  payBankName: '', payBankAccount: '', payChannel: '',
  payDate: '', payCurrency: 'CNY', payExchangeRate: 1,
  totalAmount: 0, cnyTotalAmount: 0, feeBearer: 'PAYER', feeAmount: 0,
  waterNo: '', invoiceNo: '', invoiceAmount: 0,
  voucherId: undefined, planId: undefined, planPhaseNo: undefined, payableId: undefined,
  status: 0, remark: ''
})
function formDefaults() {
  Object.assign(form, {
    paymentId: undefined, paymentNo: '', supplierId: undefined as any, supplierCode: '', supplierName: '',
    payeeName: '', payeeBank: '', payeeAccount: '', payeeSwift: '',
    payBankName: '', payBankAccount: '', payChannel: '',
    payDate: '', payCurrency: 'CNY', payExchangeRate: 1,
    totalAmount: 0, cnyTotalAmount: 0, feeBearer: 'PAYER', feeAmount: 0,
    waterNo: '', invoiceNo: '', invoiceAmount: 0,
    voucherId: undefined, planId: undefined, planPhaseNo: undefined, payableId: undefined,
    status: 0, remark: ''
  })
}
function openDialog(row?: ScmApPayment) {
  formDefaults()
  if (row?.paymentId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: ScmApPayment) {
  formDefaults()
  try {
    const detail = (await scmApPaymentApi.get(row.paymentId!)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.paymentId) { await scmApPaymentApi.edit(form as any); ElMessage.success('修改成功') }
    else { await scmApPaymentApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: ScmApPayment) {
  try { await scmApPaymentApi.remove(row.paymentId!); ElMessage.success('删除成功'); await loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => { loadOptions(); loadList() })
</script>

<style scoped>
.page-scm-ap-payment { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
