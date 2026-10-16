<template>
  <div class="app-container">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="发票号"><el-input v-model="query.invoiceNo" placeholder="发票号" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" filterable clearable placeholder="选择客户" style="width:180px">
          <el-option v-for="c in customers" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="票种">
        <el-select v-model="query.invoiceType" clearable placeholder="票种" style="width:120px">
          <el-option label="专票" value="1" /><el-option label="普票" value="2" />
          <el-option label="电子专票" value="3" /><el-option label="电子普票" value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="状态" style="width:110px">
          <el-option label="草稿" :value="0" /><el-option label="已开" :value="1" /><el-option label="作废" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="开票日期">
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="-" start-placeholder="开始" end-placeholder="结束" style="width:240px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:invoice:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增开票单</el-button>
      <el-button v-hasPermi="'sal:invoice:export'" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="invoiceNo" label="发票号" width="170" />
      <el-table-column label="票种" width="100">
        <template #default="{ row }">{{ typeText(row.invoiceType) }}</template>
      </el-table-column>
      <el-table-column prop="customerName" label="客户" min-width="180" show-overflow-tooltip />
      <el-table-column prop="invoiceDate" label="开票日期" width="110" />
      <el-table-column prop="totalAmount" label="价税合计" width="120" align="right">
        <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="buyerName" label="购方名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status===1?'success':row.status===2?'info':'warning'" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="issueBy" label="开票人" width="100" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="onView(row)">详情</el-button>
          <el-button link type="primary" v-if="row.status===0" v-hasPermi="'sal:invoice:edit'" @click="openDialog(row)">编辑</el-button>
          <el-button link type="success" v-if="row.status===0" v-hasPermi="'sal:invoice:issue'" @click="onIssue(row)">确认开票</el-button>
          <el-button link type="danger" v-if="row.status===1" v-hasPermi="'sal:invoice:cancel'" @click="onCancel(row)">作废</el-button>
          <el-button link type="danger" v-if="row.status===0" v-hasPermi="'sal:invoice:remove'" @click="onRemove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination class="pagination" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next, jumper" @size-change="loadList" @current-change="loadList" />

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialog" :title="form.invoiceId? '编辑开票单' : '新增开票单'" width="900px" destroy-on-close>
      <el-form :model="form" label-width="100px" class="form-grid">
        <el-form-item label="发票号" v-if="form.invoiceId"><el-input v-model="form.invoiceNo" disabled /></el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width:100%" @change="onCustomerChange">
            <el-option v-for="c in customers" :key="c.customerId" :label="c.customerName" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="票种" required>
          <el-select v-model="form.invoiceType" style="width:100%">
            <el-option label="专票" value="1" /><el-option label="普票" value="2" />
            <el-option label="电子专票" value="3" /><el-option label="电子普票" value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票日期" required>
          <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="购方名称"><el-input v-model="form.buyerName" placeholder="购方名称(可填客户名)" /></el-form-item>
        <el-form-item label="购方税号"><el-input v-model="form.buyerTaxNo" /></el-form-item>
        <el-form-item label="购方地址电话"><el-input v-model="form.buyerAddrPhone" /></el-form-item>
        <el-form-item label="购方开户行账号"><el-input v-model="form.buyerBankAccount" /></el-form-item>
        <el-form-item label="备注" class="full-row"><el-input v-model="form.remark" type="textarea" :rows="1" /></el-form-item>
      </el-form>

      <div class="line-toolbar">
        <span class="title">开票明细(关联应收单)</span>
        <el-button type="primary" size="small" @click="showPickDialog" :disabled="!form.customerId">
          <el-icon><Plus /></el-icon> 从可开票应收选择
        </el-button>
        <span class="summary">价税合计: <b>{{ fmt(formTotal) }}</b></span>
      </div>
      <el-table :data="lines" border size="small">
        <el-table-column prop="receivableNo" label="应收单号" min-width="160" />
        <el-table-column label="可开票余额" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.balance) }}</template>
        </el-table-column>
        <el-table-column label="本次开票金额" width="180">
          <template #default="{ row, $index }">
            <el-input-number v-model="row.amount" :min="0" :precision="2" :max="Number(row.balance)" controls-position="right" style="width:150px" @change="recalcLine(row,$index)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ $index }"><el-button link type="danger" @click="lines.splice($index,1)">移除</el-button></template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">{{ form.invoiceId? '保存' : '新增' }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="viewDialog" title="开票单详情" width="900px" destroy-on-close>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="发票号">{{ viewForm.invoiceNo }}</el-descriptions-item>
        <el-descriptions-item label="票种">{{ typeText(viewForm.invoiceType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(viewForm.status) }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ viewForm.customerName }}</el-descriptions-item>
        <el-descriptions-item label="开票日期">{{ viewForm.invoiceDate }}</el-descriptions-item>
        <el-descriptions-item label="价税合计">{{ fmt(viewForm.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="购方名称">{{ viewForm.buyerName }}</el-descriptions-item>
        <el-descriptions-item label="购方税号">{{ viewForm.buyerTaxNo }}</el-descriptions-item>
        <el-descriptions-item label="购方开户行">{{ viewForm.buyerBankAccount }}</el-descriptions-item>
        <el-descriptions-item label="开票人">{{ viewForm.issueBy }}</el-descriptions-item>
        <el-descriptions-item label="开票时间">{{ viewForm.issueTime }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ viewForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="viewLines" border size="small" class="mt-2">
        <el-table-column prop="receivableNo" label="应收单号" min-width="160" />
        <el-table-column prop="amount" label="开票金额" width="150" align="right">
          <template #default="{ row }">{{ fmt(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 可开票应收选择对话框 -->
    <el-dialog v-model="pickDialog" title="选择可开票应收单" width="720px" destroy-on-close>
      <el-table :data="invoiceable" border size="small" @selection-change="onPickChange">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="receivableNo" label="应收单号" min-width="150" />
        <el-table-column prop="bizDate" label="业务日期" width="110" />
        <el-table-column prop="bizType" label="业务类型" width="100" />
        <el-table-column prop="totalAmount" label="单据金额" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="balance" label="可开票余额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.balance) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pickDialog=false">取消</el-button>
        <el-button type="primary" @click="confirmPick">加入明细</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download } from '@element-plus/icons-vue'
import { salInvoiceApi, salCustomerApi } from '@/api'
import type { SalInvoice, SalInvoiceLine, InvoiceableReceivable, SalCustomer } from '@/types/models'

const query = reactive<any>({ pageNum: 1, pageSize: 20, invoiceNo: '', customerId: undefined, invoiceType: '', status: undefined })
const dateRange = ref<string[]>([])
const list = ref<SalInvoice[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<SalCustomer[]>([])
const exporting = ref(false)

const dialog = ref(false)
const submitting = ref(false)
const form = reactive<any>({})
const lines = ref<any[]>([])
const formTotal = computed(() => lines.value.reduce((s, l) => s + Number(l.amount || 0), 0))

const viewDialog = ref(false)
const viewForm = ref<any>({})
const viewLines = ref<any[]>([])

const pickDialog = ref(false)
const invoiceable = ref<InvoiceableReceivable[]>([])
const picked = ref<InvoiceableReceivable[]>([])

const fmt = (n: any) => Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const typeText = (t: any) => ({ '1': '专票', '2': '普票', '3': '电子专票', '4': '电子普票' }[t as string] || t || '')
const statusText = (s: any) => ({ 0: '草稿', 1: '已开', 2: '作废' }[s as number] || s)

function formDefaults() {
  Object.assign(form, {
    invoiceId: undefined, invoiceNo: '', invoiceType: '2', customerId: undefined, customerCode: '', customerName: '',
    invoiceDate: new Date().toISOString().slice(0, 10), currency: 'CNY', exchangeRate: 1,
    buyerName: '', buyerTaxNo: '', buyerAddrPhone: '', buyerBankAccount: '',
    undiscAmount: 0, taxAmount: 0, totalAmount: 0, cnyTotalAmount: 0, remark: ''
  })
  lines.value = []
}

async function loadList() {
  loading.value = true
  try {
    const [dFrom, dTo] = dateRange.value || []
    const r: any = await salInvoiceApi.list({ ...query, dateFrom: dFrom, dateTo: dTo })
    const d = r?.data || r
    list.value = d?.rows || []
    total.value = d?.total || 0
  } catch (e: any) { ElMessage.error(e?.message || '查询失败') } finally { loading.value = false }
}
function handleQuery() { query.pageNum = 1; loadList() }
function resetQuery() { Object.assign(query, { invoiceNo: '', customerId: undefined, invoiceType: '', status: undefined }); dateRange.value = []; handleQuery() }

function openDialog(row?: any) {
  formDefaults()
  if (row?.invoiceId) {
    Object.assign(form, row)
    salInvoiceApi.get(row.invoiceId).then((r: any) => {
      const d = r?.data || r
      lines.value = (d?.lines || []).map((l: any) => ({ ...l, balance: Number(l.amount) }))
    })
  }
  dialog.value = true
}
function onCustomerChange(val: any) {
  const c = customers.value.find(x => x.customerId === val)
  if (c) { form.customerCode = c.customerCode; form.customerName = c.customerName; form.buyerName = c.customerName }
  lines.value = []
}

async function showPickDialog() {
  if (!form.customerId) { ElMessage.warning('请先选客户'); return }
  try {
    const r: any = await salInvoiceApi.invoiceable(form.customerId)
    invoiceable.value = r?.data || r || []
    // 已选的应收单不再出现
    const exist = new Set(lines.value.map(l => l.receivableId))
    invoiceable.value = invoiceable.value.filter((x: any) => !exist.has(x.receivableId))
    pickDialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '查询可开票应收失败') }
}
function onPickChange(sel: InvoiceableReceivable[]) { picked.value = sel }
function confirmPick() {
  for (const p of picked.value) {
    lines.value.push({ receivableId: p.receivableId, receivableNo: p.receivableNo, balance: p.balance, amount: Number(p.balance) })
  }
  pickDialog.value = false
}
function recalcLine(_row: any, _i: number) { /* 金额变化时汇总自动算 */ }

async function onSubmit() {
  if (!form.customerId) { ElMessage.warning('请选客户'); return }
  if (!form.invoiceDate) { ElMessage.warning('请选开票日期'); return }
  if (!lines.value.length) { ElMessage.warning('请添加开票明细'); return }
  for (const l of lines.value) {
    if (!l.amount || Number(l.amount) <= 0) { ElMessage.warning('开票金额必须大于0'); return }
    if (Number(l.amount) > Number(l.balance)) { ElMessage.warning(`应收单 ${l.receivableNo} 开票金额超过可开票余额`); return }
  }
  submitting.value = true
  try {
    const submitLines = lines.value.map(l => ({ receivableId: l.receivableId, receivableNo: l.receivableNo, amount: Number(l.amount) }))
    if (form.invoiceId) {
      await salInvoiceApi.edit({ ...form }, submitLines)
      ElMessage.success('保存成功')
    } else {
      await salInvoiceApi.add({ ...form }, submitLines)
      ElMessage.success('新增成功')
    }
    dialog.value = false
    loadList()
  } catch (e: any) { ElMessage.error(e?.message || '保存失败') } finally { submitting.value = false }
}

async function onView(row: any) {
  try {
    const r: any = await salInvoiceApi.get(row.invoiceId)
    const d = r?.data || r
    viewForm.value = d?.invoice || {}
    viewLines.value = d?.lines || []
    viewDialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}

async function onIssue(row: any) {
  try {
    await ElMessageBox.confirm(`确认开票 ${row.invoiceNo}？将回写应收单已开票金额`, '提示', { type: 'warning' })
    await salInvoiceApi.issue(row.invoiceId)
    ElMessage.success('开票成功')
    loadList()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || '开票失败') }
}
async function onCancel(row: any) {
  try {
    await ElMessageBox.confirm(`作废发票 ${row.invoiceNo}？将回退应收单已开票金额`, '提示', { type: 'warning' })
    await salInvoiceApi.cancel(row.invoiceId)
    ElMessage.success('作废成功')
    loadList()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || '作废失败') }
}
async function onRemove(row: any) {
  try {
    await ElMessageBox.confirm(`删除开票单 ${row.invoiceNo}？`, '提示', { type: 'warning' })
    await salInvoiceApi.remove(row.invoiceId)
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e?.message || '删除失败') }
}

async function doExport() {
  exporting.value = true
  try {
    const [dFrom, dTo] = dateRange.value || []
    const resp: any = await salInvoiceApi.export({ ...query, dateFrom: dFrom, dateTo: dTo })
    const blob = resp?.data || resp
    const buf = blob instanceof Blob ? blob : new Blob([blob as any])
    const url = URL.createObjectURL(buf)
    const a = document.createElement('a'); a.href = url; a.download = '开票单.xlsx'; a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') } finally { exporting.value = false }
}

onMounted(async () => {
  try {
    const r: any = await salCustomerApi.list({ pageNum: 1, pageSize: 1000 })
    customers.value = r?.data?.rows || r?.rows || []
  } catch { customers.value = [] }
  loadList()
})
</script>

<style scoped>
.search-form { margin-bottom: 12px }
.table-toolbar { margin-bottom: 12px; display: flex; align-items: center; gap: 8px }
.pagination { margin-top: 12px; justify-content: flex-end; display: flex }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px }
.form-grid :deep(.full-row) { grid-column: 1 / -1 }
.line-toolbar { display: flex; align-items: center; gap: 12px; margin: 16px 0 8px }
.line-toolbar .title { font-weight: 600 }
.line-toolbar .summary { margin-left: auto; color: #606266 }
.mt-2 { margin-top: 12px }
</style>
