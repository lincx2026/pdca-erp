<template>
  <div class="page-scm-ap-plan">
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="计划号"><el-input v-model="query.planNo" placeholder="计划号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商" clearable style="width:180px" /></el-form-item>
      <el-form-item label="应付单据">
        <el-select v-model="query.payableId" filterable clearable placeholder="应付单据" style="width:200px">
          <el-option v-for="p in payables" :key="p.payableId" :label="`${p.payableNo || ''} ${p.supplierName || ''} ${p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : ''}`" :value="p.payableId">
            <span style="float:left">{{ p.payableNo }} {{ p.supplierName }}</span>
            <span style="float:right;color:#999;font-size:12px">{{ p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : '-' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="期次"><el-input-number v-model="query.phaseNo" :min="1" :precision="0" controls-position="right" style="width:120px" /></el-form-item>
      <el-form-item label="计划日期"><el-input v-model="query.planDate" placeholder="YYYY-MM-DD" clearable style="width:160px" /></el-form-item>
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
      <el-button type="primary" v-hasPermi="'scm:apayplan:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增计划</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="planNo" label="计划号" width="200" />
      <el-table-column prop="payableNo" label="应付单号" width="180" />
      <el-table-column prop="supplierName" label="供应商" width="180" show-overflow-tooltip />
      <el-table-column prop="phaseNo" label="期次" width="70" align="center" />
      <el-table-column prop="phaseName" label="名称" width="140" />
      <el-table-column prop="ratio" label="比例(%)" width="90" align="right" />
      <el-table-column prop="planDate" label="计划日期" width="120" />
      <el-table-column prop="planAmount" label="计划金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.planAmount) }}</template>
      </el-table-column>
      <el-table-column prop="actualAmount" label="已付金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.actualAmount) }}</template>
      </el-table-column>
      <el-table-column prop="unpaidAmount" label="未付金额" width="130" align="right">
        <template #default="{ row }">{{ fmt(row.unpaidAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="flowNo" label="流水号" width="160" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'scm:apayplan:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除该付款计划？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:apayplan:remove'" size="small" link type="danger">删除</el-button></template>
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
          <el-col :span="12"><el-form-item label="应付单据" required prop="payableId">
            <el-select v-model="form.payableId" filterable clearable placeholder="应付单据" style="width:100%">
              <el-option v-for="p in payables" :key="p.payableId" :label="`${p.payableNo || ''} ${p.supplierName || ''} ${p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : ''}`" :value="p.payableId">
                <span style="float:left">{{ p.payableNo }} {{ p.supplierName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ p.totalAmount != null ? '¥' + Number(p.totalAmount).toFixed(2) : '-' }}</span>
              </el-option>
            </el-select></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="应付单号"><el-input v-model="form.payableNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商" required prop="supplierId">
            <el-select v-model="form.supplierId" filterable clearable placeholder="选择供应商" style="width:100%">
              <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
                <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ s.contact }} {{ s.phone }}</span>
              </el-option>
            </el-select></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="供应商名称"><el-input v-model="form.supplierName" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="期次" prop="phaseNo"><el-input-number v-model="form.phaseNo" :min="1" :precision="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="分期名称"><el-input v-model="form.phaseName" placeholder="如 首款 30%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="比例(%)"><el-input-number v-model="form.ratio" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划日期" required prop="planDate"><el-date-picker v-model="form.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="计划金额" required prop="planAmount"><el-input-number v-model="form.planAmount" :precision="2" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-descriptions :column="3" border size="small" style="margin-bottom:8px;">
          <el-descriptions-item label="已付">{{ fmt(form.actualAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未付">{{ fmt(form.unpaidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof form.status === 'number'" :type="statusMap[form.status]?.t || ''">
              {{ statusMap[form.status]?.l ?? form.status }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="付款行"><el-input v-model="form.payBankName" placeholder="付款银行" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款账号"><el-input v-model="form.payBankAccount" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="收款单位"><el-input v-model="form.payeeName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="开户行"><el-input v-model="form.payeeBank" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="账号/SWIFT"><el-input v-model="form.payeeAccount" /></el-form-item></el-col>
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
import { ElMessage, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { scmApPlanApi, type ScmApPlanListQ, scmSupplierApi, scmApPayableApi } from '@/api'
import type { ScmApPaymentPlan } from '@/types/models'

defineOptions({ name: 'ScmApPlanPage' })

function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '待付', t: 'info' }, 1: { l: '部分付', t: 'warning' },
  2: { l: '已付清', t: 'success' }, 3: { l: '付款中', t: 'primary' }, 9: { l: '作废', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l }))
const statusMap = STATUS_LABEL
function canEdit(row: ScmApPaymentPlan) { return [0, 1].includes(Number(row.status)) }
function canRemove(row: ScmApPaymentPlan) { return Number(row.status) === 0 }

const query = reactive<ScmApPlanListQ>({ pageNum: 1, pageSize: 20, planNo: '', payableId: undefined, supplierId: undefined, supplierName: '', status: undefined, planDate: '', phaseNo: undefined })
const list = ref<ScmApPaymentPlan[]>([])
const total = ref(0)
const loading = ref(false)

// 下拉选项数据
const suppliers = ref<any[]>([])
const payables = ref<any[]>([])
async function loadOptions() {
  try {
    const [s, p] = await Promise.all([
      (async () => { try { const r = (await scmSupplierApi.options()) as any; return r?.data || r || [] } catch { return [] } })(),
      (async () => { try { const r = (await scmApPayableApi.list({ pageNum: 1, pageSize: 1000 })) as any; return (r as any)?.rows || [] } catch { return [] } })()
    ])
    suppliers.value = Array.isArray(s) ? s : []
    payables.value = Array.isArray(p) ? p : []
  } catch { /* ignore */ }
}
async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmApPlanApi.list({ ...query })) as any
    list.value = resp?.rows || []; total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { planNo: '', payableId: undefined, supplierId: undefined, supplierName: '', status: undefined, planDate: '', phaseNo: undefined, pageNum: 1 })
  loadList(1)
}

