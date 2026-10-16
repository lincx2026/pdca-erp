<template>
  <div class="page-scm-ap-payable">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="应付单号"><el-input v-model="query.payableNo" placeholder="应付单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商" clearable style="width:180px" /></el-form-item>
      <el-form-item label="业务日期">
        <el-date-picker v-model="query.bizDateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="起始" end-placeholder="结束" style="width:240px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:150px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="发票状态">
        <el-select v-model="query.invoiceStatus" placeholder="全部" clearable style="width:140px">
          <el-option v-for="o in invoiceStatusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button v-hasPermi="'scm:ap:payable:invoice'" type="primary"
                 :disabled="!curRow || Number(curRow.status) < 0" @click="openInvoiceDlg(curRow)">
        <el-icon><Document /></el-icon> 发票登记
      </el-button>
      <el-button v-hasPermi="'scm:ap:payable:reconcile'" type="success"
                 :disabled="!curRow || Number(curRow.status) !== 0" @click="doReconcile(curRow)">
        <el-icon><DataLine /></el-icon> 对账(推凭证)
      </el-button>
      <el-button v-hasPermi="'scm:ap:payable:plan'" type="warning"
                 :disabled="!curRow" @click="openPlanDlg(curRow)">
        <el-icon><Calendar /></el-icon> 生成付款计划
      </el-button>
      <el-button v-hasPermi="'scm:ap:payable:export'" @click="doExport"><el-icon><Download /></el-icon> 导出Excel</el-button>
    </div>

    <!-- 主表 -->
    <el-table v-loading="loading" :data="list" stripe border
              highlight-current-row
              @current-change="(r:any) => curRow = r || null">
      <el-table-column prop="payableId" label="ID" width="70" align="right" />
      <el-table-column prop="payableNo" label="应付单号" width="170" />
      <el-table-column prop="supplier" label="供应商" width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.supplier || row.supplierName || '-' }}</template>
      </el-table-column>
      <el-table-column label="到期日(天)" width="110" align="right">
        <template #default="{ row }">
          <span v-if="row.dueDateDays != null" :class="Number(row.dueDateDays) < 0 ? 'c-red' : ''">
            {{ Number(row.dueDateDays).toLocaleString() }}天
          </span>
          <span v-else>{{ row.dueDate || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="undiscAmount" label="未税金额" width="130" align="right">
        <template #default="{ row }">{{ row.undiscAmount == null ? '-' : '¥' + Number(row.undiscAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="taxAmount" label="税额" width="120" align="right">
        <template #default="{ row }">{{ row.taxAmount == null ? '-' : '¥' + Number(row.taxAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="totalAmount" label="应付总额" width="130" align="right">
        <template #default="{ row }">
          <b>{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</b>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="voucherId" label="凭证号" width="140" />
      <el-table-column prop="invoiceNo" label="发票号码" width="150" />
      <el-table-column label="发票状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="typeof row.invoiceStatus === 'number' || row.invoiceStatus != null"
                  :type="invoiceStatusMap[row.invoiceStatus]?.t || ''">
            {{ invoiceStatusMap[row.invoiceStatus]?.l ?? String(row.invoiceStatus) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="reconcileBy" label="对账人" width="100" />
      <el-table-column label="对账时间" width="160">
        <template #default="{ row }">
          <span v-if="row.reconcileTime">{{ row.reconcileTime }}</span>
          <span v-else class="sub-gray">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="420" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button v-if="Number(row.status) === 0 || Number(row.status) === 2"
                     v-hasPermi="'scm:ap:payable:reconcile'" size="small" link type="success" @click="doReconcile(row)">推送凭证</el-button>
          <el-button v-else size="small" link type="success" disabled>推送凭证</el-button>
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-popconfirm v-if="Number(row.status) === 0" title="确认删除该草稿？" @confirm="onRemove(row)">
            <template #reference>
              <el-button v-hasPermi="'scm:ap:payable:remove'" size="small" link type="danger">删除草稿</el-button>
            </template>
          </el-popconfirm>
          <el-popconfirm v-else title="确认删除该草稿？">
            <template #reference>
              <el-button size="small" link type="danger" disabled>删除草稿</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper"
                     v-model:current-page="query.pageNum"
                     v-model:page-size="query.pageSize"
                     :total="total"
                     :page-sizes="[20, 50, 100]"
                     @size-change="loadList(1)"
                     @current-change="loadList()" />
    </div>

    <!-- ================== 明细（行） dialog ================== -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1180px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="应付单号">{{ linesOwner.payableNo || '-' }} #{{ linesOwner.payableId }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ linesOwner.supplier || linesOwner.supplierName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总额/未付">
            ¥{{ Number(linesOwner.totalAmount||0).toFixed(2) }} / ¥{{ Number(linesOwner.unpaidAmount ?? linesOwner.totalAmount ?? 0).toFixed(2) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof linesOwner.status === 'number'" :type="statusMap[linesOwner.status]?.t || ''">
              {{ statusMap[linesOwner.status]?.l || String(linesOwner.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">应付行 ({{ lines.length }})</el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="520">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="qty" label="数量" width="100" align="right">
          <template #default="{ row }">{{ Number(row.qty||0).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="110" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : '¥' + Number(row.unitPrice).toFixed(4) }}</template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="120" align="right">
          <template #default="{ row }">{{ row.amount == null ? '-' : '¥' + Number(row.amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="90" align="right">
          <template #default="{ row }">{{ row.taxRate == null ? '-' : (Number(row.taxRate) * 100).toFixed(0) + '%' }}</template>
        </el-table-column>
        <el-table-column prop="stockInId" label="入库单ID" width="100" align="right" />
        <el-table-column prop="stockInDate" label="入库日期" width="110" />
        <el-table-column prop="poId" label="PO-ID" width="80" align="right" />
        <el-table-column prop="poNo" label="PO单号" width="150" />
      </el-table>
      <template #footer><el-button @click="linesDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- ================== 发票登记 Dialog ================== -->
    <el-dialog v-model="invoiceDlg" title="发票登记" width="640px" top="10vh" destroy-on-close>
      <div v-if="curRow" style="margin-bottom:10px;">
        <el-tag type="primary">{{ curRow.payableNo || '-' }} #{{ curRow.payableId }}</el-tag>
        <span class="sub-gray ml-12">供应商: {{ curRow.supplier || curRow.supplierName || '-' }}</span>
      </div>
      <el-form ref="invoiceFormRef" :model="invoiceForm" :rules="invoiceRules" label-width="120px">
        <el-form-item label="发票号码" required prop="invoiceNo">
          <el-input v-model="invoiceForm.invoiceNo" maxlength="40" placeholder="增值税发票号码" />
        </el-form-item>
        <el-form-item label="开票日期" required prop="invoiceDate">
          <el-date-picker v-model="invoiceForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="税额" required prop="taxAmt">
              <el-input-number v-model="invoiceForm.taxAmt" :precision="2" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="不含税金额" required prop="exclusiveAmt">
              <el-input-number v-model="invoiceForm.exclusiveAmt" :precision="2" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="收票日期" prop="receivedDate">
          <el-date-picker v-model="invoiceForm.receivedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="发票状态" required prop="status">
          <el-select v-model="invoiceForm.status" style="width:100%">
            <el-option v-for="o in invoiceStatusOpts" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票附件" prop="img">
          <el-upload class="invoice-upload"
                     action="#"
                     :http-request="uploadInvoiceImg"
                     :show-file-list="true"
                     :limit="3"
                     accept="image/*,application/pdf"
                     list-type="picture">
            <el-button size="small" type="primary"><el-icon><Upload /></el-icon> 上传（返回url）</el-button>
            <template #tip><div style="color:var(--el-text-color-secondary);font-size:12px;">上传后文件写入 invoiceForm.img (多个则逗号分隔)。</div></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceDlg = false">取消</el-button>
        <el-button type="primary" :loading="invoiceSubmitting" @click="submitInvoice">保存发票登记</el-button>
      </template>
    </el-dialog>

    <!-- ================== 生成付款计划 Dialog ================== -->
    <el-dialog v-model="planDlg" title="生成付款计划" width="920px" top="6vh" destroy-on-close>
      <div v-if="curRow" style="margin-bottom:8px;">
        <el-tag type="primary">{{ curRow.payableNo || '-' }} #{{ curRow.payableId }}</el-tag>
        <span class="ml-12">应付总额: <b>¥{{ Number(curRow.totalAmount||0).toFixed(2) }}</b></span>
        <span class="ml-12 sub-gray">到期日: {{ curRow.dueDate || '-' }}</span>
      </div>
      <div class="plan-toolbar">
        <el-form :inline="true" :model="planHd" label-width="120px">
          <el-form-item label="分期数">
            <el-input-number v-model="planHd.phaseCount" :min="1" :max="10" size="default" @change="onPhaseCountChange" />
            <span class="sub-gray ml-8">(默认 3 期)</span>
          </el-form-item>
          <el-form-item label="基准日期">
            <el-date-picker v-model="planHd.baseDate" type="date" value-format="YYYY-MM-DD" size="default" />
            <div style="color:var(--el-text-color-secondary);font-size:12px;">每行 planDate = 基准 + planDateOffset(天)</div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="default" @click="evenDistribute">平均分配比例</el-button>
            <el-button size="default" @click="addPhaseRow">+追加一行</el-button>
          </el-form-item>
        </el-form>
      </div>
      <el-table :data="phases" border size="default">
        <el-table-column label="期次" width="80" align="center">
          <template #default="{ row, $index }">
            <b>{{ row.phaseNo ?? ($index + 1) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="期次名称" width="180">
          <template #default="{ row }"><el-input v-model="row.phaseName" placeholder="如 首付30%" /></template>
        </el-table-column>
        <el-table-column label="比例(%) *" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.ratio" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" @change="recomputeAmounts" />
          </template>
        </el-table-column>
        <el-table-column label="计划日偏移(天) *" width="170">
          <template #default="{ row }">
            <el-input-number v-model="row.planDateOffset" :precision="0" controls-position="right" style="width:100%" />
            <div class="sub-gray" v-if="planHd.baseDate">= {{ plusDays(planHd.baseDate, Number(row.planDateOffset||0)) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="计划金额(估)" min-width="140" align="right">
          <template #default="{ row }"><b class="c-blue">{{ '¥' + ((Number(row.ratio||0)/100) * Number(curRow?.totalAmount||0)).toFixed(2) }}</b></template>
        </el-table-column>
        <el-table-column label="备注" min-width="180">
          <template #default="{ row }"><el-input v-model="row.remark" maxlength="100" placeholder="如 发货验收后支付" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center" fixed="right">
          <template #default="{ $index }">
            <el-button v-if="phases.length > 1" link type="danger" size="small" @click="phases.splice($index,1); onPhaseCountChange();">×</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:8px; display:flex;justify-content:space-between;">
        <span>比例合计:
          <b :style="{ color: ratioSum === 100 ? 'var(--el-color-success)' : 'var(--el-color-danger)' }">{{ ratioSum.toFixed(2) }}%</b>
        </span>
        <span class="sub-gray">分期比例总和必须等于 100%（允许 0.01 误差）</span>
      </div>
      <template #footer>
        <el-button @click="planDlg = false">取消</el-button>
        <el-button type="primary" :loading="planSubmitting" :disabled="!canSubmitPlan" @click="submitPlan">保存付款计划（{{ phases.length }}期）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import {
  Search, RefreshLeft, Download, Document, DataLine, Calendar, Upload
} from '@element-plus/icons-vue'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmApPayablePage' })
const { proxy } = getCurrentInstance() as any
const userStore = useUserStore()
function hasPermOrThrow(p: string) { if (!hasPerm(p, userStore.userInfo?.permissions)) throw new Error('无此操作权限: ' + p); return true }

function todayStr() {
  const d = new Date(); const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}
function plusDays(ds: string, days: number) {
  if (!ds) return ''
  const d = new Date(ds); d.setDate(d.getDate() + days)
  const m = String(d.getMonth()+1).padStart(2,'0'); const day = String(d.getDate()).padStart(2,'0')
  return `${d.getFullYear()}-${m}-${day}`
}

// 状态机
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l:'草稿', t:'info' },
  1: { l:'对账中', t:'warning' },
  2: { l:'已对账', t:'primary' },
  3: { l:'部分付', t:'warning' },
  4: { l:'付款完成', t:'success' },
  9: { l:'作废', t:'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v,m])=>({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const invoiceStatusOpts = [
  { v: 0, l:'未登记', t:'info' },
  { v: 1, l:'待认证', t:'warning' },
  { v: 2, l:'已认证', t:'success' },
  { v: 9, l:'作废', t:'danger' }
]
const invoiceStatusMap: Record<any, { l: string; t?: string }> = {}
invoiceStatusOpts.forEach(o => invoiceStatusMap[o.v] = { l: o.l, t: o.t })

// 查询
const query = reactive<any>({
  pageNum: 1, pageSize: 20,
  payableNo: '', supplierName: '', bizDateRange: [] as string[],
  status: undefined as number|undefined, invoiceStatus: undefined as number|undefined
})
const list = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const curRow = ref<any>(null)

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const body: any = {
      pageNum: query.pageNum, pageSize: query.pageSize,
      payableNo: query.payableNo || undefined,
      supplierName: query.supplierName || undefined,
      status: query.status,
      invoiceStatus: query.invoiceStatus
    }
    if (Array.isArray(query.bizDateRange) && query.bizDateRange.length === 2) {
      body.startBizDate = query.bizDateRange[0]
      body.endBizDate = query.bizDateRange[1]
    }
    const resp = await proxy?.$request?.('/scm/ap/payable/list', 'POST', body)
    list.value = resp?.rows || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载失败')
    list.value = []; total.value = 0
  } finally { loading.value = false }
}
function resetQuery() {
  query.payableNo = ''; query.supplierName = ''
  query.bizDateRange = []; query.status = undefined; query.invoiceStatus = undefined
  loadList(1)
}

async function doExport() {
  try {
    await hasPermOrThrow('scm:ap:payable:export')
    const body: any = {
      pageNum: 1, pageSize: 99999,
      payableNo: query.payableNo || undefined,
      supplierName: query.supplierName || undefined,
      status: query.status, invoiceStatus: query.invoiceStatus
    }
    if (Array.isArray(query.bizDateRange) && query.bizDateRange.length === 2) {
      body.startBizDate = query.bizDateRange[0]; body.endBizDate = query.bizDateRange[1]
    }
    // 优先触发下载url
    try {
      const resp = await proxy?.$request?.('/scm/ap/payable/export', 'POST', body)
      const url = resp?.url || resp?.data?.url || resp?.data
      if (typeof url === 'string' && /^https?:/.test(url)) {
        window.open(url, '_blank')
        proxy?.$modal?.msgSuccess?.('导出成功，请在新窗口下载') || ElMessage.success('导出成功，请在新窗口下载')
        return
      }
    } catch {}
    ElMessage.info('后端导出接口触发完成；如无文件下载，请联系管理员。')
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '导出失败')
  }
}

// ============== 行明细 ==============
const linesDialog = ref(false)
const linesOwner = ref<any>(null)
const lines = ref<any[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `应付行明细${linesOwner.value?.payableNo ? ' - ' + linesOwner.value.payableNo : ''}`)
async function onViewLines(row: any) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = await proxy?.$request?.(`/scm/ap/payable/${row.payableId}/lines`, 'POST', {})
    lines.value = (resp?.rows) || (resp?.data?.rows) || []
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '加载明细失败') }
  finally { linesLoading.value = false }
}
async function onView(row: any) { onViewLines(row) }
async function onRemove(row: any) {
  if (!row?.payableId) return
  try {
    await hasPermOrThrow('scm:ap:payable:remove')
    const resp = await proxy?.$request?.(`/scm/ap/payable/${row.payableId}/remove`, 'POST', {})
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '删除失败')
    proxy?.$modal?.msgSuccess?.('删除草稿成功') || ElMessage.success('删除草稿成功'); await loadList()
  } catch (e: any) { proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '删除失败') }
}

// ============== 发票登记 Dialog ==============
const invoiceDlg = ref(false)
const invoiceSubmitting = ref(false)
const invoiceFormRef = ref<FormInstance>()
const invoiceForm = reactive<any>({
  invoiceNo: '', invoiceDate: todayStr(),
  taxAmt: 0, exclusiveAmt: 0,
  receivedDate: todayStr(),
  status: 1, // 默认"待认证"
  img: ''
})
const invoiceRules: FormRules = {
  invoiceNo: [{ required: true, message: '发票号码必填', trigger: 'blur' }],
  invoiceDate: [{ required: true, message: '开票日期必填', trigger: 'change' }],
  taxAmt: [{ required: true, validator: (_r:any, v:any, cb:any) => (v != null && Number(v) >= 0) ? cb() : cb(new Error('税额必填')), trigger: 'change' }],
  exclusiveAmt: [{ required: true, validator: (_r:any, v:any, cb:any) => (v != null && Number(v) >= 0) ? cb() : cb(new Error('不含税金额必填')), trigger: 'change' }],
  status: [{ required: true, message: '发票状态必填', trigger: 'change' }]
}
function openInvoiceDlg(row: any) {
  if (!row?.payableId) { ElMessage.warning('请先选中一条应付单'); return }
  curRow.value = row
  Object.assign(invoiceForm, {
    invoiceNo: row.invoiceNo || '',
    invoiceDate: row.invoiceDate || todayStr(),
    taxAmt: Number(row.taxAmt || row.taxAmount || 0),
    exclusiveAmt: Number(row.exclusiveAmt || row.undiscAmount || 0),
    receivedDate: row.invoiceReceivedDate || todayStr(),
    status: (row.invoiceStatus != null) ? Number(row.invoiceStatus) : 1,
    img: row.invoiceImg || ''
  })
  invoiceDlg.value = true
}
async function uploadInvoiceImg(options: UploadRequestOptions) {
  try {
    // 假设后端提供上传URL并回传url字符串
    const fd = new FormData()
    fd.append('file', options.file)
    const resp = await proxy?.$request?.('/scm/ap/payable/invoice-upload', 'POST', fd, { isFormData: true })
    const url = resp?.url || resp?.data?.url || resp?.data
    if (typeof url === 'string') {
      invoiceForm.img = invoiceForm.img ? (invoiceForm.img + ',' + url) : url
      proxy?.$modal?.msgSuccess?.('上传成功') || ElMessage.success('上传成功')
      options.onSuccess?.(resp)
    } else {
      throw new Error('后端未返回有效的文件url')
    }
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '上传失败')
    options.onError?.(e as any)
  }
}
async function submitInvoice() {
  try {
    await invoiceFormRef.value?.validate()
    invoiceSubmitting.value = true
    const resp = await proxy?.$request?.(`/scm/ap/payable/${curRow.value?.payableId}/invoice`, 'PATCH', {
      invoiceNo: invoiceForm.invoiceNo,
      invoiceDate: invoiceForm.invoiceDate,
      taxAmt: Number(invoiceForm.taxAmt),
      exclusiveAmt: Number(invoiceForm.exclusiveAmt),
      receivedDate: invoiceForm.receivedDate || undefined,
      status: Number(invoiceForm.status),
      img: invoiceForm.img || undefined
    })
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '保存失败')
    proxy?.$modal?.msgSuccess?.('发票登记已保存') || ElMessage.success('发票登记已保存')
    invoiceDlg.value = false
    await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '保存失败')
  } finally { invoiceSubmitting.value = false }
}

// ============== 对账（推凭证） ==============
async function doReconcile(row: any) {
  if (!row?.payableId) return
  try {
    await hasPermOrThrow('scm:ap:payable:reconcile')
    const resp = await proxy?.$request?.(`/scm/ap/payable/${row.payableId}/reconcile`, 'POST', {})
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '对账失败')
    const data = resp?.data || resp
    const vid = data?.voucherId ?? data?.voucher_no ?? data?.voucherId
    proxy?.$modal?.msgSuccess?.(`对账成功，已推送应付凭证${vid != null ? '（voucherId=' + vid + '）' : ''}`) ||
      ElMessage.success(`对账成功，已推送应付凭证${vid != null ? '（voucherId=' + vid + '）' : ''}`)
    await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '对账失败')
  }
}

