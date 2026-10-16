<template>
  <div class="page-scm-ap-payable">
    <!-- 搜索 -->
    <el-form :inline="true" :model="query" class="search-form" @submit.prevent>
      <el-form-item label="应付单号"><el-input v-model="query.payableNo" placeholder="应付单号" clearable style="width:180px" /></el-form-item>
      <el-form-item label="供应商"><el-input v-model="query.supplierName" placeholder="供应商名称" clearable style="width:180px" /></el-form-item>
      <el-form-item label="业务日期"><el-input v-model="query.bizDate" placeholder="YYYY-MM-DD" clearable style="width:160px" /></el-form-item>
      <el-form-item label="到期日"><el-input v-model="query.dueDate" placeholder="YYYY-MM-DD" clearable style="width:160px" /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width:150px">
          <el-option v-for="o in statusOpts" :key="o.v" :label="o.l" :value="o.v" />
        </el-select>
      </el-form-item>
      <el-form-item label="逾期">
        <el-select v-model="query.isOverdue" placeholder="全部" clearable style="width:120px">
          <el-option label="是" :value="1" />
          <el-option label="否" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon> 查询</el-button>
        <el-button @click="resetQuery"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <el-button type="primary" v-hasPermi="'scm:appayable:add'" @click="openDialog()"><el-icon><Plus /></el-icon> 新增应付单</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe border>
      <el-table-column prop="payableNo" label="应付单号" width="180" />
      <el-table-column prop="bizType" label="业务类型" width="100" />
      <el-table-column prop="supplierName" label="供应商" width="180" show-overflow-tooltip />
      <el-table-column prop="bizDate" label="业务日期" width="110" />
      <el-table-column prop="creditDays" label="信用期(天)" width="95" align="right" />
      <el-table-column prop="dueDate" label="到期日" width="110" />
      <el-table-column prop="currency" label="币种" width="70" align="center" />
      <el-table-column prop="totalAmount" label="应付金额" width="130" align="right">
        <template #default="{ row }">{{ row.totalAmount == null ? '-' : fmt(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="paidAmount" label="已付金额" width="130" align="right">
        <template #default="{ row }">{{ row.paidAmount == null ? '-' : fmt(row.paidAmount) }}</template>
      </el-table-column>
      <el-table-column prop="unpaidAmount" label="未付金额" width="130" align="right">
        <template #default="{ row }">
          <span :class="{ overdue: Number(row.isOverdue) === 1 }">{{ row.unpaidAmount == null ? '-' : fmt(row.unpaidAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="逾期" width="70" align="center">
        <template #default="{ row }">
          <el-tag v-if="Number(row.isOverdue) === 1" type="danger">是({{ row.overdueDays ?? 0 }}天)</el-tag>
          <el-tag v-else type="success">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag v-if="typeof row.status === 'number'" :type="statusMap[row.status]?.t || ''">
            {{ statusMap[row.status]?.l || String(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="560" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="onViewLines(row)">明细</el-button>
          <el-button size="small" link @click="onViewPlans(row)">计划</el-button>
          <el-button size="small" link @click="onView(row)">详情</el-button>
          <el-button v-if="canEdit(row)" v-hasPermi="'scm:appayable:edit'" size="small" link type="primary" @click="openDialog(row)">编辑</el-button>
          <el-button v-else size="small" link type="primary" disabled>编辑</el-button>
          <el-popconfirm v-if="canRemove(row)" title="确认删除当前应付单？" @confirm="onRemove(row)">
            <template #reference><el-button v-hasPermi="'scm:appayable:remove'" size="small" link type="danger">删除</el-button></template>
          </el-popconfirm>
          <template v-for="a in actions(row)" :key="a.key">
            <el-tooltip v-if="a.disabled" :content="a.disabledReason" placement="top">
              <el-button size="small" link disabled>{{ a.label }}</el-button>
            </el-tooltip>
            <el-button v-else-if="hasPermission(a.permission)" v-hasPermi="[a.permission]"
              size="small" link :type="a.type" @click="a.onClick?.(row)">{{ a.label }}</el-button>
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

    <!-- 主单弹窗 -->
    <el-dialog v-model="dialog" :title="dialogTitle" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="120px" :disabled="readonly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="应付单号" prop="payableNo"><el-input v-model="form.payableNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="业务类型"><el-input v-model="form.bizType" placeholder="IN/OUT/MANUAL" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="供应商" required prop="supplierId">
            <el-select v-model="form.supplierId" placeholder="选择供应商" filterable style="width:100%" @change="onSupplierChange">
              <el-option v-for="s in suppliers" :key="s.supplierId" :label="`${s.supplierCode || ''} ${s.supplierName}`" :value="s.supplierId">
                <span style="float:left">{{ s.supplierCode }} {{ s.supplierName }}</span>
                <span style="float:right; color:var(--el-text-color-secondary); font-size:12px;">{{ [s.contact, s.phone].filter(Boolean).join(' ') }}</span>
              </el-option>
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商名称"><el-input v-model="form.supplierName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="币种"><el-input v-model="form.currency" placeholder="CNY" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="汇率"><el-input-number v-model="form.exchangeRate" :precision="6" :min="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="信用期(天)"><el-input-number v-model="form.creditDays" :precision="0" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="业务日期" required prop="bizDate"><el-date-picker v-model="form.bizDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到期日" required prop="dueDate"><el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-descriptions title="金额" :column="3" border size="small" style="margin-bottom:8px;">
          <el-descriptions-item label="税前">{{ fmt(form.undiscAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ fmt(form.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折扣">{{ fmt(form.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应付总额">{{ fmt(form.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折本总额">{{ fmt(form.cnyTotalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未付">{{ fmt(form.unpaidAmount) }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitLoading" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 明细行弹窗 -->
    <el-dialog v-model="linesDialog" :title="linesTitle" width="1100px" destroy-on-close top="6vh">
      <div v-if="linesOwner" class="lines-header">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="应付单号">{{ linesOwner.payableNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ linesOwner.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="业务/到期日">{{ linesOwner.bizDate }} / {{ linesOwner.dueDate }}</el-descriptions-item>
          <el-descriptions-item label="应付/未付">{{ fmt(linesOwner.totalAmount) }} / {{ fmt(linesOwner.unpaidAmount) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider content-position="left">
        <span style="display:flex; align-items:center; gap:10px;">
          <span>应付行 ({{ lines.length }})</span>
          <el-button type="primary" size="small" @click="openLineDialog()">新增行</el-button>
        </span>
      </el-divider>
      <el-table v-loading="linesLoading" :data="lines" stripe border size="small" max-height="460">
        <el-table-column prop="lineNo" label="行号" width="60" align="center" />
        <el-table-column prop="lineSrc" label="来源" width="80" />
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格" width="140" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="qty" label="数量" width="100" align="right">
          <template #default="{ row }">{{ row.qty == null ? '-' : Number(row.qty).toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="100" align="right">
          <template #default="{ row }">{{ row.unitPrice == null ? '-' : fmt(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="right">
          <template #default="{ row }">{{ row.taxRate == null ? '-' : (Number(row.taxRate) * 100).toFixed(0) + '%' }}</template>
        </el-table-column>
        <el-table-column prop="lineAmount" label="行金额" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.lineAmount) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="110" />
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openLineDialog(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onRemoveLine(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="linesDialog = false">关闭</el-button></template>
    </el-dialog>

    <!-- 行弹窗 -->
    <el-dialog v-model="lineDialog" :title="lineDialogTitle" width="580px" destroy-on-close top="10vh">
      <el-form ref="lineFormRef" :model="lineForm" :rules="lineRules" label-width="100px">
        <el-form-item label="物料" prop="materialId">
          <el-select v-model="lineForm.materialId" placeholder="选择物料" filterable style="width:100%" @change="onMaterialChange">
            <el-option v-for="m in materials" :key="m.materialId" :label="`${m.materialCode || ''} ${m.materialName || ''}`" :value="m.materialId">
              <span style="float:left">{{ m.materialCode }} {{ m.materialName }}</span>
              <span style="float:right; color:var(--el-text-color-secondary); font-size:12px;">{{ m.specification }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="物料编码"><el-input v-model="lineForm.materialCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="物料名称"><el-input v-model="lineForm.materialName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="lineForm.specification" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="单位"><el-input v-model="lineForm.unit" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="数量" prop="qty"><el-input-number v-model="lineForm.qty" :precision="4" controls-position="right" style="width:100%" @change="recalcLine" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单价"><el-input-number v-model="lineForm.unitPrice" :precision="4" controls-position="right" style="width:100%" @change="recalcLine" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="税率%"><el-input-number v-model="taxPercent" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" @change="onTaxChange" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="税前金额"><el-input-number v-model="lineForm.undiscAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="税额"><el-input-number v-model="lineForm.taxAmount" :precision="2" controls-position="right" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="行金额">
          <el-input-number v-model="lineForm.lineAmount" :precision="2" controls-position="right" style="width:100%" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="批次"><el-input v-model="lineForm.batchNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="生产日期"><el-date-picker v-model="lineForm.producedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="lineForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lineDialog = false">取消</el-button>
        <el-button type="primary" :loading="lineSubmitLoading" @click="onLineSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 付款计划弹窗 -->
    <el-dialog v-model="plansDialog" title="付款计划" width="900px" destroy-on-close top="8vh">
      <div v-if="plansOwner">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="应付单号">{{ plansOwner.payableNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ plansOwner.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="应付总额">{{ fmt(plansOwner.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="未付金额">{{ fmt(plansOwner.unpaidAmount) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-divider />
      <div style="display:flex; gap:8px; align-items:center; margin-bottom:8px; flex-wrap:wrap;">
        <span class="tip">分期之和需 = 100%</span>
        <el-button size="small" @click="addPhaseRow()">+ 分期</el-button>
        <el-button size="small" type="primary" :disabled="!plansOwner" :loading="planLoading" @click="onGeneratePlans">保存分期 / 重建计划</el-button>
      </div>
      <el-table :data="phases" border size="small">
        <el-table-column label="期次" width="80">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="名称" width="160">
          <template #default="{ row }"><el-input v-model="row.phaseName" placeholder="如 30% 首款" /></template>
        </el-table-column>
        <el-table-column label="比例(%)" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.ratio" :min="0" :max="100" :precision="2" controls-position="right" style="width:100%" @change="recomputePhaseAmounts" />
          </template>
        </el-table-column>
        <el-table-column label="计划日期" width="180">
          <template #default="{ row }"><el-date-picker v-model="row.planDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></template>
        </el-table-column>
        <el-table-column label="计划金额" min-width="140" align="right">
          <template #default="{ row }">{{ row._amount == null ? '-' : fmt(row._amount) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ $index }">
            <el-button v-if="phases.length > 1" size="small" link type="danger" @click="phases.splice($index,1); recomputePhaseAmounts()">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex; justify-content:space-between; margin-top:8px; color:var(--el-text-color-secondary); font-size:12px;">
        <span>比例合计: <b :style="{ color: ratioSum === 100 ? 'var(--el-color-success)' : 'var(--el-color-danger)' }">{{ ratioSum.toFixed(2) }}%</b></span>
        <span>现有计划数: {{ existingPlans.length }}</span>
      </div>
      <el-divider content-position="left">现有计划</el-divider>
      <el-table v-loading="plansLoading" :data="existingPlans" border size="small" max-height="260">
        <el-table-column prop="phaseNo" label="期次" width="70" />
        <el-table-column prop="phaseName" label="名称" width="140" />
        <el-table-column prop="planDate" label="日期" width="120" />
        <el-table-column prop="ratio" label="比例(%)" width="100" align="right" />
        <el-table-column prop="planAmount" label="计划金额" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.planAmount) }}</template>
        </el-table-column>
        <el-table-column prop="actualAmount" label="已付" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.actualAmount) }}</template>
        </el-table-column>
        <el-table-column prop="unpaidAmount" label="未付" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.unpaidAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="planStatusMap[row.status as number]?.t || ''">{{ planStatusMap[row.status as number]?.l ?? row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="plansDialog = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Search, RefreshLeft, Plus } from '@element-plus/icons-vue'
import { scmApPayableApi, scmSupplierApi, materialApi, type ScmApPayableListQ } from '@/api'
import type { ScmApPayable, ScmApPayableLine, ScmApPaymentPlan, ScmSupplier, PdmMaterial } from '@/types/models'
import { hasPerm } from '@/utils/permission'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'ScmApPayablePage' })

const userStore = useUserStore()
function hasPermission(p: string) { return hasPerm(p, userStore.userInfo?.permissions) }
function fmt(v: any) { return v == null || v === '' ? '-' : '¥' + Number(v).toFixed(2) }

const STATUS_LABEL: Record<number, { l: string; t?: string }> = {
  0: { l: '待对账', t: 'info' },
  1: { l: '对账中', t: 'warning' },
  2: { l: '对账完成', t: 'primary' },
  3: { l: '部分付', t: 'warning' },
  4: { l: '付款完成', t: 'success' },
  9: { l: '作废', t: 'info' }
}
const statusOpts = Object.entries(STATUS_LABEL).map(([v, m]) => ({ v: Number(v), l: m.l }))
const statusMap = STATUS_LABEL

const PLAN_STATUS: Record<number, { l: string; t?: string }> = {
  0: { l: '待付', t: 'info' }, 1: { l: '部分付', t: 'warning' },
  2: { l: '已付清', t: 'success' }, 3: { l: '付款中', t: 'primary' }, 9: { l: '作废', t: 'info' }
}
const planStatusMap = PLAN_STATUS

const ACTIONS = [
  { key: 'reconcile', label: '对账完成', type: 'primary', permission: 'scm:appayable:reconcile',
    allowFrom: [0, 1, 2], disabledReason: (s: number) => `仅「待对账/对账中/对账完成」可执行（当前:${STATUS_LABEL[s]?.l ?? s}）` },
  { key: 'cancel', label: '作废', type: 'danger', permission: 'scm:appayable:cancel',
    allowFrom: [0, 1, 2], disabledReason: (s: number) => `仅「未进入付款流程」可作废（当前:${STATUS_LABEL[s]?.l ?? s}）` }
]
function actions(row: ScmApPayable) {
  const s = Number(row.status)
  return ACTIONS.map(a => {
    const enabled = a.allowFrom.includes(s)
    return {
      ...a,
      disabled: !enabled,
      disabledReason: enabled ? '' : a.disabledReason(s),
      onClick: async (r: ScmApPayable) => {
        try {
          await ElMessageBox.confirm(`确认${a.label}应付单 ${r.payableNo} ?`, '操作确认', { type: 'warning' })
        } catch { return }
        try {
          const resp = (a.key === 'reconcile' ? await scmApPayableApi.reconcile(r.payableId!)
                                               : await scmApPayableApi.cancel(r.payableId!)) as any
          if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '操作失败')
          ElMessage.success(a.label + '成功'); await loadList()
        } catch (e: any) { ElMessage.error(e?.message || a.label + '失败') }
      }
    }
  })
}

function canEdit(row: ScmApPayable)   { return [0, 1].includes(Number(row.status)) }
function canRemove(row: ScmApPayable) { return Number(row.status) === 0 }

const query = reactive<ScmApPayableListQ>({ pageNum: 1, pageSize: 20, payableNo: '', supplierName: '', bizDate: '', dueDate: '', status: undefined, isOverdue: undefined })
const list = ref<ScmApPayable[]>([])
const total = ref(0)
const loading = ref(false)
const suppliers = ref<ScmSupplier[]>([])
const materials = ref<PdmMaterial[]>([])

async function loadSuppliers() {
  try {
    const resp = (await scmSupplierApi.options()) as any
    suppliers.value = resp?.data || resp || []
  } catch (e: any) { ElMessage.error(e?.message || '加载供应商失败') }
}
async function loadMaterials() {
  try {
    const resp = (await materialApi.list({ pageNum: 1, pageSize: 1000 })) as any
    materials.value = (resp as any)?.rows || []
  } catch (e: any) { ElMessage.error(e?.message || '加载物料失败') }
}
function onSupplierChange() {
  const s = suppliers.value.find(x => x.supplierId === form.supplierId)
  if (s) form.supplierName = s.supplierName
}
function onMaterialChange() {
  const m = materials.value.find(x => x.materialId === lineForm.materialId)
  if (m) {
    lineForm.materialCode = m.materialCode
    lineForm.materialName = m.materialName
    lineForm.specification = m.specification || ''
    lineForm.unit = m.unit || 'PCS'
  }
}
async function loadList(page?: number) {
  if (page) query.pageNum = page
  loading.value = true
  try {
    const resp = (await scmApPayableApi.list({ ...query })) as any
    list.value = resp?.rows || []; total.value = Number(resp?.total) || 0
  } catch (e: any) { ElMessage.error(e?.message || '加载失败') } finally { loading.value = false }
}
function resetQuery() {
  Object.assign(query, { payableNo: '', supplierName: '', bizDate: '', dueDate: '', status: undefined, isOverdue: undefined, pageNum: 1 })
  loadList(1)
}

// 主单弹窗
const dialog = ref(false)
const readonly = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => form.payableId ? (readonly.value ? '应付单详情' : '编辑应付单') : '新增应付单')
const form = reactive<Partial<ScmApPayable>>({
  payableNo: '', bizType: 'MANUAL', supplierId: undefined as any, supplierName: '',
  currency: 'CNY', exchangeRate: 1, bizDate: '', creditDays: 0, dueDate: '',
  undiscAmount: 0, discountAmount: 0, taxAmount: 0, totalAmount: 0, cnyTotalAmount: 0,
  paidAmount: 0, unpaidAmount: 0, remark: ''
})
function formDefaults() {
  Object.assign(form, {
    payableId: undefined, payableNo: '', bizType: 'MANUAL', supplierId: undefined as any, supplierName: '',
    currency: 'CNY', exchangeRate: 1, bizDate: '', creditDays: 0, dueDate: '',
    undiscAmount: 0, discountAmount: 0, taxAmount: 0, totalAmount: 0, cnyTotalAmount: 0,
    paidAmount: 0, unpaidAmount: 0, remark: ''
  })
}
function openDialog(row?: ScmApPayable) {
  formDefaults()
  if (row?.payableId) { Object.assign(form, row); readonly.value = false } else { readonly.value = false }
  dialog.value = true
}
async function onView(row: ScmApPayable) {
  formDefaults()
  try {
    const detail = (await scmApPayableApi.get(row.payableId!)) as any
    Object.assign(form, detail?.data || detail || row)
    readonly.value = true; dialog.value = true
  } catch (e: any) { ElMessage.error(e?.message || '获取详情失败') }
}
async function onSubmit() {
  submitLoading.value = true
  try {
    await formRef.value?.validate()
    if (form.payableId) { await scmApPayableApi.edit(form as any); ElMessage.success('修改成功') }
    else { await scmApPayableApi.add(form as any); ElMessage.success('新增成功') }
    dialog.value = false; await loadList()
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { submitLoading.value = false }
}
async function onRemove(row: ScmApPayable) {
  try { await scmApPayableApi.remove(row.payableId!); ElMessage.success('删除成功'); await loadList() }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// 明细弹窗
const linesDialog = ref(false)
const linesOwner = ref<ScmApPayable | null>(null)
const lines = ref<ScmApPayableLine[]>([])
const linesLoading = ref(false)
const linesTitle = computed(() => `应付行 ${linesOwner.value?.payableNo ? '- ' + linesOwner.value.payableNo : ''}`)
async function onViewLines(row: ScmApPayable) {
  linesOwner.value = row; lines.value = []; linesDialog.value = true; linesLoading.value = true
  try {
    const resp = (await scmApPayableApi.lines(row.payableId!)) as any
    lines.value = (resp?.rows || []).slice().sort((a: ScmApPayableLine, b: ScmApPayableLine) => (a.lineNo || 0) - (b.lineNo || 0))
  } catch (e: any) { ElMessage.error(e?.message || '加载明细失败') }
  finally { linesLoading.value = false }
}

const lineDialog = ref(false)
const lineSubmitLoading = ref(false)
const lineFormRef = ref<FormInstance | null>(null)
const lineEditingId = ref<number | null>(null)
const taxPercent = ref<number>(13)
const lineForm = reactive<Partial<ScmApPayableLine>>({
  payableId: undefined, lineSrc: 'MANUAL', materialId: 1, materialCode: '', materialName: '',
  specification: '', unit: 'PCS', qty: 0, unitPrice: 0, taxRate: 0.13,
  undiscAmount: 0, taxAmount: 0, lineAmount: 0, batchNo: '', producedDate: '', remark: ''
})
const lineRules = {
  materialId: [{ required: true, message: '物料ID必填', trigger: 'blur' }],
  qty: [{ required: true, validator: (_r: any, v: any, cb: any) => (v != null && Number(v) >= 0) ? cb() : cb(new Error('数量必填')), trigger: 'change' }]
} as any
const lineDialogTitle = computed(() => lineEditingId.value ? '编辑应付行' : '新增应付行')
function resetLineForm() {
  Object.assign(lineForm, {
    lineId: undefined, payableId: linesOwner.value?.payableId ?? undefined, lineSrc: 'MANUAL',
    materialId: 1, materialCode: '', materialName: '', specification: '', unit: 'PCS',
    qty: 0, unitPrice: 0, taxRate: 0.13, undiscAmount: 0, taxAmount: 0, lineAmount: 0,
    batchNo: '', producedDate: '', remark: ''
  })
  taxPercent.value = 13
  lineEditingId.value = null
}
function openLineDialog(row?: ScmApPayableLine) {
  if (!linesOwner.value?.payableId) { ElMessage.warning('缺少应付单ID'); return }
  resetLineForm()
  if (row) {
    Object.assign(lineForm, { ...row })
    lineEditingId.value = (row.lineId as number) ?? null
    taxPercent.value = Number(row.taxRate ?? 0.13) * 100
  }
  lineDialog.value = true
}
function onTaxChange() {
  lineForm.taxRate = Number((taxPercent.value / 100).toFixed(6))
  recalcLine()
}
function recalcLine() {
  const q = Number(lineForm.qty ?? 0); const p = Number(lineForm.unitPrice ?? 0); const t = Number(lineForm.taxRate ?? 0)
  const undisc = Number((q * p).toFixed(2))
  const tax = Number((undisc * t).toFixed(2))
  lineForm.undiscAmount = undisc
  lineForm.taxAmount = tax
  lineForm.lineAmount = Number((undisc + tax).toFixed(2))
}
async function onLineSubmit() {
  lineSubmitLoading.value = true
  try {
    await lineFormRef.value?.validate()
    recalcLine()
    const payload = { ...lineForm, payableId: linesOwner.value!.payableId! }
    if (lineEditingId.value) {
      await scmApPayableApi.editLine({ ...payload, lineId: lineEditingId.value } as any)
      ElMessage.success('修改成功')
    } else {
      await scmApPayableApi.addLine(payload as any)
      ElMessage.success('新增成功')
    }
    lineDialog.value = false
    await onViewLines(linesOwner.value!)
  } catch (e: any) {
    const msg = e?.message || (e && !e.code ? '表单校验失败,请检查红色提示项' : '保存失败')
    ElMessage.error(msg)
  } finally { lineSubmitLoading.value = false }
}
async function onRemoveLine(row: ScmApPayableLine) {
  try { await ElMessageBox.confirm(`确定删除行号 ${row.lineNo} ?`, '提示', { type: 'warning' }) } catch { return }
  try { await scmApPayableApi.removeLine(row.lineId as number); ElMessage.success('删除成功'); await onViewLines(linesOwner.value!) }
  catch (e: any) { ElMessage.error(e?.message || '删除失败') }
}

// 计划弹窗
const plansDialog = ref(false)
const plansOwner = ref<ScmApPayable | null>(null)
const existingPlans = ref<ScmApPaymentPlan[]>([])
const plansLoading = ref(false)
const planLoading = ref(false)
const phases = ref<any[]>([
  { phaseNo: 1, phaseName: '首款', ratio: 30, planDate: '' },
  { phaseNo: 2, phaseName: '尾款', ratio: 70, planDate: '' }
])
const ratioSum = computed(() => phases.value.reduce((s, p) => s + Number(p.ratio || 0), 0))
function recomputePhaseAmounts() {
  const total = Number(plansOwner.value?.totalAmount ?? 0)
  phases.value.forEach(p => {
    p._amount = Number(((Number(p.ratio || 0) / 100) * total).toFixed(2))
  })
}
function addPhaseRow() {
  phases.value.push({ phaseNo: phases.value.length + 1, phaseName: `第${phases.value.length + 1}期`, ratio: 0, planDate: '', _amount: 0 })
  recomputePhaseAmounts()
}
async function onViewPlans(row: ScmApPayable) {
  plansOwner.value = row; existingPlans.value = []; plansDialog.value = true; plansLoading.value = true
  try {
    const data = (await scmApPayableApi.plans(row.payableId!)) as any
    existingPlans.value = (data?.data || data || []).slice()
    const defaultDue = row.dueDate || ''
    phases.value = existingPlans.value.length ? existingPlans.value.map((p, i) => ({
      phaseNo: p.phaseNo ?? (i + 1), phaseName: p.phaseName || `第${i + 1}期`,
      ratio: Number(p.ratio ?? 0), planDate: p.planDate || defaultDue
    })) : [
      { phaseNo: 1, phaseName: '首款', ratio: 30, planDate: defaultDue },
      { phaseNo: 2, phaseName: '尾款', ratio: 70, planDate: defaultDue }
    ]
    recomputePhaseAmounts()
  } catch (e: any) { ElMessage.error(e?.message || '加载计划失败') }
  finally { plansLoading.value = false }
}
async function onGeneratePlans() {
  if (!plansOwner.value) return
  const sum = ratioSum.value
  if (Math.abs(sum - 100) > 0.01) { ElMessage.warning(`分期比例之和必须 = 100%（当前 ${sum.toFixed(2)}%）`); return }
  for (const p of phases.value) if (!p.planDate) { ElMessage.warning(`第${p.phaseNo ?? '-'}期计划日期必填`); return }
  planLoading.value = true
  try {
    const resp = (await scmApPayableApi.generatePlans(plansOwner.value.payableId!, phases.value.map(p => ({
      phaseNo: Number(p.phaseNo), phaseName: p.phaseName, ratio: Number(p.ratio), planDate: p.planDate
    })))) as any
    if (resp && (resp.code ?? 200) >= 400) throw new Error(resp.msg || '生成失败')
    ElMessage.success('生成成功')
    await onViewPlans(plansOwner.value); await loadList()
  } catch (e: any) { ElMessage.error(e?.message || '生成失败') }
  finally { planLoading.value = false }
}

onMounted(async () => {
  await Promise.all([loadSuppliers(), loadMaterials()])
  await loadList()
})
</script>

<style scoped>
.page-scm-ap-payable { padding: 16px; }
.search-form { background: var(--el-bg-color, #fff); padding: 12px 16px; border-radius: 8px; margin-bottom: 12px; }
.table-toolbar { margin-bottom: 8px; }
.pagination { margin-top: 12px; display: flex; justify-content: flex-end; }
.overdue { color: var(--el-color-danger); font-weight: 600; }
.lines-header { margin-bottom: 4px; }
.tip { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
