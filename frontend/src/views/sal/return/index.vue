<template>
  <div class="page-sal-return">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="退货单号"><el-input v-model="query.returnNo" placeholder="退货单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="客户">
        <el-select v-model="query.customerId" placeholder="全部客户" clearable filterable style="width:220px">
          <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
        </el-select>
      </el-form-item>
      <el-form-item label="退货日期"><el-date-picker v-model="query.returnDate" type="date" value-format="YYYY-MM-DD" placeholder="YYYY-MM-DD" style="width:160px" /></el-form-item>
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

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'sal:return:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增销售退货</el-button>
      <el-button v-hasPermi="'sal:return:export'" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="returnNo" label="退货单号" width="160" />
      <el-table-column prop="soNo" label="销售订单号" width="140" />
      <el-table-column prop="customerName" label="客户" width="180" show-overflow-tooltip />
      <el-table-column prop="returnDate" label="退货日期" width="110" />
      <el-table-column prop="reason" label="退货原因" width="200" show-overflow-tooltip />
      <el-table-column prop="totalAmount" label="退货金额" width="120"><template #default="{ row }">{{ row.totalAmount == null ? '-' : '¥' + Number(row.totalAmount).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="refundAmount" label="退款金额" width="120"><template #default="{ row }">{{ row.refundAmount == null ? '-' : '¥' + Number(row.refundAmount).toFixed(2) }}</template></el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="质检" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.qcStatus===1" type="success" size="small">合格</el-tag>
          <el-tag v-else-if="row.qcStatus===2" type="warning" size="small">部分不良</el-tag>
          <el-tag v-else-if="row.qcStatus===3" type="danger" size="small">全不良</el-tag>
          <el-tag v-else-if="row.qcStatus===0" type="info" size="small">待检</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="560" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button v-if="row.status===3 && row.qcStatus===0" v-hasPermi="'sal:return:qc'" size="small" link type="warning" @click="openQcDialog(row)">质检</el-button>
          <el-button v-hasPermi="'sal:return:list'" size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'sal:return:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled :title="cannotEditReason(row)">编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前记录？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'sal:return:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <el-popconfirm v-else title="确认删除当前记录？">
            <template #reference><el-button size="small" link type="danger" disabled :title="cannotRemoveReason(row)">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in stateActionsFor(row)" :key="a.key">
            <el-tooltip v-if="a.disabled" :content="a.disabledReason" placement="top">
              <el-button size="small" link :type="a.type || 'default'" disabled>{{ a.label }}</el-button>
            </el-tooltip>
            <el-button v-else-if="!a.permission || hasPermission(a.permission)"
              v-hasPermi="a.permission ? [a.permission] : undefined"
              size="small" link :type="a.type || 'default'"
              @click="onActionClick(a, row)">
              {{ a.label }}
            </el-button>
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

    <!-- 新增/编辑/详情 弹窗 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-form-item label="退货单号" prop="returnNo"><el-input v-model="form.returnNo" placeholder="保存时后端自动生成" /></el-form-item>
        <el-form-item label="销售订单" prop="soId">
          <el-select v-model="form.soId" placeholder="请选择销售订单" filterable clearable style="width:100%" @change="onOrderChange">
            <el-option v-for="o in orders" :key="o.soId" :label="`${o.soNo || ''} ${o.customerName || ''}`" :value="o.soId" />
          </el-select>
        </el-form-item>
        <el-form-item label="销售订单号" prop="soNo"><el-input v-model="form.soNo" /></el-form-item>
        <el-form-item label="客户" required prop="customerId">
          <el-select v-model="form.customerId" placeholder="请选择客户" filterable style="width:100%" @change="onCustomerChange">
            <el-option v-for="c in customers" :key="c.customerId" :label="`${c.customerCode || ''} ${c.customerName}`" :value="c.customerId" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName"><el-input v-model="form.customerName" disabled /></el-form-item>
        <el-form-item label="退货日期" prop="returnDate"><el-date-picker v-model="form.returnDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
        <el-form-item label="退货原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="退款金额" prop="refundAmount"><el-input-number v-model="form.refundAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item>
        <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 退货明细弹窗 -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="980px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="退货单号">{{ linesOwner.returnNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ linesOwner.customerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退货原因">{{ linesOwner.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退货日期">{{ linesOwner.returnDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退货金额">{{ linesOwner.totalAmount == null ? '-' : '¥' + Number(linesOwner.totalAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="typeof linesOwner.status === 'number'" :type="statusMap[linesOwner.status]?.t || ''">
              {{ statusMap[linesOwner.status]?.l || String(linesOwner.status) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">
        <span style="display:flex; align-items:center; gap:10px;">
          <span>退货明细行（共 {{ lines.length }} 行）</span>
          <el-button type="primary" size="small" @click="openLineDialog()">新增行</el-button>
        </span>
      </el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="520">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格" width="150" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="returnQty" label="退货数量" width="100" align="right">
          <template #default="{ row }">{{ row.returnQty == null ? '-' : Number(row.returnQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="110" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : '¥' + Number(row.unitPrice).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="returnAmount" label="退货金额" width="120" align="right">
          <template #default="{ row }">{{ row.returnAmount == null ? '-' : '¥' + Number(row.returnAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="行退货原因" min-width="170" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onRemoveLine(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="lines-footer"><span class="lines-total">共 {{ lines.length }} 行</span></div>
      <template #footer><el-button @click="linesDialog = false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="lineDialog" :title="lineDialogTitle" width="580px" destroy-on-close top="10vh">
      <el-form ref="lineFormRef" :model="lineForm" :rules="lineRules" label-width="90px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lineForm.materialId" placeholder="请选择物料" filterable clearable style="width:100%" @change="onMaterialChange">
            <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName || ''}`" :value="m.materialId">
              <div style="line-height:1.4">
                <div>{{ m.materialCode || '' }} {{ m.materialName || '' }}</div>
                <div style="font-size:12px; color:var(--el-text-color-secondary)">{{ m.specification || '-' }}</div>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="物料编码"><el-input v-model="lineForm.materialCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称"><el-input v-model="lineForm.materialName" maxlength="200" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="lineForm.specification" maxlength="200" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="lineForm.unit" maxlength="20" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="退货数量" prop="returnQty"><el-input-number v-model="lineForm.returnQty" :min="0" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="lineForm.unitPrice" :min="0" :precision="4" controls-position="right" style="width:100%" @change="recalcLineAmount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="退货金额">
              <el-input-number v-model="lineForm.returnAmount" :min="0" :precision="2" controls-position="right" style="width:100%" />
              <div style="color:var(--el-text-color-secondary); font-size:12px; margin-top:2px;">默认 = 数量 × 单价</div>
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="行退货原因"><el-input v-model="lineForm.reason" maxlength="500" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">取消</el-button>
        <el-button type="primary" :loading="lineSubmitLoading" @click="onLineSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 退货质检录入弹窗(入库确认后待检 → 录入合格数/处置 → 推进3→4并重算退款) -->
    <el-dialog v-model="qcDialog" title="退货质检录入" width="920px" destroy-on-close top="6vh">
      <el-descriptions v-if="qcOwner" :column="3" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="退货单号">{{ qcOwner.returnNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ qcOwner.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="退货金额">{{ qcOwner.totalAmount == null ? '-' : '¥' + Number(qcOwner.totalAmount).toFixed(2) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="qcLines" border size="small" max-height="380">
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="returnQty" label="退货数" width="80" align="right">
          <template #default="{ row }">{{ Number(row.returnQty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="合格数" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.qcQty" :min="0" :max="Number(row.returnQty)" :precision="4" :step="1" controls-position="right" style="width:100%" />
          </template>
        </el-table-column>
        <el-table-column label="行处置" width="150">
          <template #default="{ row }">
            <el-select v-model="row.qcDisposition" style="width:100%" size="small">
              <el-option v-for="o in dispositionOpts" :key="o.v" :label="o.l" :value="o.v" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="行备注" min-width="160">
          <template #default="{ row }"><el-input v-model="row.qcRemark" size="small" /></template>
        </el-table-column>
      </el-table>
      <el-form :model="qcForm" label-width="90px" style="margin-top:16px">
        <el-form-item label="整体处置">
          <el-select v-model="qcForm.disposition" style="width:280px">
            <el-option v-for="o in dispositionOpts" :key="o.v" :label="o.l" :value="o.v" />
          </el-select>
          <span style="color:var(--el-text-color-secondary); font-size:12px; margin-left:8px;">合格入库/退供方/折价让步/报废</span>
        </el-form-item>
        <el-form-item label="质检备注"><el-input v-model="qcForm.qcRemark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcDialog = false">取消</el-button>
        <el-button type="primary" :loading="qcSubmitting" @click="onQcSubmit">确认质检</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus, Download } from '@element-plus/icons-vue'
import { salReturnApi, salCustomerApi, salOrderApi, materialApi, type SalReturnListQ } from '@/api'
import type { SalSalesReturn, SalSalesReturnLine, SalCustomer } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'SalReturnPage' })

const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function hasPermOrThrow(p: string) { if (!hasPermission(p)) throw new Error('无此操作权限: ' + p); return true }

// 0 草稿 1 提交 2 审核通过 3 入库中 4 已完成 5 客户已签收 9 取消
const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '草稿', t: 'info' },
  1: { l: '已提交', t: 'warning' },
  2: { l: '审核通过', t: 'primary' },
  3: { l: '入库中', t: 'warning' },
  4: { l: '已完成', t: 'success' },
  5: { l: '客户签收', t: '' },
  9: { l: '取消', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l, t: m.t }))
const statusMap = STATUS_LABEL

const ACTION_DEF: Record<string, { label: string; type?: any; permission: string; allowFrom: number[]; disabledReason: (s: number) => string }> = {
  submit:   { label: '提交',   type: 'default', permission: 'sal:return:submit',   allowFrom: [0],   disabledReason: (s) => `仅「草稿」可提交（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  audit:    { label: '审核',   type: 'primary', permission: 'sal:return:audit',    allowFrom: [1],   disabledReason: (s) => `仅「已提交」可审核（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  complete: { label: '完成',   type: 'primary', permission: 'sal:return:complete', allowFrom: [2, 3], disabledReason: (s) => `仅「审核通过/入库中」可完成（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  reject:   { label: '驳回',   type: 'default', permission: 'sal:return:reject',   allowFrom: [1],   disabledReason: (s) => `仅「已提交」可驳回（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  cancel:   { label: '取消',   type: 'danger',  permission: 'sal:return:cancel',   allowFrom: [0, 1], disabledReason: (s) => `仅「草稿/已提交」可取消（当前:${STATUS_LABEL[s]?.l ?? s}）` }
}
const EDITABLE_STATUS = new Set<number>([0])
function canEdit(row: SalSalesReturn)  { return EDITABLE_STATUS.has(Number(row.status)) }
function canRemove(row: SalSalesReturn){ return EDITABLE_STATUS.has(Number(row.status)) }
function cannotEditReason(row: SalSalesReturn)   { return `仅「草稿」可编辑（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }
function cannotRemoveReason(row: SalSalesReturn) { return `仅「草稿」可删除（当前:${STATUS_LABEL[row.status as number]?.l ?? row.status}）` }

function stateActionsFor(row: SalSalesReturn): any[] {
  const s = Number(row.status)
  return (['submit', 'audit', 'complete', 'reject', 'cancel'] as const).map((key) => {
    const d = ACTION_DEF[key]
    const enabled = d.allowFrom.includes(s)
    return {
      key, label: d.label, type: d.type, permission: d.permission,
      disabled: !enabled,
      disabledReason: enabled ? '' : d.disabledReason(s),
      onClick: (async (r: any) => {
        try {
          await hasPermOrThrow(d.permission)
          if (key === 'reject') {
            const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回-' + r.returnNo, {
              confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: '选填', inputType: 'text'
            }).catch(() => ({ value: undefined }))
            const resp = (await salReturnApi.reject(r.returnId, value)) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          } else {
            const resp = await (salReturnApi as any)[key](r.returnId) as any
            if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          }
          ElMessage.success(d.label + '成功'); await loadList()
        } catch (e: any) {
          if (e === undefined || e === null) return
          ElMessage.error(e?.message || d.label + '失败')
        }
      }),
      confirm: (key === 'submit' || key === 'cancel') ? `确认${d.label}当前退货单？` : ''
    }
  })
}

const query = reactive<SalReturnListQ>({
  pageNum: 1, pageSize: 20, returnNo: '', soId: undefined, customerId: undefined,
  customerName: '', returnDate: '', status: undefined as number | undefined
})
const list = ref<SalSalesReturn[]>([])
const total = ref(0)
const loading = ref(false)
const customers = ref<SalCustomer[]>([])
const orders = ref<any[]>([])
const materials = ref<any[]>([])

async function loadCustomers() {
  try {
    const resp = (await salCustomerApi.options()) as any
    customers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载客户失败') }
}

async function loadOptions() {
  try {
    const [orderResp, matResp] = await Promise.all([
      salOrderApi.options() as any,
      materialApi.list({ pageNum: 1, pageSize: 1000 }) as any
    ])
    orders.value = orderResp?.data || orderResp || []
    materials.value = (matResp as any)?.rows || []
  } catch (e: any) { ElMessage.error(e?.message || '加载选项数据失败') }
}

async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await salReturnApi.list({
      pageNum: query.pageNum, pageSize: query.pageSize, returnNo: query.returnNo, soId: query.soId,
      customerId: query.customerId, returnDate: query.returnDate, status: query.status
    })) as any
    list.value = resp?.rows || []
    total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败'); list.value = []; total.value = 0 }
  finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { returnNo: '', soId: undefined, customerId: undefined, customerName: '', returnDate: '', status: undefined, pageNum: 1 })
  loadList(1)
}

function onActionClick(a: any, row: any) {
  if (a.confirm) { ElMessageBox.confirm(a.confirm, '操作确认', { type: 'warning' }).then(() => a.onClick?.(row)).catch(() => {}) }
  else { a.onClick?.(row) }
}

const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (form.returnId ? (readonly.value ? '销售退货详情' : '编辑销售退货') : '新增销售退货'))
const form = reactive<Partial<SalSalesReturn>>({
  returnNo: '', soId: undefined, soNo: '', customerId: undefined as any, customerCode: '', customerName: '',
  returnDate: '', warehouseId: undefined, reason: '',
  currency: 'CNY', exchangeRate: 1, totalAmount: undefined, refundAmount: undefined,
  rejectReason: '', remark: ''
})
function formDefaults() {
  Object.assign(form, {
    returnId: undefined, returnNo: '', soId: undefined, soNo: '', customerId: undefined as any, customerCode: '', customerName: '',
    returnDate: '', warehouseId: undefined, reason: '',
    currency: 'CNY', exchangeRate: 1, totalAmount: undefined, refundAmount: undefined,
    rejectReason: '', remark: ''
  })
}
function onCustomerChange(val: any) {
  const c = customers.value.find(x => x.customerId === val)
  if (c) {
    form.customerCode = c.customerCode
    form.customerName = c.customerName
  }
}
function onOrderChange(val: any) {
  const o = orders.value.find(x => x.soId === val)
  if (o) {
    form.soNo = o.soNo || ''
    if (o.customerId != null) {
      form.customerId = o.customerId
      onCustomerChange(o.customerId)
    }
  }
}
function openDialog(row?: any) {
  formDefaults()
  if (row?.returnId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: any) {
  formDefaults()
  try {
    const detail = (await salReturnApi.get(row.returnId)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.returnId) { await salReturnApi.edit(form as any); ElMessage.success('修改成功') }
    else { await salReturnApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: any) {
  try {
    await hasPermOrThrow('sal:return:remove')
    await salReturnApi.remove(row.returnId)
    ElMessage.success('删除成功'); await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

/* ================ 导出 ================ */
const exporting = ref(false)
function saveBlob(blob: Blob, resp: any, fallback: string) {
  const disp = resp?.headers?.['content-disposition'] || resp?.headers?.['Content-Disposition']
  let name = fallback
  if (disp) {
    const m = /filename\*=utf-8''([^;]+)/i.exec(disp) || /filename="?([^";]+)"?/i.exec(disp)
    if (m?.[1]) try { name = decodeURIComponent(m[1]) } catch { /* ignore */ }
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
async function doExport() {
  exporting.value = true
  try {
    const resp = await salReturnApi.export({ ...query, pageNum: 1, pageSize: 10000 })
    saveBlob(resp.data, resp, `销售退货_${Date.now()}.xlsx`)
    ElMessage.success('导出成功')
  } catch (e: any) { ElMessage.error(e?.message || '导出失败') }
  finally { exporting.value = false }
}

// 明细弹窗
const linesDialog = ref(false)
const linesOwner = ref<SalSalesReturn | null>(null)
const lines = ref<SalSalesReturnLine[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `销售退货明细${linesOwner.value?.returnNo ? ' - ' + linesOwner.value.returnNo : ''}`)
async function onViewLines(row: SalSalesReturn) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = (await salReturnApi.lines(row.returnId!)) as any
    const rows = resp?.rows || []
    lines.value = (rows as SalSalesReturnLine[]).slice().sort((a, b) => (a.lineNo || 0) - (b.lineNo || 0))
  } catch (e: any) { ElMessage.error(e?.message || '加载明细失败'); lines.value = [] }
  finally { linesLoading.value = false }
}

// ---------- 行编辑弹窗 ----------
const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance | null>(null)
const lineEditingId = ref<number | null>(null)
const lineForm = reactive<Partial<SalSalesReturnLine>>({
  returnId: undefined, materialId: undefined as any, materialCode: '', materialName: '', specification: '', unit: 'PCS',
  returnQty: 0, unitPrice: 0, returnAmount: 0, reason: '', remark: ''
})
const lineRules = {
  materialId: [{ required: true, message: '请选择物料', trigger: 'change' }],
  returnQty:  [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) > 0) ? cb() : cb(new Error('退货数量需大于0')), trigger: 'change' }],
} as any
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑退货行' : '新增退货行')
function resetLineForm() {
  Object.assign(lineForm, {
    returnId: linesOwner.value?.returnId ?? undefined, materialId: undefined as any, materialCode: '', materialName: '',
    specification: '', unit: 'PCS', returnQty: 0, unitPrice: 0, returnAmount: 0,
    reason: '', remark: ''
  })
  lineEditingId.value = null
}
function openLineDialog(row?: SalSalesReturnLine) {
  if (!linesOwner.value?.returnId) { ElMessage.warning('缺少主单ID'); return }
  if (row) { Object.assign(lineForm, { ...row }); lineEditingId.value = (row.lineId as number) ?? null }
  else resetLineForm()
  lineDialog.value = true
}
function recalcLineAmount() {
  const q = Number(lineForm.returnQty ?? 0)
  const p = Number(lineForm.unitPrice ?? 0)
  lineForm.returnAmount = Number((q * p).toFixed(2))
}
function onMaterialChange(val: any) {
  const m = materials.value.find(x => x.materialId === val)
  if (m) {
    lineForm.materialCode = m.materialCode || ''
    lineForm.materialName = m.materialName || ''
    lineForm.specification = m.specification || ''
    lineForm.unit = m.unit || 'PCS'
  }
}
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    recalcLineAmount()
    const payload = { ...lineForm, returnId: linesOwner.value!.returnId! }
    if (lineEditingId.value) {
      await salReturnApi.editLine({ ...payload, lineId: lineEditingId.value } as any)
      ElMessage.success('修改行成功')
    } else {
      await salReturnApi.addLine(payload as any)
      ElMessage.success('新增行成功')
    }
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: SalSalesReturnLine) {
  try { await ElMessageBox.confirm(`确定删除行号 ${row.lineNo} ?`, '提示', { type: 'warning' }) } catch { return }
  try { await salReturnApi.removeLine(row.lineId as number); ElMessage.success('删除成功'); await onViewLines(linesOwner.value!) }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

/* ================ 退货质检录入 ================ */
const qcDialog = ref(false)
const qcOwner = ref<SalSalesReturn | null>(null)
const qcLines = ref<any[]>([])
const qcForm = reactive({ disposition: 'ACCEPT', qcRemark: '' })
const qcSubmitting = ref(false)
const dispositionOpts = [
  { v: 'ACCEPT', l: '合格入库' },
  { v: 'RETURN_SUPPLIER', l: '退供方' },
  { v: 'CONCESSION', l: '折价让步' },
  { v: 'SCRAP', l: '报废' }
]
async function openQcDialog(row: SalSalesReturn) {
  qcOwner.value = row
  qcForm.disposition = 'ACCEPT'
  qcForm.qcRemark = ''
  qcDialog.value = true
  try {
    const resp = (await salReturnApi.lines(row.returnId!)) as any
    const rows = resp?.rows || []
    // 默认合格数=退货数, 行处置=ACCEPT
    qcLines.value = rows.map((r: any) => ({
      ...r,
      qcQty: r.qcQty ?? r.returnQty,
      qcDisposition: r.qcDisposition || 'ACCEPT',
      qcRemark: r.qcRemark || ''
    }))
  } catch (e: any) { ElMessage.error(e?.message || '加载明细失败'); qcLines.value = [] }
}
async function onQcSubmit() {
  if (!qcOwner.value?.returnId) return
  qcSubmitting.value = true
  try {
    const lines = qcLines.value.map((l: any) => ({
      lineId: l.lineId, qcQty: l.qcQty, qcDisposition: l.qcDisposition, qcRemark: l.qcRemark
    }))
    const resp = (await salReturnApi.submitQc(qcOwner.value.returnId, {
      disposition: qcForm.disposition, qcRemark: qcForm.qcRemark, lines
    })) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '质检失败')
    ElMessage.success('质检录入成功, 退货单已完成')
    qcDialog.value = false
    await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '质检失败') }
  finally { qcSubmitting.value = false }
}

onMounted(async () => {
  await Promise.all([loadCustomers(), loadOptions()])
  await loadList()
})
</script>

<style scoped>
.page-sal-return { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.lines-header { margin-bottom: 4px; }
.lines-footer { margin-top: 8px; display: flex; justify-content: flex-end; }
.lines-total { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