// ============== 生成付款计划 Dialog ==============
const planDlg = ref(false)
const planSubmitting = ref(false)
const planHd = reactive<any>({
  phaseCount: 3,
  baseDate: todayStr()
})
interface PhaseRow {
  phaseNo: number
  phaseName: string
  ratio: number
  planDateOffset: number
  remark: string
}
const phases = ref<PhaseRow[]>([])
const ratioSum = computed(() => phases.value.reduce((s, p) => s + Number(p.ratio || 0), 0))

function recomputeAmounts() { /* reserved hook */ }

function defaultPhaseName(i: number, total: number) {
  if (total === 1) return '一次性支付'
  if (i === 0) return '首付'
  if (i === total - 1) return '尾款'
  return `第${i+1}期`
}
function resetPhases() {
  const n = Math.min(10, Math.max(1, Number(planHd.phaseCount) || 3))
  const ratio = Number((100 / n).toFixed(2))
  const arr: PhaseRow[] = []
  for (let i = 0; i < n; i++) {
    arr.push({
      phaseNo: i + 1,
      phaseName: defaultPhaseName(i, n),
      ratio: ratio,
      planDateOffset: Math.max(0, i) * 30,   // 默认间隔30天
      remark: ''
    })
  }
  // 修正误差：最后一期补上 100 - sum
  const sum = arr.reduce((s, p) => s + p.ratio, 0)
  if (arr.length) arr[arr.length - 1].ratio = Number((arr[arr.length - 1].ratio + (100 - sum)).toFixed(2))
  phases.value = arr
}
function onPhaseCountChange() { resetPhases() }
function evenDistribute() {
  planHd.phaseCount = phases.value.length || planHd.phaseCount
  resetPhases()
}
function addPhaseRow() {
  const i = phases.value.length
  phases.value.push({
    phaseNo: i + 1,
    phaseName: `第${i+1}期`,
    ratio: 0,
    planDateOffset: (i) * 30,
    remark: ''
  })
}
const canSubmitPlan = computed(() => {
  if (!curRow.value?.payableId) return false
  if (!phases.value.length) return false
  if (Math.abs(ratioSum.value - 100) > 0.011) return false
  for (const p of phases.value) {
    if (p.planDateOffset == null) return false
  }
  return true
})
function openPlanDlg(row: any) {
  if (!row?.payableId) { ElMessage.warning('请先选中一条应付单'); return }
  curRow.value = row
  planHd.baseDate = row.dueDate || todayStr()
  planHd.phaseCount = 3
  resetPhases()
  planDlg.value = true
}
async function submitPlan() {
  if (!canSubmitPlan.value) {
    const diff = Math.abs(ratioSum.value - 100)
    if (diff > 0.011) { ElMessage.warning(`分期比例总和必须 = 100%（当前 ${ratioSum.value.toFixed(2)}%）`); return }
    ElMessage.warning('请完善分期参数')
    return
  }
  try {
    planSubmitting.value = true
    const body = {
      phases: phases.value.map(p => ({
        phaseNo: Number(p.phaseNo),
        phaseName: p.phaseName,
        ratio: Number(p.ratio),
        dueDateOffset: Number(p.planDateOffset),
        remark: p.remark || undefined
      }))
    }
    const resp = await proxy?.$request?.(`/scm/ap/payable/${curRow.value.payableId}/generate-plans`, 'POST', body)
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '保存失败')
    const cnt = resp?.planCount ?? resp?.data?.planCount ?? phases.value.length
    proxy?.$modal?.msgSuccess?.(`生成付款计划成功（${cnt} 期）`) || ElMessage.success(`生成付款计划成功（${cnt} 期）`)
    planDlg.value = false; await loadList()
  } catch (e: any) {
    proxy?.$modal?.msgError?.(e?.message) || ElMessage.error(e?.message || '保存失败')
  } finally { planSubmitting.value = false }
}

onMounted(() => loadList())
</script>

<style scoped>
.page-scm-ap-payable { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; display:flex; align-items:center; gap:8px; flex-wrap:wrap; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 6px; }
.c-red { color: var(--el-color-danger); font-weight: 600; }
.c-blue { color:#409EFF; }
.sub-gray { color: var(--el-text-color-secondary); }
.ml-8 { margin-left: 8px; }
.ml-12 { margin-left: 12px; }
.plan-toolbar { background:#f5f7fa; padding:8px 12px; border-radius:6px; margin-bottom:8px; }
.invoice-upload :deep(.el-upload-list) { margin-top: 6px; }
</style>