const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.planId ? (readonly.value ? '付款计划详情' : '编辑付款计划') : '新增付款计划')
const form = reactive<Partial<ScmApPaymentPlan>>({
  planNo: '', payableId: undefined as any, payableNo: '', supplierId: undefined as any, supplierName: '',
  phaseNo: 1, phaseName: '', ratio: 0, planDate: '', planAmount: 0,
  actualAmount: 0, unpaidAmount: 0, status: 0,
  payBankName: '', payBankAccount: '', payeeName: '', payeeBank: '', payeeAccount: '', payeeSwift: '',
  flowNo: '', remark: ''
})
function formDefaults() {
  Object.assign(form, {
    planId: undefined, planNo: '', payableId: undefined as any, payableNo: '', supplierId: undefined as any, supplierName: '',
    phaseNo: 1, phaseName: '', ratio: 0, planDate: '', planAmount: 0,
    actualAmount: 0, unpaidAmount: 0, status: 0,
    payBankName: '', payBankAccount: '', payeeName: '', payeeBank: '', payeeAccount: '', payeeSwift: '',
    flowNo: '', remark: ''
  })
}
function openDialog(row?: ScmApPaymentPlan) {
  formDefaults()
  if (row?.planId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: ScmApPaymentPlan) {
  formDefaults()
  try {
    const detail = (await scmApPlanApi.get(row.planId!)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.planId) { await scmApPlanApi.edit(form as any); ElMessage.success('修改成功') }
    else { await scmApPlanApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: ScmApPaymentPlan) {
  try { await scmApPlanApi.remove(row.planId!); ElMessage.success('删除成功'); await loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

onMounted(() => { loadOptions(); loadList() })
</script>

<style scoped>
.page-scm-ap-plan { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>
